package file.writer;

import model.RowData;
import org.apache.commons.csv.*;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static config.ApplicationConfig.CSV_BOM;
import static config.ApplicationConfig.CSV_DELIMITER;

/**
 * CSV Writer - basiert auf Ihrem funktionierenden Code.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class CsvWriter implements DataWriter {

    private final CSVPrinter printer;

    public CsvWriter(String path) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8);
        writer.write(CSV_BOM);
        this.printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withDelimiter(CSV_DELIMITER));
    }

    @Override
    public void writeHeader(List<String> headers) throws IOException {
        printer.printRecord(headers);
    }

    @Override
    public void writeRow(RowData row) throws IOException {
        writeFormattedRow(row);
    }


    @Override
    public void writeCustomData(List<RowData> data, List<String> headers) throws IOException {
        if (data == null || data.isEmpty()) {
            writeHeader(headers);
            return;
        }

        writeHeader(headers);

        for (RowData row : data) {
            List<String> values = headers.stream()
                    .map(header ->  row.getValues().getOrDefault(header, ""))
                    .toList();
            printer.printRecord(values);
        }
    }

    @Override
    public void writeFormattedRecord(List<String> formattedValues) throws IOException {
        printer.printRecord(formattedValues);
    }

    @Override
    public void close() throws IOException {
        printer.flush();
        printer.close();
    }
}