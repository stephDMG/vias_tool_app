package file.writer;

import model.RowData;
import formatter.ColumnValueFormatter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wie Ihr alter AbstractDataWriter.
 */
public interface DataWriter extends AutoCloseable {

    void writeHeader(List<String> headers) throws IOException;
    // Abstrakte Methode
    void writeFormattedRecord(List<String> formattedValues) throws IOException;

    /**
     * Schreibt formatierte Zeile - GENAU wie Ihr alter Code.
     */
    default void writeFormattedRow(RowData row) throws IOException {
        List<String> formatted = row.getValues().keySet().stream()
                .map(col -> formatter.ColumnValueFormatter.format(row, col))
                .collect(Collectors.toList());
        writeFormattedRecord(formatted);
    }

    void writeRow(RowData row) throws IOException;

    /**
     * Schreibt Custom Data mit Formatierung - GENAU wie Ihr alter Code.
     */
    default void writeCustomData(List<RowData> data, List<String> headers) throws IOException {
        writeHeader(headers);

        // Daten schreiben
        for (RowData row : data) {
            // Erstelle eine Liste der formatierten Werte
            List<String> formattedValues = headers.stream()
                    .map(header ->row.getValues().getOrDefault(header, ""))
                    .toList();

            // Rufe die Methode zum Schreiben der Zeile auf
            writeFormattedRecord(formattedValues);
        }
    }

    @Override
    void close() throws IOException;
}