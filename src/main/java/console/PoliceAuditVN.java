package console;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PoliceAuditVN {
    private static final String PDF_PATH =
            "C:\\Users\\stephane.dongmo\\Downloads\\Dokument_August.pdf";

    public static void main(String[] args) throws Exception {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("deu");
        tesseract.setPageSegMode(6);

        StringBuilder fullText = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(new File(PDF_PATH))) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String pageText = tesseract.doOCR(image);
                fullText.append(pageText).append("\n");
            }
        } catch (TesseractException e) {
            e.printStackTrace();
            return;
        }

        // Affichage formaté
        System.out.printf("%-15s %-15s %-15s %-12s %-10s %-5s %-5s %-10s %-12s %-12s %-5s%n",
                "POLICE", "VN", "SCHA-NR", "SCH-DATUM", "BUCHUNG", "VA", "SA", "ANTEIL %", "ANT.REGUL", "100%", "V");
        System.out.println("---------------------------------------------------------------------------------------------------------------");

        for (String line : fullText.toString().split("\\r?\\n")) {
            line = line.trim();
            if (!line.startsWith("w")) continue; // garder seulement les lignes police

            String[] parts = line.split("\\s+");
            if (parts.length < 3) continue;

            String police = parts[0];

            // VN (nom juste après la police)
            String vn = (parts.length > 1) ? parts[1].replace("_", "") : "";

            // SCHA-NR (pattern style 2024012340/001)
            String schaNr = "";
            Matcher schaMatcher = Pattern.compile("\\d{9,}/\\d{3}").matcher(line);
            if (schaMatcher.find()) schaNr = schaMatcher.group();

            // SCH-DATUM (jj,mm,yyyy -> jj.mm.yyyy)
            String schDatum = "";
            Matcher dateMatcher = Pattern.compile("\\d{1,2},\\d{1,2},\\d{4}").matcher(line);
            if (dateMatcher.find()) {
                schDatum = dateMatcher.group().replace(",", ".");
            }

            // BUCHUNG (K, R, SE)
            String buchung = "";
            Matcher buchungMatcher = Pattern.compile("\\b(K|R|SE)\\b").matcher(line);
            if (buchungMatcher.find()) buchung = buchungMatcher.group();

            // VA et SA -> exactement les deux triplets après BUCHUNG
            String va = "", sa = "";
            if (!buchung.isEmpty() && line.contains(buchung)) {
                String afterBuchung = line.substring(line.indexOf(buchung) + buchung.length()).trim();
                String[] tokens = afterBuchung.split("\\s+");
                if (tokens.length >= 2) {
                    if (tokens[0].matches("\\d{3}")) va = tokens[0];
                    if (tokens[1].matches("\\d{3}")) sa = tokens[1];
                }
            }

            // ANTEIL % (valeurs comme 100,0000)
            String anteil = "";
            Matcher anteilMatcher = Pattern.compile("\\d{1,3},\\d{4}").matcher(line);
            if (anteilMatcher.find()) anteil = anteilMatcher.group();

            // Montants ANT.REGUL
            List<String> montants = Arrays.stream(parts)
                    .filter(p -> p.matches("-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}"))
                    .collect(Collectors.toList());
            if (montants.isEmpty()) continue;

            String antRegul = montants.get(montants.size() - 1);
            String hundert = antRegul; // 100% = ANT.REGUL (copie brute pour l’instant)

            // Symbole final
            String symbole = "";
            if (line.matches(".*[Vv/,7]+$")) {
                symbole = line.substring(line.length() - 1);
                if (line.endsWith("V/") || line.endsWith("V,")) {
                    symbole = line.substring(line.length() - 2);
                }
            }

            // Impression
            System.out.printf("%-15s %-15s %-15s %-12s %-10s %-5s %-5s %-10s %-12s %-12s %-5s%n",
                    police, vn, schaNr, schDatum, buchung, va, sa, anteil, antRegul, hundert, symbole);
        }
    }
}