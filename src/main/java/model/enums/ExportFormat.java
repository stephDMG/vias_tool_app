package model.enums;

/**
 * Unterstützte Export-Formate.
 */
public enum ExportFormat {
    CSV("csv"),
    XLSX("xlsx"),
    TXT("txt"),
    PDF("pdf"),
    XLS("xls");

    private final String extension;

    ExportFormat(String extension) {
        this.extension = extension;
    }

    public String getExtension() { return extension; }
}