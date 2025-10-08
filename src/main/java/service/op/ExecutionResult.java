package service.op;

/**
 * Repräsentiert das detaillierte Ergebnis einer Dienstoperation.
 * Diese Klasse transportiert nicht nur den Status (Erfolg/Misserfolg) und eine Nachricht,
 * sondern auch kontextbezogene Daten wie den Ausgabepfad oder die Anzahl der verarbeiteten Zeilen.
 */
public class ExecutionResult {

    private final Status status;
    private final String message;
    private final String outputPath; // Chemin du fichier exporté
    private final int rowCount;      // Nombre de lignes exportées
    /**
     * Konstruktor für ein einfaches Ergebnis ohne zusätzliche Daten.
     */
    public ExecutionResult(Status status, String message) {
        this(status, message, null, 0);
    }

    /**
     * Vollständiger Konstruktor für ein erfolgreiches Ergebnis mit zusätzlichen Daten.
     */
    public ExecutionResult(Status status, String message, String outputPath, int rowCount) {
        this.status = status;
        this.message = message;
        this.outputPath = outputPath;
        this.rowCount = rowCount;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public int getRowCount() {
        return rowCount;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        FAILURE
    }
}