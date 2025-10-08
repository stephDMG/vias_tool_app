package file.extrator.schadenregelierung;

import file.extrator.DataExtractor;
import model.SchadenregulierungData;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SchadenregulierungExtractor implements DataExtractor<SchadenregulierungData> {

    private static final Logger logger = LoggerFactory.getLogger(SchadenregulierungExtractor.class);

    // Konstanten für die Betragsextraktion (aus PoliceAudit übernommen)
    private static final Pattern AMOUNT_MATCHER =
            Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})([^\\s]*)(\\s+)?(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})([^\\s]*)$");
    private static final Pattern AMOUNT_MATCHER_FALLBACK =
            Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*)(\\s+)?(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*)$");

    public SchadenregulierungExtractor() {
        // Blacklist-Logik entfernt
    }

    @Override
    public List<SchadenregulierungData> extractData(String filePath) {
        // Logique d'extraction et de boucle inchangée (correcte)
        logger.info("🚀 Starte Extraktion HYBRID für: {}", filePath);
        List<SchadenregulierungData> results = new ArrayList<>();

        String fullText = extractTextWithPDFBox(filePath);
        if (fullText == null || fullText.trim().length() < 100) {
            logger.warn("⚠️ PDFBox hat nichts gelesen → OCR wird verwendet...");
            fullText = extractTextWithOCR(filePath);
        } else {
            logger.info("✅ Text erfolgreich via PDFBox extrahiert.");
        }
        if (fullText == null || fullText.trim().isEmpty()) {
            logger.error("❌ Weder PDFBox noch OCR konnten Text lesen!");
            return results;
        }

        String cleanedText = cleanOcrText(fullText);

        int processed = 0, filteredByV = 0;

        for (String line : cleanedText.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.startsWith("w")) continue;
            processed++;

            SchadenregulierungData data = parseLine(line);

            if (data == null) {
                filteredByV++;
                continue;
            }

            results.add(data);
        }

        logger.info("✅ Statistik: {} Zeilen verarbeitet, {} aufgrund des 'V'-Symbols (oder Fehler) gefiltert, {} exportiert.",
                processed, filteredByV, results.size());
        return results;
    }

    // --- Extraction / Cleaning (Unverändert) ---
    private String extractTextWithPDFBox(String filePath) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            logger.error("Fehler bei PDFBox-Extraktion: {}", e.getMessage());
            return null;
        }
    }

    private String extractTextWithOCR(String filePath) {
        ITesseract tesseract = new Tesseract();
        try {
            tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
            tesseract.setLanguage("deu");
            tesseract.setPageSegMode(6);

            StringBuilder ocrResult = new StringBuilder();
            try (PDDocument document = Loader.loadPDF(new File(filePath))) {
                PDFRenderer renderer = new PDFRenderer(document);
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    BufferedImage bim = renderer.renderImageWithDPI(i, 300);
                    String text = tesseract.doOCR(bim);
                    ocrResult.append(text).append("\n");
                }
            }
            return ocrResult.toString();
        } catch (IOException | TesseractException e) {
            logger.error("Fehler bei OCR-Extraktion: {}", e.getMessage());
            return null;
        }
    }

    private String cleanOcrText(String rawText) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : rawText.split("\\r?\\n")) {
            String cl = line;
            cl = cl.replaceAll("([A-Za-z_]+)(\\d{10}/\\d{3})", "$1 $2");
            cl = cl.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
            cleaned.append(cl).append(System.lineSeparator());
        }
        return cleaned.toString();
    }

    // --- NOUVELLE MÉTHODE : NETTOYAGE SIMPLIFIÉ (Exactement comme dans PoliceAudit pour l'output) ---
    /**
     * Effectue le nettoyage simple des montants, identique à la logique du PoliceAudit main().
     * Conserve les chiffres, le point, le moins et la virgule.
     */
    private String cleanForOutput(String rawToken) {
        if (rawToken == null) return null;
        // Supprime tout ce qui n'est pas chiffre, point, moins ou virgule.
        return rawToken.replaceAll("[^0-9,\\.-]", "");
    }


    // ---------- Parsing mit V-Logik und Filter (Die kritische Methode) ----------
    private SchadenregulierungData parseLine(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length < 6) return null;

            SchadenregulierungData data = new SchadenregulierungData();

            // --- Basiselement-Extraktion (Unverändert) ---
            String police = parts[0]; data.setPolice(police);

            int schaIdx = indexOf(parts, "\\d{10}/\\d{3}", 1);
            if (schaIdx == -1) { logger.warn("❌ SCHA-NR nicht gefunden: {}", line); return null; }
            String vnRaw = parts[schaIdx - 1]; data.setVn(normalizeVn(vnRaw)); data.setSchaNr(parts[schaIdx]);

            int dateIdx = indexOf(parts, "\\d{1,2}[.,]\\d{1,2}[.,]\\d{4}", schaIdx + 1);
            if (dateIdx == -1) { logger.warn("❌ Datum nicht gefunden: {}", line); return null; }
            String datum = parts[dateIdx].replace(',', '.'); data.setSchDatum(datum);

            int buchIdx = indexOf(parts, "(?:K|R|SE)", dateIdx + 1);
            if (buchIdx == -1) buchIdx = indexOf(parts, "(?:K|R|SE)", dateIdx + 2);
            if (buchIdx == -1) { logger.warn("❌ Buchungstext nicht gefunden: {}", line); return null; }
            data.setBuchungstext(parts[buchIdx]);

            String va = null, sa = null;
            int afterVaSaIdx = buchIdx + 1;
            if (buchIdx + 2 < parts.length && parts[buchIdx + 1].matches("\\d{3}") && parts[buchIdx + 2].matches("\\d{3}")) {
                va = parts[buchIdx + 1]; sa = parts[buchIdx + 2]; afterVaSaIdx = buchIdx + 3;
            } else if (buchIdx + 1 < parts.length && parts[buchIdx + 1].matches("\\d{6}")) {
                String vasa = parts[buchIdx + 1]; va = vasa.substring(0, 3); sa = vasa.substring(3, 6); afterVaSaIdx = buchIdx + 2;
            } else {
                int vIdx = indexOf(parts, "\\d{3}", buchIdx + 1);
                if (vIdx != -1 && vIdx + 1 < parts.length && parts[vIdx + 1].matches("\\d{3}")) {
                    va = parts[vIdx]; sa = parts[vIdx + 1]; afterVaSaIdx = vIdx + 2;
                }
            }
            data.setVa(va); data.setSa(sa);
            // --- Ende Basiselement-Extraktion ---


            // --- V-ERKENNUNG UND ATTRIBUTION DES MONTANT ---
            List<String> montants = Arrays.stream(parts, afterVaSaIdx, parts.length)
                    .filter(p -> p.matches("-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*"))
                    .collect(Collectors.toList());

            String hundertRaw;
            String antRegulRaw;

            // --- Fallback-Logik zur Betragserfassung ---
            if (montants.isEmpty() || montants.size() < 2) {
                Matcher amountMatcher = AMOUNT_MATCHER.matcher(line);
                Matcher amountMatcherFallback = AMOUNT_MATCHER_FALLBACK.matcher(line);

                if (montants.isEmpty() && amountMatcher.find()) {
                    antRegulRaw = amountMatcher.group(1);
                    hundertRaw = amountMatcher.group(4) + amountMatcher.group(5);
                } else if (montants.isEmpty() && amountMatcherFallback.find()) {
                    antRegulRaw = amountMatcherFallback.group(1);
                    hundertRaw = amountMatcherFallback.group(3);
                } else {
                    logger.warn("❌ Beträge (ANT.REGUL & 100%) nicht gefunden: {}", line);
                    return null;
                }
            } else {
                hundertRaw = montants.get(montants.size() - 1);
                antRegulRaw = montants.get(montants.size() - 2);
            }

            // **1. V-Symbol-Check und Filtern**
            String vSymbol = detectResidualSymbol(antRegulRaw, hundertRaw, line);
            if (!vSymbol.isBlank()) {
                logger.debug("Police {} gefiltert, da V-Haken ({}) gefunden.", police, vSymbol);
                return null;
            }

            // **2. Speichern des BEREINIGTEN Betrags (Utilisation du cleanForOutput)**
            data.setProzent100(cleanForOutput(hundertRaw));
            data.setAntRegul(cleanForOutput(antRegulRaw));

            // **3. Gestion des autres champs (ANTEIL %)**

            // Re-génère la liste pour trouver ANTEIL % et s'assurer que les autres champs sont gérés.
            List<String> allNumericTokens = new ArrayList<>();
            for (int i = afterVaSaIdx; i < parts.length; i++) {
                String tok = parts[i];
                if (tok.matches("\\d{1,2}[.,]\\d{1,2}[.,]\\d{4}") || tok.matches("\\d{6}") || tok.matches("\\d{3}")) continue;
                String norm = normalizeNumericToken(tok);
                if (norm != null) allNumericTokens.add(norm);
            }

            // ATTENTION: ANT.KOSTEN est retiré de l'attribution
            data.setAntKosten(null);

            // ANTEIL %:
            String anteil = null;
            for (String t : allNumericTokens) {
                if (t.matches("\\d{1,3},\\d{4}")) { anteil = t; break; }
            }
            if (anteil == null) {
                int pos100 = allNumericTokens.indexOf("100,0000");
                if (pos100 >= 0) anteil = "100,0000";
            }
            data.setAnteilProzent(anteil);

            return data;

        } catch (Exception e) {
            logger.warn("❌ Fehler beim Parsen: {}", line, e);
            return null;
        }
    }

    // --- HILFSMETHODEN FÜR V-DETEKTION (Identique à PoliceAudit) ---

    private double getNumericValue(String s) {
        if (s == null) return 0.0;
        String clean = s.replaceAll("[^0-9,\\.-]", "");
        clean = clean.replace(".", "");
        clean = clean.replace(',', '.');

        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            if (clean.length() > 1 && !Character.isDigit(clean.charAt(clean.length() - 1))) {
                try {
                    return Double.parseDouble(clean.substring(0, clean.length() - 1));
                } catch (NumberFormatException ignored) {
                    return 0.0;
                }
            }
            return 0.0;
        }
    }

    // DANS SchadenregulierungExtractor.java

    /**
     * Bestimmt, ob ein 'V' (Haken) vorhanden ist. (100% identisch mit PoliceAudit)
     */
    private String detectResidualSymbol(String antRegulRaw, String hundertRaw, String line) {
        if (hundertRaw == null || antRegulRaw == null) return "";

        // 1. Explizite Muster (V, V/, v, V,, } etc. + NEU: Walting, NG)
        // Cherche toutes les variations de V PLUS "Walting" et "NG" à la fin de la ligne.
        Matcher explicit = Pattern.compile("(Walting|NG|[vV/,\\}]+)$").matcher(line.replaceAll("\\s+$", ""));
        if (explicit.find()) return "V";

        // 2. Erreur '7' ou autres caractères collés
        double valAnt = getNumericValue(antRegulRaw);
        double valHund = getNumericValue(hundertRaw);

        // Extrait la partie non numérique restante du token 100%
        String numericPart = hundertRaw.replaceAll("[^0-9,\\.-]", "").replace(".", "").replace(',', '.');
        String remainder = "";

        try {
            Pattern p = Pattern.compile(Pattern.quote(numericPart));
            Matcher m = p.matcher(hundertRaw);
            if (m.find()) {
                remainder = hundertRaw.substring(m.end()).replaceAll("\\s", "");
            }
        } catch (Exception e) {
            // Ignorer
        }

        // Si la valeur est numériquement différente OU si un caractère parasite a été trouvé
        if (Math.abs(valAnt - valHund) > 0.01 || !remainder.isEmpty()) {
            // Si la valeur est différente et qu'il y a un bruit connu (7) ou un bruit générique:
            if (hundertRaw.matches(".*[7},]*$") || !remainder.matches("^[\\s]*$")) {
                return "V";
            }
        }

        return "";
    }


    // --- Restliche Hilfsmethoden (Unverändert) ---
    private int indexOf(String[] arr, String regex, int start) {
        for (int i = Math.max(0, start); i < arr.length; i++) {
            if (arr[i].matches(regex)) return i;
        }
        return -1;
    }

    private String normalizeVn(String vn) {
        if (vn == null) return null;
        if (vn.matches(".*[A-Za-z].*")) {
            vn = vn
                    .replace('0', 'o')
                    .replace('1', 'l')
                    .replace('3', 'e')
                    .replace('5', 's')
                    .replace('7', 't')
                    .replace('4', 'a');
        }
        return vn.toLowerCase(Locale.ROOT);
    }

    private String normalizeNumericToken(String tok) {
        // Cette fonction reste inchangée car elle est utilisée pour les autres tokens
        // et par la fonction getNumericValue pour le parsing interne.
        if (tok == null) return null;
        String t = tok.replaceAll("[^0-9.,-]", "");
        if (t.isEmpty()) return null;

        if (t.contains(",") && t.contains(".")) {
            t = t.replace(".", "");
        }

        if (!t.contains(",") && t.matches(".*\\.\\d{2}$")) {
            t = t.replaceFirst("\\.(\\d{2})$", ",$1");
        }

        if (!t.contains(",") && !t.contains(".") && t.matches("\\d{3,}")) {
            t = t.replaceFirst("(\\d+)(\\d{2})$", "$1,$2");
        }

        t = t.replaceAll("\\.(?=\\d{3}(\\D|$))", "");

        t = t.replaceFirst("([,]\\d{2})\\d$", "$1");

        if (t.matches("\\d{1,3},\\d{2}") || t.matches("\\d{1,3},\\d{4}") || t.matches("\\d{1,3}(?:\\.\\d{3})+,\\d{2}"))
            return t;

        return null;
    }

    // --- Meta ---
    @Override
    public boolean canExtract(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.matches("(januar|februar|märz|april|mai|juni|jul|august|september|oktober|november|dezember)20\\d{2}_.*\\.pdf");
    }

    @Override
    public String getSupportedDocumentType() {
        return "Schadenregulierung (Hybrid OCR)";
    }
}