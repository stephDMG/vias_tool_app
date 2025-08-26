package file.handler;

import file.reader.CsvReader;
import file.writer.CsvWriter;
import model.RowData;
import model.enums.ExportFormat;

import java.util.List;

/**
 * Handler für CSV-Dateien.
 * Diese Klasse implementiert das {@link FileHandler}-Interface, um Operationen
 * wie das Lesen und Schreiben von CSV-Dateien zu ermöglichen.
 * Sie delegiert die eigentliche Lese- und Schreiblogik an {@link CsvReader} und {@link CsvWriter}.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class CsvFileHandler implements FileHandler {

    /**
     * Der {@link CsvReader}, der für das Lesen von CSV-Dateien verwendet wird.
     */
    private final CsvReader reader = new CsvReader();

    /**
     * Liest Daten aus einer CSV-Datei.
     *
     * @param filePath Der Pfad zur CSV-Datei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, die die gelesenen Zeilen repräsentieren.
     */
    @Override
    public List<RowData> read(String filePath) {
        return reader.read(filePath);
    }

    /**
     * Schreibt eine Liste von {@link RowData}-Objekten in eine CSV-Datei.
     * Die Daten werden zusammen mit den angegebenen Headern in die Ausgabedatei geschrieben.
     *
     * @param data       Die Liste der {@link RowData}-Objekte, die geschrieben werden sollen.
     * @param headers    Eine Liste von Strings, die die Header der CSV-Datei darstellen.
     * @param outputPath Der Pfad zur Ausgabedatei (CSV).
     * @throws RuntimeException Wenn ein Fehler beim Schreiben der CSV-Datei auftritt.
     */
    @Override
    public void write(List<RowData> data, List<String> headers, String outputPath) {
        try (CsvWriter writer = new CsvWriter(outputPath)) {
            writer.writeCustomData(data, headers);
        } catch (Exception e) {
            throw new RuntimeException("CSV Schreibfehler: " + outputPath, e);
        }
    }

    /**
     * Überprüft, ob dieser Handler den angegebenen Dateipfad verarbeiten kann.
     * Dies ist der Fall, wenn der Dateipfad auf ".csv" endet (Groß-/Kleinschreibung wird ignoriert).
     *
     * @param filePath Der zu prüfende Dateipfad.
     * @return {@code true}, wenn der Handler die Datei verarbeiten kann, sonst {@code false}.
     */
    @Override
    public boolean canHandle(String filePath) {
        return filePath.toLowerCase().endsWith(".csv");
    }

    /**
     * Gibt das von diesem Handler unterstützte Exportformat zurück.
     *
     * @return Das {@link ExportFormat#CSV}-Enum.
     */
    @Override
    public ExportFormat getFormat() {
        return ExportFormat.CSV;
    }
}