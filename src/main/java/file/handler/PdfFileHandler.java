package file.handler;

import file.reader.PdfReader;
import file.writer.PdfWriter;
import model.RowData;
import model.enums.ExportFormat;

import java.io.IOException;
import java.util.List;

/**
 * Implementierung des {@link FileHandler}-Interfaces für PDF-Dateien.
 * Diese Klasse ermöglicht das Lesen von Daten aus PDF-Dateien,
 * unterstützt jedoch das Schreiben in PDF-Dateien nicht.
 *
 * @author Stephane Dongmo
 * @since 15.07.2025
 */
public class PdfFileHandler implements FileHandler {

    /**
     * Der interne {@link PdfReader}, der für das tatsächliche Lesen von PDF-Dateien verwendet wird.
     */
    private final PdfReader reader = new PdfReader();
    private final PdfWriter writer = new PdfWriter();

    /**
     * Liest Daten aus einer PDF-Datei.
     * Die Implementierung delegiert das Lesen an den internen {@link PdfReader}.
     *
     * @param filePath Der Pfad zur PDF-Datei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, die die gelesenen Zeilen repräsentieren.
     */
    @Override
    public List<RowData> read(String filePath) {
        return reader.read(filePath);
    }

    /**
     * Diese Methode ist nicht implementiert und wirft eine {@link UnsupportedOperationException},
     * da das Schreiben von Daten in PDF-Dateien von diesem Handler nicht unterstützt wird.
     *
     * @param data       Die Liste der {@link RowData}-Objekte, die geschrieben werden sollen (nicht unterstützt).
     * @param headers    Eine Liste von Strings, die die Header darstellen (nicht unterstützt).
     * @param outputPath Der Pfad zur Ausgabedatei (nicht unterstützt).
     * @throws UnsupportedOperationException Immer, da das Schreiben in PDF nicht unterstützt wird.
     */
    @Override
    public void write(List<RowData> data, List<String> headers, String outputPath) {
        try(PdfWriter writer = new PdfWriter()) {
            writer.writeCustomData(data, headers, outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben der PDF-Datei : " + outputPath, e);
        }
    }


    /**
     * Schreibt eine OP-Liste als PDF-Datei.
     * Diese Methode verwendet einen neuen {@link PdfWriter}, um die Daten zu exportieren.
     *
     * @param data       Die zu exportierenden {@link RowData}-Objekte.
     * @param headers    Die Header der OP-Liste.
     * @param outputPath Der Pfad zur Ausgabedatei.
     * @throws RuntimeException Falls ein Fehler beim Schreiben der PDF-Datei auftritt.
     */
    public void writeOpList(List<RowData> data, List<String> headers, String outputPath) {
        try(PdfWriter writer = new PdfWriter()) {
            writer.writeCustomData(data, headers, outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben der OP-Liste PDF: " + outputPath, e);
        }
    }

    /**
     * Überprüft, ob dieser Handler den angegebenen Dateipfad verarbeiten kann.
     * Dies ist der Fall, wenn der Dateipfad auf ".pdf" endet (Groß-/Kleinschreibung wird ignoriert).
     *
     * @param filePath Der zu prüfende Dateipfad.
     * @return {@code true}, wenn der Handler die Datei verarbeiten kann, sonst {@code false}.
     */
    @Override
    public boolean canHandle(String filePath) {
        return filePath.toLowerCase().endsWith(".pdf");
    }

    /**
     * Gibt das von diesem Handler unterstützte Exportformat zurück.
     *
     * @return Das {@link ExportFormat#PDF}-Enum.
     */
    @Override
    public ExportFormat getFormat() {
        return ExportFormat.PDF;
    }

}