package file.writer;

import model.RowData;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Text Writer - basiert auf Ihrem TextDateiWriter.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class TxtWriter implements DataWriter {

    private final BufferedWriter writer;

    /**
         * Erstellt einen Writer für einfache Textdateien.
         * @param path Zielpfad
         * @throws IOException bei I/O-Fehlern
         */
        public TxtWriter(String path) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(path));
    }

    /**
     * Schreibt die Kopfzeile (mit " | " getrennt).
     * @param headers Spaltenüberschriften
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void writeHeader(List<String> headers) throws IOException {
        writer.write(String.join(" | ", headers));
        writer.newLine();
    }

    /**
     * Schreibt eine Datenzeile, unter Nutzung der Standardformatierung.
     * @param row Datenzeile
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void writeRow(RowData row) throws IOException {
        // ✅ Anwendung von formatierter Ausgabe
        writeFormattedRow(row);
    }

    @Override
    public void writeCustomData(List<RowData> data, List<String> headers) throws IOException {
        // Header schreiben
        writeHeader(headers);

        for (RowData row : data) {
            List<String> values = headers.stream()
                    .map(header -> formatter.ColumnValueFormatter.format(row, header))
                    .toList();
            writer.write(String.join(" | ", values));
            writer.newLine();
        }
    }

    @Override
    public void writeFormattedRecord(List<String> formattedValues) throws IOException {

    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }
}