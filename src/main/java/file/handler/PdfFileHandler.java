package file.handler;

import file.reader.PdfReader;
import file.writer.op.OpListePdfWriter;
import model.RowData;
import model.enums.ExportFormat;

import java.io.IOException;
import java.util.List;

public class PdfFileHandler implements FileHandler {

    private final PdfReader reader = new PdfReader();

    @Override
    public List<RowData> read(String filePath) {
        return reader.read(filePath);
    }

    @Override
    public void write(List<RowData> data, List<String> headers, String outputPath) {
        try {
            OpListePdfWriter writer = new OpListePdfWriter();
            writer.writeCustomData(data, headers, outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben der PDF-Datei : " + outputPath, e);
        }
    }

    public void writeOpList(List<RowData> data, List<String> headers, String outputPath) {
        try {
            OpListePdfWriter writer = new OpListePdfWriter();
            writer.writeCustomData(data, headers, outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben der OP-Liste PDF: " + outputPath, e);
        }
    }

    @Override
    public boolean canHandle(String filePath) {
        return filePath.toLowerCase().endsWith(".pdf");
    }

    @Override
    public ExportFormat getFormat() {
        return ExportFormat.PDF;
    }
}