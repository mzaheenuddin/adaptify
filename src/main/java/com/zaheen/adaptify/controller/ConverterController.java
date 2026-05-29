package com.zaheen.adaptify.controller;

import com.zaheen.adaptify.model.ConversionResult;
import com.zaheen.adaptify.model.OutputFormat;
import com.zaheen.adaptify.service.PdfConverterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
public class ConverterController {

    private final PdfConverterService converterService;

    public ConverterController(PdfConverterService converterService) {
        this.converterService = converterService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("formats", OutputFormat.values());
        return "index";
    }

    @PostMapping("/convert")
    public ResponseEntity<byte[]> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String formatName,
            @RequestParam(value = "quality", defaultValue = "standard") String quality) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            OutputFormat format = OutputFormat.valueOf(formatName.toUpperCase());
            ConversionResult result = converterService.convert(file, format, quality);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(result.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + result.getFileName() + "\"")
                    .header("X-File-Size", String.valueOf(result.getSizeBytes()))
                    .body(result.getData());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/info")
    @ResponseBody
    public ResponseEntity<PageInfoResponse> getInfo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            int pages = converterService.getPageCount(file.getBytes());
            long size = file.getSize();
            return ResponseEntity.ok(new PageInfoResponse(pages, size));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    public record PageInfoResponse(int pages, long sizeBytes) {}
}
