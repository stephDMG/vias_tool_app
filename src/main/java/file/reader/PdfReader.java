package file.reader;

import model.RowData;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF-Leser unter Verwendung der PDFBox 3.x API.
 * Diese Klasse ist dafür verantwortlich, Textinhalte aus PDF-Dateien zu extrahieren
 * und jede relevante Zeile als ein {@link RowData}-Objekt zu speichern.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class PdfReader {

    /**
     * Liest den Textinhalt aus einer PDF-Datei und strukturiert ihn in einer Liste
     * von {@link RowData}-Objekten. Jedes {@link RowData}-Objekt repräsentiert eine
     * nicht-leere Zeile aus dem PDF und enthält die Seitennummer (vereinfacht auf "1"),
     * die Zeilennummer und den getrimmten Inhalt der Zeile.
     *
     * @param filePath Der Pfad zur PDF-Datei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, die die Zeilen des PDF-Inhalts repräsentieren.
     * @throws RuntimeException Wenn ein {@link IOException} während des Lesevorgangs auftritt.
     */
    public List<RowData> read(String filePath) {
        List<RowData> data = new ArrayList<>();

        try {
            // PDFBox 3.x API: Loader.loadPDF() wird zum Laden des Dokuments verwendet.
            File pdfFile = new File(filePath);

            // Das PDDocument wird im try-with-resources Block geöffnet,
            // um sicherzustellen, dass es ordnungsgemäß geschlossen wird.
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                // Erstellt einen PDFTextStripper, um den Text aus dem Dokument zu extrahieren.
                PDFTextStripper stripper = new PDFTextStripper();
                // Extrahiert den gesamten Text aus dem PDF-Dokument.
                String text = stripper.getText(document);

                // Teilt den gesamten Text in einzelne Zeilen auf.
                String[] lines = text.split("\n");
                int lineNumber = 1; // Zähler für die Zeilennummer

                // Jede extrahierte Zeile verarbeiten
                for (String line : lines) {
                    // Nur nicht-leere Zeilen berücksichtigen
                    if (!line.trim().isEmpty()) {
                        RowData row = new RowData();
                        // Vereinfacht: Setzt die Seitennummer auf "1". Für komplexere Szenarien
                        // müsste die Seitennummer genauer bestimmt werden.
                        row.put("Page", "1");
                        row.put("Line", String.valueOf(lineNumber));
                        // Den getrimmten Inhalt der Zeile speichern.
                        row.put("Content", line.trim());
                        data.add(row);
                        lineNumber++;
                    }
                }
            }

        } catch (IOException e) {
            // Eine RuntimeException werfen, falls ein Fehler beim Lesen der PDF-Datei auftritt.
            throw new RuntimeException("PDF Lesefehler: " + filePath, e);
        }

        return data; // Die Liste der gelesenen Zeilendaten zurückgeben.
    }
}