package console;


import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PoliceAudit {

    private static final String PDF_PATH = "C:\\Users\\stephane.dongmo\\Downloads\\März2025_115863-100209-21.07.2025-€ - 207.202,05.pdf";
    private static final String TESSDATA_PATH = "C:\\Program Files\\Tesseract-OCR\\tessdata";

    private static final Pattern AMOUNT_MATCHER =
            Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})([^\\s]*)(\\s+)?(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})([^\\s]*)$");

    private static final Pattern AMOUNT_MATCHER_FALLBACK =
            Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*)(\\s+)?(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*)$");

    public static void main(String[] args) throws Exception {
        String raw = readPdfText(PDF_PATH);
        List<Map<String, String>> all = buildRecordsFromText(raw);
        printTable(all, "=== ALLE DATENSÄTZE ===");
        List<Map<String, String>> onlyWithoutV = filterWithoutV(all);
        printTable(onlyWithoutV, "=== DATENSÄTZE OHNE 'V'-HAKEN ===");
        // TODO: CSV-Export hier implementieren
    }

    /**
     * Führt OCR auf allen Seiten des PDFs durch.
     * @param pdfPath Der Pfad zur PDF-Datei.
     * @return Der gesamte extrahierte Text.
     */
    private static String readPdfText(String pdfPath) throws Exception {
        ITesseract t = new Tesseract();
        t.setDatapath(TESSDATA_PATH);
        t.setLanguage("deu");
        t.setPageSegMode(6); // Standardmodus für Text in Spalten

        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(new File(pdfPath))) {
            PDFRenderer r = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = r.renderImageWithDPI(i, 300);
                sb.append(t.doOCR(img)).append("\n");
            }
        } catch (TesseractException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    /**
     * Erstellt eine Liste von Datensätzen (Zeilen) aus dem OCR-Text.
     * @param text Der gesamte Text aus dem PDF.
     * @return Eine Liste von Maps, die je eine Zeile darstellen.
     */
    private static List<Map<String, String>> buildRecordsFromText(String text) {
        List<Map<String, String>> out = new ArrayList<>();

        // Der Code zur Verarbeitung anderer Spalten ist stabil und bleibt unverändert.
        // Konzentrieren wir uns auf die Spalten ANT.REGUL, 100% und V.

        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            // Nur Zeilen, die mit einer Policennummer beginnen (z.B. 'w66...')
            if (!line.startsWith("w")) continue;

            String[] parts = line.split("\\s+");
            if (parts.length < 3) continue;

            Map<String, String> rec = new LinkedHashMap<>();

            // Extrahiere POLICE, VN, SCHA-NR, SCH-DATUM, BUCHUNG, VA, SA...
            // (Ihr vorhandener Extraktionscode hier...)
            rec.put("POLICE", parts[0]);
            rec.put("VN", parts.length > 1 ? parts[1].replace("_", "") : "");

            Matcher scha = Pattern.compile("\\d{9,}/\\d{3}").matcher(line);
            rec.put("SCHA-NR", scha.find() ? scha.group() : "");

            Matcher date = Pattern.compile("\\b\\d{1,2}[.,]\\d{1,2}[.,]\\d{4}\\b").matcher(line);
            rec.put("SCH-DATUM", date.find() ? normalizeDate(date.group()) : "");

            Matcher b = Pattern.compile("\\b(K|R|SE)\\b").matcher(line);
            String buchung = b.find() ? b.group() : "";
            rec.put("BUCHUNG", buchung);

            String va = "", sa = "";
            if (!buchung.isEmpty()) {
                String after = line.substring(line.indexOf(buchung) + buchung.length()).trim();
                String[] t = after.split("\\s+");
                if (t.length >= 2) {
                    if (t[0].matches("\\d{3}")) va = t[0];
                    if (t[1].matches("\\d{3}")) sa = t[1];
                }
            }
            rec.put("VA", va);
            rec.put("SA", sa);

            Matcher anteil = Pattern.compile("\\b\\d{1,3},\\d{4}\\b").matcher(line);
            rec.put("ANTEIL %", anteil.find() ? anteil.group() : "");

            // --- Spezifische Logik für ANT.REGUL und 100% ---
            List<String> montants = Arrays.stream(parts)
                    .filter(p -> p.matches("-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}[^\\s]*")) // Regex angepasst, um 7/V/etc. am Ende zu erlauben
                    .collect(Collectors.toList());

            // Placez ceci comme constantes (private static final Pattern) de la classe PoliceAudit


// --- La partie dans buildRecordsFromText ---

            if (montants.isEmpty() || montants.size() < 2) {
                // Versuchen, die Beträge aus dem Zeilenende zu extrahieren,
                // falls sie nicht als einzelne 'parts' erkannt wurden

                // Étape 1: Créer le Matcher à partir du Pattern compilé et de la ligne actuelle
                Matcher amountMatcher = AMOUNT_MATCHER.matcher(line);
                Matcher amountMatcherFallback = AMOUNT_MATCHER_FALLBACK.matcher(line);

                if (montants.isEmpty() && amountMatcher.find()) {
                    // Wenn die Beträge am Ende der Zeile stehen und getrennt sind
                    montants.add(amountMatcher.group(1)); // ANT.REGUL
                    montants.add(amountMatcher.group(4) + amountMatcher.group(5)); // 100% + V
                } else if (montants.isEmpty() && amountMatcherFallback.find()) {
                    // Fallback für Beträge, die nur durch Leerzeichen getrennt sind
                    montants.add(amountMatcherFallback.group(1));
                    montants.add(amountMatcherFallback.group(3)); // Correction: group(3) capture la seconde quantité
                }

                if (montants.size() < 2) continue;
            }

            // Die Beträge sind die letzten beiden nummerischen Felder.
            String antRegul = montants.get(montants.size() - 2);
            String hundertRaw = montants.get(montants.size() - 1); // Rohwert, enthält möglicherweise 7/V

            rec.put("ANT.REGUL", antRegul.replaceAll("[^0-9,\\.-]", ""));
            rec.put("100%", hundertRaw.replaceAll("[^0-9,\\.-]", ""));

            // 👉 Zentrale Logik: Bestimmung des 'V'-Symbols
            String vSymbol = detectResidualSymbol(antRegul, hundertRaw, line);
            rec.put("V", vSymbol);

            out.add(rec);
        }

        return out;
    }

    /**
     * Konvertiert einen String (deutsches Zahlenformat) in einen numerischen Wert.
     * Entfernt Tausendertrennzeichen (Punkte) und ersetzt Dezimalkomma durch Punkt.
     * @param s Der zu konvertierende String (z.B. "1.945,00" oder "1945007").
     * @return Der numerische Wert als Double.
     */
    private static double getNumericValue(String s) {
        // Entfernt alles, was nicht Ziffer, Komma, Punkt oder Minus ist.
        String clean = s.replaceAll("[^0-9,\\.-]", "");

        // Entfernt Tausendertrennzeichen (Punkte)
        clean = clean.replace(".", "");

        // Ersetzt Dezimalkomma durch Punkt für Double.parseDouble
        clean = clean.replace(',', '.');

        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            // Dies fängt Fälle ab, in denen "1945007" nicht als Zahl gelesen wird,
            // weil Tesseract das 7 am Ende als "V" fehldeutet.
            // Wir versuchen, das letzte Zeichen zu entfernen, wenn es keine Zahl ist (z.B. "7" in "1945007")
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
     * Zentrales Verfahren: Bestimmt, ob ein 'V' (Haken) vorhanden ist,
     * basierend auf explizitem Text oder einem angehängten "7" / "," im OCR-Resultat.
     * @param antRegul Der extrahierte ANT.REGUL-Betrag.
     * @param hundertRaw Der Roh-Betrag der 100%-Spalte (könnte "7" oder "V" enthalten).
     * @param line Die gesamte Textzeile.
     * @return "V" wenn der Haken erkannt wird, sonst "".
     */
    private static String detectResidualSymbol(String antRegul, String hundertRaw, String line) {
        if (hundertRaw == null || antRegul == null) return "";

        // 1. Explizite Muster (V, V/, v, V,, etc.)
        // Wir suchen am Ende der Zeile nach den Haken-Variationen, die Tesseract ausgibt.
        Matcher explicit = Pattern.compile("[vV/,\\}]+$").matcher(line.replaceAll("\\s+$", ""));
        if (explicit.find()) return "V";

        // 2. Fehlerhafte '7'-Anhängsel (z.B. "1945007" statt "1945,00")

        // Wir vergleichen die numerischen Werte. Wenn sie abweichen und hundertRaw mit einer
        // potenziellen '7'-Fehllesung endet, gehen wir von einem Haken aus.
        double valAnt = getNumericValue(antRegul);
        double valHund = getNumericValue(hundertRaw);

        // Holen Sie den Teil des 100%-Strings, der nicht zur Zahl gehört.
        // Bsp: 1945007. Der numerische Teil ist 194500. Der Rest ist 7.
        String numericPart = hundertRaw.replaceAll("[^0-9,\\.-]", "").replace(".", "").replace(',', '.');
        String remainder = "";

        try {
            // Finden Sie den numerischen Teil im Roh-String
            Pattern p = Pattern.compile(Pattern.quote(numericPart));
            Matcher m = p.matcher(hundertRaw);
            if (m.find()) {
                remainder = hundertRaw.substring(m.end()).replaceAll("\\s", "");
            }
        } catch (Exception e) {
            // Ignorieren
        }

        // Wenn der numerische Wert nicht übereinstimmt ODER der Rest ein potenzielles V ist
        if (Math.abs(valAnt - valHund) > 0.01 || remainder.matches("^[vV/,}7]+$")) {
            // Der Fehler, dass Tesseract das Tausendertrennzeichen weglässt und die 7 anhängt,
            // führt zu einer großen Diskrepanz zwischen valAnt (1945.00) und valHund (1945007).
            // Wenn diese Diskrepanz auftritt UND der Rohwert mit 7, 7, oder 7} endet, ist es ein 'V'.
            if (hundertRaw.matches(".*[7},]*$")) {
                return "V";
            }
        }

        return "";
    }

    /**
     * Normalisiert das Datumsformat (z.B. "3.12.2024" -> "3.12.2024").
     */
    private static String normalizeDate(String src) {
        String[] p = src.replace(',', '.').split("\\.");
        if (p.length != 3) return src.replace(',', '.');
        String d = String.valueOf(Integer.parseInt(p[0]));
        String m = (p[1].length() == 1 ? "0" + p[1] : p[1]);
        return d + "." + m + "." + p[2];
    }

    /**
     * Filtert alle Datensätze heraus, die keinen 'V'-Haken in der Spalte 'V' haben.
     */
    private static List<Map<String, String>> filterWithoutV(List<Map<String, String>> in) {
        return in.stream()
                .filter(r -> r.getOrDefault("V", "").isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Gibt die Datensätze in einem tabellarischen Format aus.
     */
    private static void printTable(List<Map<String, String>> rows, String title) {
        System.out.println(title);
        System.out.printf("%-15s %-15s %-15s %-12s %-10s %-5s %-5s %-10s %-12s %-12s %-7s%n",
                "POLICE", "VN", "SCHA-NR", "SCH-DATUM", "BUCHUNG", "VA", "SA",
                "ANTEIL %", "ANT.REGUL", "100%", "V");
        System.out.println("---------------------------------------------------------------------------------------------------------------");
        for (Map<String, String> r : rows) {
            System.out.printf("%-15s %-15s %-15s %-12s %-10s %-5s %-5s %-10s %-12s %-12s %-7s%n",
                    r.getOrDefault("POLICE", ""),
                    r.getOrDefault("VN", ""),
                    r.getOrDefault("SCHA-NR", ""),
                    r.getOrDefault("SCH-DATUM", ""),
                    r.getOrDefault("BUCHUNG", ""),
                    r.getOrDefault("VA", ""),
                    r.getOrDefault("SA", ""),
                    r.getOrDefault("ANTEIL %", ""),
                    r.getOrDefault("ANT.REGUL", ""),
                    r.getOrDefault("100%", ""),
                    r.getOrDefault("V", ""));
        }
    }
}