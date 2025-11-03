package file.writer;

import model.RowData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static config.ApplicationConfig.CSV_BOM;
import static config.ApplicationConfig.CSV_DELIMITER;

/**
 * CSV-Writer zum Schreiben von CSV-Dateien mit UTF-8 und BOM.
 * Nutzt Apache Commons CSV und die Anwendungskonfiguration für Trennzeichen.
 *
 * <p>Verwendungsbeispiel:
 * <pre>
 * try (CsvWriter w = new CsvWriter(path)) {
 *     w.writeHeader(headers);
 *     data.forEach(w::writeRow);
 * }
 * </pre>
 * </p>
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class CsvWriter implements DataWriter {

    private final CSVPrinter printer;

    /**
     * Erstellt einen CSV-Writer für den angegebenen Ausgabepfad.
     * Schreibt zu Beginn die UTF-8 BOM und konfiguriert das Trennzeichen.
     *
     * @param path Zielpfad der CSV-Datei
     * @throws IOException wenn der Stream nicht geöffnet/geschrieben werden kann
     */
    public CsvWriter(String path) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8);
        writer.write(CSV_BOM);
        this.printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withDelimiter(CSV_DELIMITER));
    }

    /**
     * Schreibt die Kopfzeile der CSV-Datei.
     *
     * @param headers Spaltenüberschriften in Anzeigereihenfolge
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void writeHeader(List<String> headers) throws IOException {
        printer.printRecord(headers);
    }

    /**
     * Schreibt eine einzelne Datenzeile unter Verwendung der Formatierungslogik
     * aus DataWriter#writeFormattedRow(RowData).
     *
     * @param row Zeilenobjekt
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void writeRow(RowData row) throws IOException {
        writeFormattedRow(row);
    }


    /**
     * Schreibt eine Datenliste mit benutzerdefinierten Headern in die CSV.
     * Ist die Liste leer, werden nur die Header geschrieben.
     *
     * @param data    Datenzeilen
     * @param headers Reihenfolge/Bezeichner der Spalten
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void writeCustomData(List<RowData> data, List<String> headers) throws IOException {
        if (data == null || data.isEmpty()) {
            writeHeader(headers);
            return;
        }

        writeHeader(headers);

        for (RowData row : data) {
            List<String> values = headers.stream()
                    .map(header -> row.getValues().getOrDefault(header, ""))
                    .toList();
            printer.printRecord(values);
        }
    }

    /**
     * Schreibt eine bereits formatierte Datensatzzeile in die CSV-Datei.
     *
     * @param formattedValues Werte in der Reihenfolge der zuvor geschriebenen Header
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void writeFormattedRecord(List<String> formattedValues) throws IOException {
        printer.printRecord(formattedValues);
    }

    /**
     * Flusht und schließt den zugrunde liegenden CSVPrinter/Writer.
     *
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void close() throws IOException {
        printer.flush();
        printer.close();
    }
}