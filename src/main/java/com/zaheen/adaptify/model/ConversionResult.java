package com.zaheen.adaptify.model;

public class ConversionResult {
    private final byte[] data;
    private final String fileName;
    private final String mimeType;
    private final long sizeBytes;

    public ConversionResult(byte[] data, String fileName, String mimeType) {
        this.data = data;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = data.length;
    }

    public byte[] getData() { return data; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
}
