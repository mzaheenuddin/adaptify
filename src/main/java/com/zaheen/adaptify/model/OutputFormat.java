package com.zaheen.adaptify.model;

public enum OutputFormat {
    JPEG("JPEG", "image/jpeg", ".jpg"),
    PNG("PNG", "image/png", ".png"),
    TIFF("TIFF", "image/tiff", ".tiff"),
    BMP("BMP", "image/bmp", ".bmp"),
    WEBP("WebP", "image/webp", ".webp"),
    GIF("GIF", "image/gif", ".gif"),
    TXT("Plain Text", "text/plain", ".txt"),
    HTML("HTML", "text/html", ".html"),
    DOCX("Word Document", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
    SVG("SVG", "image/svg+xml", ".svg");

    private final String displayName;
    private final String mimeType;
    private final String extension;

    OutputFormat(String displayName, String mimeType, String extension) {
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String getDisplayName() { return displayName; }
    public String getMimeType() { return mimeType; }
    public String getExtension() { return extension; }

    public boolean isImageFormat() {
        return this == JPEG || this == PNG || this == TIFF || this == BMP || this == WEBP || this == GIF || this == SVG;
    }
}
