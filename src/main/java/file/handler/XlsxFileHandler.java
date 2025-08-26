package file.handler;

import file.pivot.PivotProcessor;
import file.reader.XlsxReader;
import file.writer.XlsxWriter;

import model.PivotConfig;
import model.RowData;
import model.enums.ExportFormat;

import java.util.List;

/**
 * Implementierung des {@link FileHandler}-Interfaces für Microsoft Excel (XLSX)-Dateien.
 * Diese Klasse ermöglicht das Lesen, Schreiben und die Erstellung von Pivot-Tabellen
 * in XLSX-Dateien.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class XlsxFileHandler implements FileHandler {

    /**
     * Der interne {@link XlsxReader}, der für das tatsächliche Lesen von XLSX-Dateien verwendet wird.
     */
    private final XlsxReader reader = new XlsxReader();

    /**
     * Liest Daten aus einer XLSX-Datei.
     * Die Implementierung delegiert das Lesen an den internen {@link XlsxReader}.
     *
     * @param filePath Der Pfad zur XLSX-Datei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, die die gelesenen Zeilen repräsentieren.
     */
    @Override
    public List<RowData> read(String filePath) {
        return reader.read(filePath);
    }

    /**
     * Schreibt eine Liste von {@link RowData}-Objekten in eine XLSX-Datei.
     * Die Daten werden zusammen mit den angegebenen Headern in die Ausgabedatei geschrieben.
     *
     * @param data       Die Liste der {@link RowData}-Objekte, die geschrieben werden sollen.
     * @param headers    Eine Liste von Strings, die die Header der XLSX-Datei darstellen.
     * @param outputPath Der Pfad zur Ausgabedatei (XLSX).
     * @throws RuntimeException Wenn ein Fehler beim Schreiben der XLSX-Datei auftritt.
     */
    @Override
    public void write(List<RowData> data, List<String> headers, String outputPath) {
        try (XlsxWriter writer = new XlsxWriter(outputPath)) {
            writer.writeCustomData(data, headers);
        } catch (Exception e) {
            throw new RuntimeException("Excel Schreibfehler: " + outputPath, e);
        }
    }

    /**
     * Überprüft, ob dieser Handler den angegebenen Dateipfad verarbeiten kann.
     * Dies ist der Fall, wenn der Dateipfad auf ".xlsx" endet (Groß-/Kleinschreibung wird ignoriert).
     *
     * @param filePath Der zu prüfende Dateipfad.
     * @return {@code true}, wenn der Handler die Datei verarbeiten kann, sonst {@code false}.
     */
    @Override
    public boolean canHandle(String filePath) {
        String lowerCasePath = filePath.toLowerCase();
        return lowerCasePath.endsWith(".xlsx") || lowerCasePath.endsWith(".xls");
    }

    /**
     * Gibt das von diesem Handler unterstützte Exportformat zurück.
     *
     * @return Das {@link ExportFormat#XLSX}-Enum.
     */
    @Override
    public ExportFormat getFormat() {
        return ExportFormat.XLSX;
    }

    /**
     * Schreibt eine Pivot-Tabelle in eine XLSX-Datei basierend auf den bereitgestellten Daten
     * und der Pivot-Konfiguration.
     * Führt eine Validierung durch, transformiert die Daten und schreibt sie dann in die Ausgabedatei.
     *
     * @param data       Die ursprüngliche Liste von {@link RowData}-Objekten, die für die Pivot-Transformation verwendet werden sollen.
     * @param config     Die {@link PivotConfig}, die definiert, wie die Pivot-Tabelle erstellt werden soll (Spalten, Zeilen, Werte).
     * @param outputPath Der Pfad zur Ausgabedatei (XLSX), in die die Pivot-Tabelle geschrieben wird.
     * @throws IllegalArgumentException Wenn die Pivot-Transformation für die gegebenen Daten und Konfiguration nicht möglich ist.
     * @throws RuntimeException         Wenn ein Fehler beim Schreiben der XLSX-Datei auftritt (delegiert an {@link #write(List, List, String)}).
     */
    public void writePivot(List<RowData> data, PivotConfig config, String outputPath) {
        PivotProcessor processor = new PivotProcessor();


        if (!processor.canPivot(data, config)) {
            throw new IllegalArgumentException("Pivot-Transformation nicht möglich für diese Daten");
        }

        // Debug-Informationen zur Pivot-Transformation ausgeben.
        System.out.println("📊 " + processor.getPivotInfo(data, config));

        // Daten transformieren, um die Pivot-Struktur zu erzeugen.
        List<RowData> pivotData = processor.transform(data, config);
        // Header für die Pivot-Tabelle generieren.
        List<String> pivotHeaders = processor.generateHeaders(data, config);

        // Die transformierten Pivot-Daten mit den generierten Headern in die XLSX-Datei schreiben.
        write(pivotData, pivotHeaders, outputPath);

        System.out.println("✅ Pivot-Export abgeschlossen: " + outputPath);
    }

    public void writeOpList(List<RowData> data, List<String> headers, String outputPath) {
        try (XlsxWriter writer = new XlsxWriter(outputPath)) {
            writer.writeOpList(data, headers);
        } catch (Exception e) {
            throw new RuntimeException("Excel Schreibfehler: " + outputPath, e);
        }
    }
}