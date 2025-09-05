package file.reader;

import config.ApplicationConfig;
import model.RowData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV-Leser mit Unterstützung für Byte Order Mark (BOM).
 * Diese Klasse ist für das Lesen von CSV-Dateien konzipiert und kann
 * die UTF-8 Byte Order Mark automatisch erkennen und überspringen.
 * Sie verwendet die Apache Commons CSV-Bibliothek für das Parsen
 * und berücksichtigt die in {@link ApplicationConfig} definierten
 * CSV-Einstellungen wie das Trennzeichen.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class CsvReader {

    /**
     * Liest Daten aus einer CSV-Datei und gibt sie als Liste von {@link RowData}-Objekten zurück.
     * Die Methode behandelt die UTF-8 Byte Order Mark (BOM) automatisch,
     * verwendet das in {@link ApplicationConfig#CSV_DELIMITER} definierte Trennzeichen
     * und liest die erste Zeile als Header.
     * Leere Zeilen werden ignoriert und Werte getrimmt.
     *
     * @param filePath Der Pfad zur CSV-Datei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, wobei jedes Objekt eine Zeile
     * der CSV-Datei repräsentiert und die Header als Schlüssel für die Spaltenwerte dienen.
     * @throws RuntimeException Wenn ein {@link IOException} während des Lesevorgangs auftritt.
     */
    public List<RowData> read(String filePath) {
        List<RowData> data = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {

            // BOM überspringen, falls vorhanden (speziell für UTF-8 BOM: \uFEFF)
            reader.mark(1); // Merke die aktuelle Position im Stream
            int firstChar = reader.read(); // Lies das erste Zeichen
            if (firstChar != 0xFEFF) { // Prüfe, ob es sich um die Unicode BOM handelt
                reader.reset(); // Wenn nicht, setze den Stream zurück, um das Zeichen erneut zu lesen
            }

            // CSVParser konfigurieren und initialisieren
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader() // Die erste Zeile als Header verwenden
                    .withDelimiter(ApplicationConfig.CSV_DELIMITER) // Trennzeichen aus der Konfiguration verwenden
                    .withIgnoreEmptyLines(true) // Leere Zeilen ignorieren
                    .withTrim(true) // Leerzeichen an Anfang und Ende von Werten trimmen
                    .parse(reader); // Den BufferedReader zum Parsen übergeben

            // Jede CSV-Zeile (Record) verarbeiten
            for (CSVRecord record : parser) {
                RowData row = new RowData();
                // Die Header-Namen des Parsers durchlaufen, um die Daten der Zeile zu extrahieren
                for (String header : parser.getHeaderNames()) {
                    // BOM von Headern entfernen, falls sie dort noch vorhanden ist (kann bei einigen Tools passieren)
                    String cleanHeader = header.replace(ApplicationConfig.CSV_BOM, "").trim();
                    String value = record.get(header); // Wert für den aktuellen Header abrufen
                    // Wert in RowData speichern, dabei trimmen und leere Strings für Null-Werte setzen
                    row.put(cleanHeader, value != null ? value.trim() : "");
                }
                data.add(row); // Die vollständig gefüllte RowData zur Liste hinzufügen
            }

        } catch (IOException e) {
            // Eine RuntimeException werfen, falls ein Fehler beim Lesen der Datei auftritt
            throw new RuntimeException("CSV Lesefehler: " + filePath, e);
        }

        return data; // Die Liste der gelesenen Daten zurückgeben
    }
}