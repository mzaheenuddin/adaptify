package com.zaheen.adaptify.service;

import com.zaheen.adaptify.model.ConversionResult;
import com.zaheen.adaptify.model.OutputFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PdfConverterService {

    /**
     * Quality presets mapped to DPI (for images) and JPEG compression (0.0–1.0)
     * draft=72dpi, standard=150dpi, high=200dpi, ultra=300dpi
     */
    public ConversionResult convert(MultipartFile file, OutputFormat format, String quality) throws IOException {
        byte[] pdfBytes = file.getBytes();
        String baseName = getBaseName(file.getOriginalFilename());

        return switch (format) {
            case JPEG, PNG, TIFF, BMP, GIF, WEBP -> convertToImages(pdfBytes, format, quality, baseName);
            case SVG -> convertToSvgZip(pdfBytes, quality, baseName);
            case TXT -> convertToText(pdfBytes, baseName);
            case HTML -> convertToHtml(pdfBytes, baseName);
            case DOCX -> convertToDocx(pdfBytes, baseName);
        };
    }

    // ─── Image conversion ──────────────────────────────────────────────────────

    private ConversionResult convertToImages(byte[] pdfBytes, OutputFormat format,
                                              String quality, String baseName) throws IOException {
        float dpi = getDpi(quality);
        float jpegQuality = getJpegQuality(quality);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();

            if (pageCount == 1) {
                // Single page → return file directly
                BufferedImage img = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
                byte[] imageBytes = encodeImage(img, format, jpegQuality);
                return new ConversionResult(imageBytes,
                        baseName + format.getExtension(),
                        format.getMimeType());
            } else {
                // Multi-page → zip archive
                ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
                try (ZipOutputStream zip = new ZipOutputStream(zipOut)) {
                    for (int i = 0; i < pageCount; i++) {
                        BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                        byte[] imageBytes = encodeImage(img, format, jpegQuality);
                        String entryName = String.format("%s_page_%03d%s", baseName, i + 1, format.getExtension());
                        zip.putNextEntry(new ZipEntry(entryName));
                        zip.write(imageBytes);
                        zip.closeEntry();
                    }
                }
                return new ConversionResult(zipOut.toByteArray(),
                        baseName + "_pages.zip",
                        "application/zip");
            }
        }
    }

    private byte[] encodeImage(BufferedImage img, OutputFormat format, float jpegQuality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (format == OutputFormat.JPEG) {
            // Use quality-aware JPEG encoder
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    // JPEG doesn't support alpha — ensure RGB
                    BufferedImage rgbImg = toRGB(img);
                    writer.write(null, new IIOImage(rgbImg, null, null), param);
                    writer.dispose();
                }
                return baos.toByteArray();
            }
        }

        if (format == OutputFormat.WEBP) {
            // Fallback: encode as PNG since standard JDK has no WebP encoder
            // (production: add webp-imageio dependency)
            ImageIO.write(img, "PNG", baos);
        } else if (format == OutputFormat.BMP) {
            ImageIO.write(toRGB(img), "bmp", baos);
        } else {
            String formatName = format.name().toLowerCase();
            ImageIO.write(img, formatName, baos);
        }

        return baos.toByteArray();
    }

    private BufferedImage toRGB(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) return img;
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    // ─── SVG (basic — one SVG per page wrapped in zip) ────────────────────────

    private ConversionResult convertToSvgZip(byte[] pdfBytes, String quality, String baseName) throws IOException {
        float dpi = getDpi(quality);
        try (PDDocument doc = Loader.loadPDF(pdfBytes);) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();

            ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(zipOut)) {
                for (int i = 0; i < pageCount; i++) {
                    BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                    // Embed raster image inside SVG for compatibility
                    ByteArrayOutputStream pngBaos = new ByteArrayOutputStream();
                    ImageIO.write(img, "PNG", pngBaos);
                    String base64Png = java.util.Base64.getEncoder().encodeToString(pngBaos.toByteArray());
                    String svg = String.format(
                            """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                                 width="%d" height="%d" viewBox="0 0 %d %d">
                              <image xlink:href="data:image/png;base64,%s" width="%d" height="%d"/>
                            </svg>
                            """,
                            img.getWidth(), img.getHeight(),
                            img.getWidth(), img.getHeight(),
                            base64Png,
                            img.getWidth(), img.getHeight()
                    );
                    String entryName = pageCount == 1
                            ? baseName + ".svg"
                            : String.format("%s_page_%03d.svg", baseName, i + 1);
                    zip.putNextEntry(new ZipEntry(entryName));
                    zip.write(svg.getBytes());
                    zip.closeEntry();
                }
            }

            String fileName = pageCount == 1 ? baseName + ".svg.zip" : baseName + "_svg_pages.zip";
            return new ConversionResult(zipOut.toByteArray(), fileName, "application/zip");
        }
    }

    // ─── Text conversion ───────────────────────────────────────────────────────

    private ConversionResult convertToText(byte[] pdfBytes, String baseName) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes);) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            byte[] textBytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new ConversionResult(textBytes, baseName + ".txt", "text/plain");
        }
    }

    // ─── HTML conversion ───────────────────────────────────────────────────────

    private ConversionResult convertToHtml(byte[] pdfBytes, String baseName) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes);) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            String rawText = stripper.getText(doc);

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
            html.append("<meta charset=\"UTF-8\">\n");
            html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            html.append("<title>").append(escapeHtml(baseName)).append("</title>\n");
            html.append("""
                    <style>
                      body { font-family: Georgia, serif; max-width: 860px; margin: 40px auto;
                             line-height: 1.7; color: #222; padding: 0 24px; }
                      p { margin: 0 0 1em; }
                      h1 { font-size: 1.4em; border-bottom: 1px solid #ccc; padding-bottom: .4em; }
                    </style>
                    """);
            html.append("</head>\n<body>\n");
            html.append("<h1>").append(escapeHtml(baseName)).append("</h1>\n");

            String[] paragraphs = rawText.split("\n\n+");
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (!trimmed.isEmpty()) {
                    html.append("<p>").append(escapeHtml(trimmed).replace("\n", "<br>")).append("</p>\n");
                }
            }
            html.append("</body>\n</html>");

            byte[] htmlBytes = html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new ConversionResult(htmlBytes, baseName + ".html", "text/html");
        }
    }

    // ─── DOCX conversion ───────────────────────────────────────────────────────

    private ConversionResult convertToDocx(byte[] pdfBytes, String baseName) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes);) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(doc);

            XWPFDocument wordDoc = new XWPFDocument();

            // Title paragraph
            XWPFParagraph titlePara = wordDoc.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(baseName);
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            // Content paragraphs
            String[] paragraphs = rawText.split("\n\n+");
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (!trimmed.isEmpty()) {
                    XWPFParagraph p = wordDoc.createParagraph();
                    XWPFRun run = p.createRun();
                    run.setText(trimmed);
                    run.setFontSize(11);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wordDoc.write(baos);
            wordDoc.close();
            return new ConversionResult(baos.toByteArray(), baseName + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
    }

    // ─── Quality helpers ───────────────────────────────────────────────────────

    private float getDpi(String quality) {
        return switch (quality) {
            case "draft" -> 150f;
            case "standard" -> 200f;
            case "high" -> 300f;
            case "ultra" -> 600f;
            default -> 150f;
        };
    }

    private float getJpegQuality(String quality) {
        return switch (quality) {
            case "draft" -> 0.5f;
            case "standard" -> 0.60f;
            case "high" -> 0.70f;
            case "ultra" -> 0.75f;
            default -> 0.75f;
        };

        /*
        return switch (quality) {
            case "draft" -> 0.5f;
            case "standard" -> 0.75f;
            case "high" -> 0.85f;
            case "ultra" -> 0.90f;
            default -> 0.75f;
        };
         */
    }

    private String getBaseName(String fileName) {
        if (fileName == null) return "converted";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    // ─── PDF info ──────────────────────────────────────────────────────────────

    public int getPageCount(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes);) {
            return doc.getNumberOfPages();
        }
    }
}
