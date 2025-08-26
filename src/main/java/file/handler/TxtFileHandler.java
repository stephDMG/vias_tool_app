package file.handler;

import file.reader.TxtReader;
import model.RowData;
import model.enums.ExportFormat;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementierung des {@link FileHandler}-Interfaces für TXT-Dateien.
 * Diese Klasse ermöglicht das Lesen und Schreiben von Daten in einfachen Textdateien.
 * Beim Schreiben werden die Daten mit einem Pipe-Symbol (" | ") getrennt.
 *
 * @author Stephane Dongmo
 * @since 15.07.2025
 */
public class TxtFileHandler implements FileHandler {

    /**
     * Der interne {@link TxtReader}, der für das tatsächliche Lesen von TXT-Dateien verwendet wird.
     */
    private final TxtReader reader = new TxtReader();

    /**
     * Liest Daten aus einer TXT-Datei.
     * Die Implementierung delegiert das Lesen an den internen {@link TxtReader}.
     *
     * @param filePath Der Pfad zur TXT-Datei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, die die gelesenen Zeilen repräsentieren.
     */
    @Override
    public List<RowData> read(String filePath) {
        return reader.read(filePath);
    }

    /**
     * Schreibt eine Liste von {@link RowData}-Objekten in eine TXT-Datei.
     * Die Header werden in der ersten Zeile geschrieben, gefolgt von den Datenzeilen.
     * Felder innerhalb einer Zeile werden mit " | " getrennt.
     *
     * @param data       Die Liste der {@link RowData}-Objekte, die geschrieben werden sollen.
     * @param headers    Eine Liste von Strings, die die Header der TXT-Datei darstellen.
     * @param outputPath Der Pfad zur Ausgabedatei (TXT).
     * @throws RuntimeException Wenn ein {@link IOException} während des Schreibvorgangs auftritt.
     */
    @Override
    public void write(List<RowData> data, List<String> headers, String outputPath) {
        try (FileWriter writer = new FileWriter(outputPath)) {
            // Header schreiben
            writer.write(String.join(" | ", headers) + "\n");

            // Datenzeilen schreiben
            for (RowData row : data) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    // Werte basierend auf den Headern abrufen und leere Strings für fehlende Werte verwenden
                    values.add(row.getValues().getOrDefault(header, ""));
                }
                writer.write(String.join(" | ", values) + "\n");
            }

        } catch (IOException e) {
            throw new RuntimeException("Text Schreibfehler: " + outputPath, e);
        }
    }

    /**
     * Überprüft, ob dieser Handler den angegebenen Dateipfad verarbeiten kann.
     * Dies ist der Fall, wenn der Dateipfad auf ".txt" endet (Groß-/Kleinschreibung wird ignoriert).
     *
     * @param filePath Der zu prüfende Dateipfad.
     * @return {@code true}, wenn der Handler die Datei verarbeiten kann, sonst {@code false}.
     */
    @Override
    public boolean canHandle(String filePath) {
        return filePath.toLowerCase().endsWith(".txt");
    }

    /**
     * Gibt das von diesem Handler unterstützte Exportformat zurück.
     *
     * @return Das {@link ExportFormat#TXT}-Enum.
     */
    @Override
    public ExportFormat getFormat() {
        return ExportFormat.TXT;
    }
}