package console;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;

public class DetectVSymbol {

    private static final String PDF_PATH = "C:\\Users\\stephane.dongmo\\Downloads\\März2025_115863-100209-21.07.2025-€ - 207.202,05.pdf";
    private static final String TESSDATA_PATH = "C:\\Program Files\\Tesseract-OCR\\tessdata";

    public static void main(String[] args) {
        ITesseract t = new Tesseract();
        t.setDatapath(TESSDATA_PATH);
        t.setLanguage("deu");
        t.setPageSegMode(6); // standard mode pour texte en colonnes

        try (PDDocument doc = Loader.loadPDF(new File(PDF_PATH))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                System.out.println("========== PAGE " + (i + 1) + " ==========");
                BufferedImage image = renderer.renderImageWithDPI(i, 300);

                try {
                    String text = t.doOCR(image);
                    System.out.println(text);
                } catch (TesseractException e) {
                    System.err.println("Erreur OCR page " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

