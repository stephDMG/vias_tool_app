package file.reader;

import model.RowData;
import org.apache.poi.ss.usermodel.*; // Importiert alle notwendigen Klassen aus Apache POI
import org.apache.poi.xssf.usermodel.XSSFWorkbook; // Spezifischer Import für XLSX-Dateien
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest Daten aus XLSX-Dateien für das VIAS Export Tool.
 * Diese Klasse nutzt die Apache POI-Bibliothek, um Microsoft Excel
 * (im XLSX-Format) Dateien zu parsen. Sie liest die erste Zeile als Header
 * und den restlichen Inhalt als Datenzeilen.
 *
 * @author Stephane Dongmo
 * @since 15.07.2025
 */
public class XlsxReader {

    /**
     * Liest Daten aus einer XLSX-Datei und gibt sie als Liste von {@link RowData}-Objekten zurück.
     * Die erste Zeile des Arbeitsblatts wird als Header interpretiert.
     * Jedes {@link RowData}-Objekt repräsentiert eine Zeile aus der Excel-Datei,
     * wobei die Header als Schlüssel für die Spaltenwerte dienen.
     *
     * @param filePath Der Pfad zur XLSX-Datei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, die die gelesenen Zeilen repräsentieren.
     * Gibt eine leere Liste zurück, wenn die Datei nicht existiert, leer ist oder keine Header-Zeile hat.
     * @throws RuntimeException Wenn ein {@link IOException} während des Lesevorgangs auftritt.
     */
    public List<RowData> read(String filePath) {
        List<RowData> data = new ArrayList<>();

        // Verwendet try-with-resources, um sicherzustellen, dass FileInputStream und Workbook
        // ordnungsgemäß geschlossen werden.
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) { // Erstellt ein Workbook-Objekt für XLSX-Dateien

            Sheet sheet = workbook.getSheetAt(0); // Holt das erste Arbeitsblatt (Index 0)
            Row headerRow = sheet.getRow(0); // Holt die erste Zeile, die als Header angenommen wird

            if (headerRow == null) {
                // Wenn die Header-Zeile null ist (z.B. leeres Blatt), gib eine leere Liste zurück.
                return data;
            }

            List<String> headers = new ArrayList<>();
            // Extrahiert die Header-Namen aus der ersten Zeile.
            for (Cell cell : headerRow) {
                headers.add(getCellValue(cell)); // Nutzt die Hilfsmethode, um den Zellwert zu erhalten.
            }

            // Iteriert über alle Datenzeilen, beginnend ab der zweiten Zeile (Index 1).
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i); // Holt die aktuelle Datenzeile.
                if (row == null) {
                    // Überspringt leere Zeilen.
                    continue;
                }

                RowData rowData = new RowData();
                // Iteriert über die Spalten basierend auf der Anzahl der Header.
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j); // Holt die Zelle in der aktuellen Spalte.
                    // Ruft den Zellwert ab; wenn die Zelle null ist, wird ein leerer String verwendet.
                    String value = cell != null ? getCellValue(cell) : "";
                    // Speichert den Wert im RowData-Objekt unter dem entsprechenden Header.
                    rowData.put(headers.get(j), value);
                }
                data.add(rowData); // Fügt die vollständig gefüllte RowData zur Ergebnisliste hinzu.
            }

        } catch (IOException e) {
            // Fängt IOException ab und wirft eine RuntimeException, um den Fehler zu propagieren.
            throw new RuntimeException("Excel Lesefehler: " + filePath, e);
        }

        return data; // Gibt die Liste der gelesenen Daten zurück.
    }

    /**
     * Eine private Hilfsmethode, um den Wert einer Zelle unabhängig von ihrem Typ zu extrahieren.
     * Unterstützt String-, numerische und boolesche Zelltypen. Andere Typen geben einen leeren String zurück.
     *
     * @param cell Die zu lesende {@link Cell}-Instanz.
     * @return Der Wert der Zelle als {@link String}, oder ein leerer String, wenn die Zelle null ist
     * oder einen nicht unterstützten Typ hat.
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // Prüfen, ob der numerische Wert eine Ganzzahl ist
                double numericValue = cell.getNumericCellValue();
                if (numericValue == (long) numericValue) {
                    // Wenn ja, als Ganzzahl ohne Dezimalstellen formatieren
                    return String.valueOf((long) numericValue);
                } else {
                    // Ansonsten als Double-String beibehalten
                    return String.valueOf(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Formeln auswerten und das Ergebnis als String zurückgeben
                DataFormatter formatter = new DataFormatter();
                return formatter.formatCellValue(cell);
            default:
                return "";
        }
    }

}