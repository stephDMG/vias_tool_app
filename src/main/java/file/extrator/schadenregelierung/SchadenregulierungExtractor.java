package file.extrator.schadenregelierung;

import file.extrator.DataExtractor;
import file.extrator.protocol.ProtocolAware;
import file.extrator.protocol.ProtocolReport;
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

public class SchadenregulierungExtractor implements DataExtractor<SchadenregulierungData>, ProtocolAware {

    private static final Logger logger = LoggerFactory.getLogger(SchadenregulierungExtractor.class);

    // Konstanten für die Betragsextraktion (aus PoliceAudit übernommen)
    private static final Pattern AMOUNT_MATCHER =
            Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})([^\\s]*)(\\s+)?(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})([^\\s]*)$");
    private static final Pattern AMOUNT_MATCHER_FALLBACK =
            Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*)(\\s+)?(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*)$");



    // --- ZUSATZ: Laufende Statistik für Korrekturen / fehlende Felder ---
    private int statCorrectedSchaNr = 0;
    private int statMissingFields   = 0;
    private int statVSymbol         = 0;

    private ProtocolReport protocol;

    public SchadenregulierungExtractor() {
        // Blacklist-Logik entfernt
    }

    @Override
    public List<SchadenregulierungData> extractData(String filePath) {
        final String extractorType = getSupportedDocumentType();
        protocol = ProtocolReport.start(filePath, extractorType);

        logger.info("🚀 Starte Extraktion HYBRID für: {}", filePath);
        List<SchadenregulierungData> results = new ArrayList<>();

        String fullText = extractTextWithPDFBox(filePath);
        if (fullText == null || fullText.trim().length() < 100) {
            logger.warn("⚠️ PDFBox hat nichts gelesen → OCR wird verwendet...");
            protocol.warn("OCR_FALLBACK", "PDFBox hat nichts gelesen – OCR wird verwendet."); // <<< NEW
            fullText = extractTextWithOCR(filePath);
        } else {
            logger.info("✅ Text erfolgreich via PDFBox extrahiert.");
            protocol.info("PDFBOX_OK", "Text erfolgreich via PDFBox extrahiert."); // <<< NEW
        }
        if (fullText == null || fullText.trim().isEmpty()) {
            logger.error("❌ Weder PDFBox noch OCR konnten Text lesen!");
            protocol.missing("NO_TEXT", "Weder PDFBox noch OCR konnten Text lesen."); // <<< NEW
            protocol.finish(); // <<< NEW
            return results;
        }

        String cleanedText = cleanOcrText(fullText);

        int processed = 0;
        for (String line : cleanedText.split("\\r?\\n")) {
            line = line.replace('_', ' ');
            line = line.trim();
            if (line.isEmpty() || !line.startsWith("w")) continue;
            processed++;

            SchadenregulierungData data = parseLineWithProtocol(line, processed);
            results.add(data);
        }

        logger.info("✅ Statistik: {} Zeilen verarbeitet, {} mit korrigierter SCHA-NR, {} mit fehlenden Feldern, {} mit V-Symbol, {} exportiert.",
                processed, statCorrectedSchaNr, statMissingFields, statVSymbol, results.size());

        // petit résumé protocole
        protocol.info("SUMMARY",
                String.format("Statistik: %d Zeilen, %d Korrekturen SCHA, %d fehlende Felder, %d V-Symbole, %d exportiert",
                        processed, statCorrectedSchaNr, statMissingFields, statVSymbol, results.size()));

        protocol.finish(); // <<< NEW
        return results;
    }


    private SchadenregulierungData parseLineWithProtocol(String line, int rowNo) {
        SchadenregulierungData data = new SchadenregulierungData();
        boolean anyMissing = false;

        try {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                logger.warn("❌ Zu wenig Tokens, exportiere leer/roh: {}", line);
                data.setPolice(parts.length > 0 ? parts[0] : null);
                statMissingFields++;

                protocol.missing("TOO_FEW_TOKENS", "Zu wenig Tokens – Zeile exportiert, aber leer/roh.",
                        rowNo, Map.of("line", line));
                return data;
            }

            String police = parts[0];
            data.setPolice(police);

            int schaIdx = indexOf(parts, "\\d{10}/\\d{3}", 1);
            boolean schaAlreadySet = false;

            if (schaIdx == -1) {
                for (int i = 1; i < parts.length - 1; i++) {
                    String combined = parts[i] + parts[i + 1];
                    if (combined.matches("\\d{10}/\\d{3}")) {
                        String vnRaw = (i - 1 >= 0) ? parts[i - 1] : null;
                        data.setVn(normalizeVn(vnRaw));
                        data.setSchaNr(combined);
                        statCorrectedSchaNr++;

                        logger.info("[KORR] SCHA-NR mit Leerzeichen erkannt und korrigiert: '{}' + '{}' -> {}",
                                parts[i], parts[i + 1], combined);
                        protocol.correction("SCHA_JOIN",
                                "SCHA-NR mit Leerzeichen erkannt und korrigiert.",
                                rowNo, Map.of("before", parts[i] + " " + parts[i+1], "after", combined, "vn", String.valueOf(vnRaw)));

                        schaIdx = i + 1;
                        schaAlreadySet = true;
                        break;
                    }
                }
            }

            if (!schaAlreadySet) {
                if (schaIdx == -1) {
                    Matcher m = Pattern.compile("(\\d{6})\\s*(\\d{4}/\\d{3})").matcher(line);
                    if (m.find()) {
                        String corrected = m.group(1) + m.group(2);
                        data.setSchaNr(corrected);

                        String[] toks = line.split("\\s+");
                        int guess = -1;
                        for (int i = 1; i < toks.length; i++) {
                            if ((toks[i] + (i + 1 < toks.length ? toks[i + 1] : ""))
                                    .contains(m.group(1))) { guess = i - 1; break; }
                        }
                        String vn = (guess >= 0 ? normalizeVn(toks[guess]) : null);

                        data.setVn(vn);
                        statCorrectedSchaNr++;

                        logger.info("[KORR] SCHA-NR aus ganzer Zeile rekonstruiert: {}", corrected);
                        protocol.correction("SCHA_REBUILD",
                                "SCHA-NR aus ganzer Zeile rekonstruiert.",
                                rowNo, Map.of("after", corrected, "vn", String.valueOf(vn)));
                    } else {
                        logger.warn("❌ SCHA-NR nicht gefunden (Zeile wird NICHT verworfen): {}", line);
                        protocol.missing("SCHA_MISSING", "SCHA-NR nicht gefunden.", rowNo, Map.of("line", line));
                        anyMissing = true;
                    }
                } else {
                    String vnRaw = parts[schaIdx - 1];
                    //String vnRaw= (parts.length > 1) ? parts[schaIdx - 1].replace("_", "") : "";
                    data.setVn(normalizeVn(vnRaw));
                    data.setSchaNr(parts[schaIdx]);
                }
            }

            int dateIdx = indexOf(parts, "\\d{1,2}[.,]\\d{1,2}[.,]\\d{4}", Math.max(1, (schaIdx == -1 ? 1 : schaIdx + 1)));
            if (dateIdx == -1) {
                logger.warn("❌ Datum nicht gefunden (leer gesetzt): {}", line);
                protocol.missing("DATE_MISSING", "Datum nicht gefunden.", rowNo, Map.of("line", line));
                data.setSchDatum(null);
                anyMissing = true;
            } else {
                String datum = parts[dateIdx].replace(',', '.');
                data.setSchDatum(datum);
            }

            int startAfter = (dateIdx == -1 ? Math.max(1, (schaIdx == -1 ? 1 : schaIdx + 1)) : dateIdx + 1);
            int buchIdx = indexOf(parts, "(?:K|R|SE|RE)", startAfter);
            if (buchIdx == -1) buchIdx = indexOf(parts, "(?:K|R|SE|RE)", startAfter + 1);
            if (buchIdx == -1) {
                logger.warn("❌ Buchungstext nicht gefunden (leer gesetzt): {}", line);
                protocol.missing("BUCHTXT_MISSING", "Buchungstext (K|R|SE|RE) nicht gefunden.", rowNo, Map.of("line", line));
                data.setBuchungstext(null);
                anyMissing = true;
            } else {
                data.setBuchungstext(parts[buchIdx]);
            }

            // ... (reste identique) ...
            // --- VA/SA ---
            String va = null, sa = null;
            int afterVaSaIdx;
            if (buchIdx == -1) {
                afterVaSaIdx = startAfter; // best effort
            } else {
                afterVaSaIdx = buchIdx + 1;
                if (buchIdx + 2 < parts.length && parts[buchIdx + 1].matches("\\d{3}") && parts[buchIdx + 2].matches("\\d{3}")) {
                    va = parts[buchIdx + 1];
                    sa = parts[buchIdx + 2];
                    afterVaSaIdx = buchIdx + 3;
                } else if (buchIdx + 1 < parts.length && parts[buchIdx + 1].matches("\\d{6}")) {
                    String vasa = parts[buchIdx + 1];
                    va = vasa.substring(0, 3);
                    sa = vasa.substring(3, 6);
                    afterVaSaIdx = buchIdx + 2;
                } else {
                    int vIdx = indexOf(parts, "\\d{3}", buchIdx + 1);
                    if (vIdx != -1 && vIdx + 1 < parts.length && parts[vIdx + 1].matches("\\d{3}")) {
                        va = parts[vIdx];
                        sa = parts[vIdx + 1];
                        afterVaSaIdx = vIdx + 2;
                    }
                }
            }
            data.setVa(va);
            data.setSa(sa);
            if (va == null || sa == null) {
                anyMissing = true;
                protocol.missing("VASA_MISSING", "VA/SA nicht gefunden.", rowNo, Map.of("line", line));
            }

            List<String> montants = Arrays.stream(parts, Math.min(afterVaSaIdx, parts.length), parts.length)
                    .filter(p -> p.matches("-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*"))
                    .toList();

            String hundertRaw = null;
            String antRegulRaw = null;

            if (montants.isEmpty() || montants.size() < 2) {
                Matcher amountMatcher = AMOUNT_MATCHER.matcher(line);
                Matcher amountMatcherFallback = AMOUNT_MATCHER_FALLBACK.matcher(line);

                if (montants.isEmpty() && amountMatcher.find()) {
                    antRegulRaw = amountMatcher.group(1);
                    hundertRaw  = amountMatcher.group(4) + amountMatcher.group(5);
                } else if (montants.isEmpty() && amountMatcherFallback.find()) {
                    antRegulRaw = amountMatcherFallback.group(1);
                    hundertRaw  = amountMatcherFallback.group(3);
                } else {
                    logger.warn("❌ Beträge (ANT.REGUL & 100%) nicht gefunden (leer gesetzt): {}", line);
                    protocol.missing("AMOUNT_MISSING", "Beträge (ANT.REGUL/100%) nicht gefunden.", rowNo, Map.of("line", line));
                    anyMissing = true;
                }
            } else {
                hundertRaw  = montants.get(montants.size() - 1);
                antRegulRaw = montants.get(montants.size() - 2);
            }

            data.setProzent100(cleanForOutput(hundertRaw));
            data.setAntRegul(cleanForOutput(antRegulRaw));

            List<String> allNumericTokens = new ArrayList<>();
            int startIdxAN = Math.min(afterVaSaIdx, parts.length);
            for (int i = startIdxAN; i < parts.length; i++) {
                String tok = parts[i];
                if (tok.matches("\\d{1,2}[.,]\\d{1,2}[.,]\\d{4}") || tok.matches("\\d{6}") || tok.matches("\\d{3}")) continue;
                String norm = normalizeNumericToken(tok);
                if (norm != null) allNumericTokens.add(norm);
            }
            data.setAntKosten(null); // bleibt leer
            String anteil = null;
            for (String t : allNumericTokens) {
                if (t.matches("\\d{1,3},\\d{4}")) { anteil = t; break; }
            }
            if (anteil == null && allNumericTokens.contains("100,0000")) {
                anteil = "100,0000";
            }
            data.setAnteilProzent(anteil);
            if (anteil == null) {
                anyMissing = true;
                protocol.missing("ANTEIL_MISSING", "Anteil % nicht gefunden.", rowNo, Map.of("line", line));
            }


            String vSymbol = detectResidualSymbol(antRegulRaw, hundertRaw, line);
            if (!vSymbol.isBlank()) {
                statVSymbol++;
                logger.info("[V-SYMBOL] Zeile markiert, aber NICHT gefiltert: {}", line);
                protocol.symbol("V_SYMBOL", "Restwert/V-Symbol erkannt.", rowNo, Map.of("line", line));
            }


            // Si des champs manquent
            if (anyMissing) {
                statMissingFields++;
            }

        } catch (Exception ex) {
            logger.warn("❌ Fehler beim Parsen (Zeile NICHT verworfen): {}", line, ex);
            statMissingFields++;
            protocol.warn("PARSE_EXCEPTION", "Fehler beim Parsen – Zeile nicht verworfen.",
                    rowNo, Map.of("exception", ex.getClass().getSimpleName(), "message", ex.getMessage()));
        }

        return data;
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

    /**
     * Reinigung für Ausgabe der Beträge (wie im PoliceAudit).
     */
    private String cleanForOutput(String rawToken) {
        if (rawToken == null) return null;
        return rawToken.replaceAll("[^0-9,\\.-]", "");
    }

    // ---------- Parsing: KEIN Verwerfen mehr ----------
    @Deprecated
    private SchadenregulierungData parseLine(String line) {
        SchadenregulierungData data = new SchadenregulierungData();
        boolean anyMissing = false;

        try {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                logger.warn("❌ Zu wenig Tokens, exportiere leer/roh: {}", line);
                data.setPolice(parts.length > 0 ? parts[0] : null);
                statMissingFields++; // praktisch alles fehlt
                return data;
            }

            // --- Basis: Police ---
            String police = parts[0];
            data.setPolice(police);

            // --- SCHA-NR + VN ---
            int schaIdx = indexOf(parts, "\\d{10}/\\d{3}", 1);
            boolean schaAlreadySet = false;

            if (schaIdx == -1) {
                // Fallback: 10 Ziffern durch Leerzeichen getrennt, dann /xxx
                for (int i = 1; i < parts.length - 1; i++) {
                    String combined = parts[i] + parts[i + 1];
                    if (combined.matches("\\d{10}/\\d{3}")) {
                        String vnRaw = (i - 1 >= 0) ? parts[i - 1] : null;
                        data.setVn(normalizeVn(vnRaw));
                        data.setSchaNr(combined);
                        statCorrectedSchaNr++;
                        logger.info("[KORR] SCHA-NR mit Leerzeichen erkannt und korrigiert: '{}' + '{}' -> {}",
                                parts[i], parts[i + 1], combined);

                        // Für nachfolgende Suche: nächster Index nach i+1 (Datum etc.)
                        schaIdx = i + 1;
                        schaAlreadySet = true;     // <<< NEU: merken, dass VN + SCHA-NR bereits gesetzt sind
                        break;
                    }
                }
            }

            if (!schaAlreadySet) {                 // <<< NEU: nur setzen, wenn NICHT bereits korrigiert
                if (schaIdx == -1) {
                    // Letzter Fallback: aus der ganzen Zeile rekonstruieren
                    Matcher m = Pattern.compile("(\\d{6})\\s*(\\d{4}/\\d{3})").matcher(line);
                    if (m.find()) {
                        String corrected = m.group(1) + m.group(2);
                        data.setSchaNr(corrected);
                        // VN heuristisch: Token vor dem ersten passenden Fragment
                        String[] toks = line.split("\\s+");
                        int guess = -1;
                        for (int i = 1; i < toks.length; i++) {
                            if ((toks[i] + (i + 1 < toks.length ? toks[i + 1] : ""))
                                    .contains(m.group(1))) { guess = i - 1; break; }
                        }
                        data.setVn(guess >= 0 ? normalizeVn(toks[guess]) : null);
                        statCorrectedSchaNr++;
                        logger.info("[KORR] SCHA-NR aus ganzer Zeile rekonstruiert: {}", corrected);
                        // Hinweis: schaIdx bleibt -1; der folgende Code berücksichtigt das
                    } else {
                        logger.warn("❌ SCHA-NR nicht gefunden (Zeile wird NICHT verworfen): {}", line);
                        anyMissing = true;
                    }
                } else {
                    // Normalfall: direktes Token
                    String vnRaw = parts[schaIdx - 1];
                    data.setVn(normalizeVn(vnRaw));
                    data.setSchaNr(parts[schaIdx]);
                }
            }

            // --- Datum ---
            int dateIdx = indexOf(parts, "\\d{1,2}[.,]\\d{1,2}[.,]\\d{4}", Math.max(1, (schaIdx == -1 ? 1 : schaIdx + 1)));
            if (dateIdx == -1) {
                logger.warn("❌ Datum nicht gefunden (leer gesetzt): {}", line);
                data.setSchDatum(null); // leer exportieren
                anyMissing = true;
            } else {
                String datum = parts[dateIdx].replace(',', '.');
                data.setSchDatum(datum);
            }

            // --- Buchungstext (K | R | SE | S | RE) ---
            int startAfter = (dateIdx == -1 ? Math.max(1, (schaIdx == -1 ? 1 : schaIdx + 1)) : dateIdx + 1);
            int buchIdx = indexOf(parts, "(?:K|R|SE|S|RE)", startAfter);
            if (buchIdx == -1) buchIdx = indexOf(parts, "(?:K|R|SE|S|RE)", startAfter + 1);
            if (buchIdx == -1) {
                logger.warn("❌ Buchungstext nicht gefunden (leer gesetzt): {}", line);
                data.setBuchungstext(null);
                anyMissing = true;
            } else {
                data.setBuchungstext(parts[buchIdx]);
            }

            // --- VA/SA ---
            String va = null, sa = null;
            int afterVaSaIdx;
            if (buchIdx == -1) {
                afterVaSaIdx = startAfter; // best effort
            } else {
                afterVaSaIdx = buchIdx + 1;
                if (buchIdx + 2 < parts.length && parts[buchIdx + 1].matches("\\d{3}") && parts[buchIdx + 2].matches("\\d{3}")) {
                    va = parts[buchIdx + 1];
                    sa = parts[buchIdx + 2];
                    afterVaSaIdx = buchIdx + 3;
                } else if (buchIdx + 1 < parts.length && parts[buchIdx + 1].matches("\\d{6}")) {
                    String vasa = parts[buchIdx + 1];
                    va = vasa.substring(0, 3);
                    sa = vasa.substring(3, 6);
                    afterVaSaIdx = buchIdx + 2;
                } else {
                    int vIdx = indexOf(parts, "\\d{3}", buchIdx + 1);
                    if (vIdx != -1 && vIdx + 1 < parts.length && parts[vIdx + 1].matches("\\d{3}")) {
                        va = parts[vIdx];
                        sa = parts[vIdx + 1];
                        afterVaSaIdx = vIdx + 2;
                    }
                }
            }
            data.setVa(va);
            data.setSa(sa);
            if (va == null || sa == null) anyMissing = true;

            // --- Beträge ---
            List<String> montants = Arrays.stream(parts, Math.min(afterVaSaIdx, parts.length), parts.length)
                    .filter(p -> p.matches("-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*"))
                    .collect(Collectors.toList());

            String hundertRaw = null;
            String antRegulRaw = null;

            if (montants.isEmpty() || montants.size() < 2) {
                Matcher amountMatcher = AMOUNT_MATCHER.matcher(line);
                Matcher amountMatcherFallback = AMOUNT_MATCHER_FALLBACK.matcher(line);

                if (montants.isEmpty() && amountMatcher.find()) {
                    antRegulRaw = amountMatcher.group(1);
                    hundertRaw  = amountMatcher.group(4) + amountMatcher.group(5);
                } else if (montants.isEmpty() && amountMatcherFallback.find()) {
                    antRegulRaw = amountMatcherFallback.group(1);
                    hundertRaw  = amountMatcherFallback.group(3);
                } else {
                    logger.warn("❌ Beträge (ANT.REGUL & 100%) nicht gefunden (leer gesetzt): {}", line);
                    anyMissing = true;
                }
            } else {
                hundertRaw  = montants.get(montants.size() - 1);
                antRegulRaw = montants.get(montants.size() - 2);
            }

            // V-Symbol erkennen, aber NICHT mehr filtern
            String vSymbol = detectResidualSymbol(antRegulRaw, hundertRaw, line);
            if (!vSymbol.isBlank()) {
                statVSymbol++;
                logger.info("[V-SYMBOL] Zeile markiert, aber NICHT gefiltert: {}", line);
            }

            // Beträge bereinigt setzen (dürfen null sein)
            data.setProzent100(cleanForOutput(hundertRaw));
            data.setAntRegul(cleanForOutput(antRegulRaw));

            // --- Anteil % ---
            List<String> allNumericTokens = new ArrayList<>();
            int startIdxAN = Math.min(afterVaSaIdx, parts.length);
            for (int i = startIdxAN; i < parts.length; i++) {
                String tok = parts[i];
                if (tok.matches("\\d{1,2}[.,]\\d{1,2}[.,]\\d{4}") || tok.matches("\\d{6}") || tok.matches("\\d{3}")) continue;
                String norm = normalizeNumericToken(tok);
                if (norm != null) allNumericTokens.add(norm);
            }

            data.setAntKosten(null); // bleibt absichtlich leer

            String anteil = null;
            for (String t : allNumericTokens) {
                if (t.matches("\\d{1,3},\\d{4}")) { anteil = t; break; }
            }
            if (anteil == null && allNumericTokens.contains("100,0000")) {
                anteil = "100,0000";
            }
            data.setAnteilProzent(anteil);
            if (anteil == null) anyMissing = true;

        } catch (Exception e) {
            // Auf keinen Fall verwerfen – alles leer lassen, aber sauber loggen
            logger.warn("❌ Fehler beim Parsen (Zeile NICHT verworfen): {}", line, e);
            statMissingFields++;
            return data;
        }

        if (anyMissing) statMissingFields++;
        return data;
    }



    // --- HILFSMETHODEN FÜR V-DETEKTION (Identisch zur vorherigen Logik) ---
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

    /**
     * Bestimmt, ob ein 'V' (Haken) vorhanden ist.
     * (Wie gehabt; wir filtern aber nicht mehr.)
     */
    private String detectResidualSymbol(String antRegulRaw, String hundertRaw, String line) {
        if (hundertRaw == null || antRegulRaw == null) return "";

        Matcher explicit = Pattern.compile("(Walting|NG|[vV/,\\}]+)$").matcher(line.replaceAll("\\s+$", ""));
        if (explicit.find()) return "V";

        double valAnt  = getNumericValue(antRegulRaw);
        double valHund = getNumericValue(hundertRaw);

        String numericPart = hundertRaw.replaceAll("[^0-9,\\.-]", "").replace(".", "").replace(',', '.');
        String remainder = "";
        try {
            Pattern p = Pattern.compile(Pattern.quote(numericPart));
            Matcher m = p.matcher(hundertRaw);
            if (m.find()) remainder = hundertRaw.substring(m.end()).replaceAll("\\s", "");
        } catch (Exception ignored) {}

        if (Math.abs(valAnt - valHund) > 0.01 || !remainder.isEmpty()) {
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
        if (tok == null) return null;
        String t = tok.replaceAll("[^0-9.,-]", "");
        if (t.isEmpty()) return null;

        if (t.contains(",") && t.contains(".")) t = t.replace(".", "");
        if (!t.contains(",") && t.matches(".*\\.\\d{2}$")) t = t.replaceFirst("\\.(\\d{2})$", ",$1");
        if (!t.contains(",") && !t.contains(".") && t.matches("\\d{3,}")) t = t.replaceFirst("(\\d+)(\\d{2})$", "$1,$2");
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
        return name.matches(".*(januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember).*\\.pdf");
    }

    @Override
    public String getSupportedDocumentType() {
        return "Schadenregulierung (Hybrid OCR)";
    }

    @Override
    public ProtocolReport getProtocolReport() {
        return protocol;
    }
}
