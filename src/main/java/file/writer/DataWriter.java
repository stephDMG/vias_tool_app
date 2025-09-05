package file.writer;

import model.RowData;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Schnittstelle für schreibende Dateiformate (CSV/XLSX, etc.).
 * Stellt Standardlogik für formatiertes Schreiben bereit und
 * definiert die nötigen Methoden für konkrete Writer.
 */
public interface DataWriter extends AutoCloseable {

    /**
     * Schreibt eine Kopfzeile in das Zielmedium.
     *
     * @param headers Spaltenüberschriften
     * @throws IOException bei I/O-Fehlern
     */
    void writeHeader(List<String> headers) throws IOException;

    /**
     * Schreibt einen bereits formatierten Datensatz.
     *
     * @param formattedValues Werte in Header-Reihenfolge
     * @throws IOException bei I/O-Fehlern
     */
    void writeFormattedRecord(List<String> formattedValues) throws IOException;

    /**
     * Erzeugt eine formatierte Zeile anhand der RowData und delegiert an
     * {@link #writeFormattedRecord(List)}.
     *
     * @param row Datenzeile
     * @throws IOException bei I/O-Fehlern
     */
    default void writeFormattedRow(RowData row) throws IOException {
        List<String> formatted = row.getValues().keySet().stream()
                .map(col -> formatter.ColumnValueFormatter.format(row, col))
                .collect(Collectors.toList());
        writeFormattedRecord(formatted);
    }

    /**
     * Schreibt eine einzelne RowData-Zeile (ggf. mit Formatierung in der Implementierung).
     *
     * @param row Datenzeile
     * @throws IOException bei I/O-Fehlern
     */
    void writeRow(RowData row) throws IOException;

    /**
     * Schreibt eine Liste von Zeilen mit vorgegebenen Headern.
     * Implementiert Standardlogik: Header schreiben und jede Zeile in der
     * Reihenfolge der Header schreiben (ohne zusätzliche Formatierung).
     *
     * @param data    Zeilen
     * @param headers Spalten in Zielreihenfolge
     * @throws IOException bei I/O-Fehlern
     */
    default void writeCustomData(List<RowData> data, List<String> headers) throws IOException {
        writeHeader(headers);

        // Daten schreiben
        for (RowData row : data) {
            // Erstelle eine Liste der formatierten Werte
            List<String> formattedValues = headers.stream()
                    .map(header -> row.getValues().getOrDefault(header, ""))
                    .toList();

            // Rufe die Methode zum Schreiben der Zeile auf
            writeFormattedRecord(formattedValues);
        }
    }

    @Override
    void close() throws IOException;
}