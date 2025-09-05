package model;

import java.time.LocalDateTime;

/**
 * Métadonnées sur un document extrait.
 *
 * @author Stephane Dongmo
 * @since 16/07/2025
 */
public class DocumentMetadata {

    private String fileName;
    private String documentType;
    private LocalDateTime extractionTime;
    private boolean extractionSuccess;
    private String errorMessage;
    private int dataCount;
    private String filePath;
    private long fileSize;

    public DocumentMetadata(String fileName, String documentType, String filePath, long fileSize) {
        this.fileName = fileName;
        this.documentType = documentType;
        this.extractionTime = LocalDateTime.now();
        this.filePath = filePath;
        this.fileSize = fileSize;
    }

    // Getters et Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public LocalDateTime getExtractionTime() {
        return extractionTime;
    }

    public void setExtractionTime(LocalDateTime extractionTime) {
        this.extractionTime = extractionTime;
    }

    public boolean isExtractionSuccess() {
        return extractionSuccess;
    }

    public void setExtractionSuccess(boolean extractionSuccess) {
        this.extractionSuccess = extractionSuccess;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getDataCount() {
        return dataCount;
    }

    public void setDataCount(int dataCount) {
        this.dataCount = dataCount;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
