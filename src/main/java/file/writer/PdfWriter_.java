package file.writer;

import formatter.op.OpListeFormatter;
import model.RowData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PDF-Writer für tabellarische Exporte (z. B. OP-Listen) auf Basis von PDFBox.
 * Erzeugt eine Querformat-Seite, zeichnet Kopf-/Fußbereich und rendert eine Tabelle
 * mit Spaltenüberschriften und Datenzeilen.
 *
 * Sicherheitsfeatures:
 * - AES-256 Verschlüsselung
 * - Kopieren/Einfügen deaktiviert
 * - Dokumenten-Hash für Integritätsprüfung
 */
public class PdfWriter_ implements DataWriter {

    private static final java.util.Set<String> RED_COLUMNS = new java.util.HashSet<>(java.util.List.of(
            "Invoice No.", "Policy No.", "Year", "Policy holder", "Invoice date", "Due date", "Currency",
            "Settlement amount", "Payment amount/Partial payment", "Balance",
            "Rg-NR", "Policen-Nr", "Zeichnungsjahr", "Versicherungsnehmer", "Rg-Datum", "Fälligkeit", "Währung",
            "Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO"
    ));

    private final float margin = 50;
    private PDDocument document;
    private PDPageContentStream contentStream;
    private float yPosition;
    private float tableWidth;
    private PDPage currentPage;
    private int pageIndex;

    // Für Integritätsprüfung
    private String documentHash;
    private String documentId;

    /**
     * Erstellt einen neuen PDF-Writer.
     * Ressourcen (Dokument/Streams) werden pro Export in writeCustomData erzeugt.
     */
    public PdfWriter_() {
    }

    private java.time.LocalDate parseAnyDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception ignore) {
        }
        try {
            return java.time.LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception ignore) {
        }
        return null;
    }

    private boolean shouldHighlightRow(java.util.Map<String, String> v) {
        String inv = v.getOrDefault("Rg-Datum", v.getOrDefault("Invoice date", ""));
        String due = v.getOrDefault("Fälligkeit", v.getOrDefault("Due date", ""));
        var d1 = parseAnyDate(inv);
        var d2 = parseAnyDate(due);

        boolean old =
                (d1 != null && d1.getYear() <= 2024) ||
                        (d2 != null && d2.getYear() <= 2024) ||
                        (d1 == null && d2 == null &&
                                (v.getOrDefault("Year", v.getOrDefault("Zeichnungsjahr", "")).matches("\\d{4}") &&
                                        Integer.parseInt(v.getOrDefault("Year", v.getOrDefault("Zeichnungsjahr", "9999"))) <= 2024));

        double saldo = OpListeFormatter.parseDouble(v.getOrDefault("SALDO", v.getOrDefault("Balance", "0")));
        return old && (saldo > 0.0);
    }

    /**
     * Kürzt einen Text so, dass er in die angegebene Zellbreite passt.
     * Falls nötig, wird der Text mit "..." abgeschnitten.
     */
    private String fitTextToCell(String text, PDFont font, float fontSize, float cellWidth) throws IOException {
        if (text == null) {
            return "";
        }
        String original = text.trim();
        if (original.isEmpty()) {
            return "";
        }

        float maxWidth = cellWidth - 2f;
        String candidate = original;

        while (!candidate.isEmpty()) {
            float w = font.getStringWidth(candidate) / 1000f * fontSize;
            if (w <= maxWidth) {
                break;
            }
            if (candidate.length() <= 4) {
                candidate = candidate.substring(0, 1);
                break;
            }
            candidate = candidate.substring(0, candidate.length() - 2);
        }

        if (!candidate.equals(original) && candidate.length() >= 3) {
            candidate = candidate.substring(0, candidate.length() - 3) + "...";
        }

        return candidate;
    }

    /**
     * Génère un hash SHA-256 du contenu du document pour vérification d'intégrité.
     */
    private String generateDocumentHash(List<RowData> data, List<String> headers) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder content = new StringBuilder();

            // Ajouter les headers
            for (String header : headers) {
                content.append(header).append("|");
            }

            // Ajouter les données
            for (RowData row : data) {
                for (Map.Entry<String, String> entry : row.getValues().entrySet()) {
                    content.append(entry.getKey()).append("=").append(entry.getValue()).append("|");
                }
            }

            // Ajouter timestamp
            content.append(System.currentTimeMillis());

            byte[] hashBytes = digest.digest(content.toString().getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Retourne le hash du document généré (pour stockage en base de données).
     */
    public String getDocumentHash() {
        return documentHash;
    }

    /**
     * Retourne l'ID unique du document généré.
     */
    public String getDocumentId() {
        return documentId;
    }

    /**
     * Schreibt die übergebenen Daten als Tabelle in eine neue PDF-Datei.
     * Erzeugt bei Bedarf mehrere Querformat-Seiten, zeichnet auf JEDER Seite:
     * <ul>
     *     <li>Header mit kleinem Logo links</li>
     *     <li>Tabelleninhalt (über alle Seiten verteilt)</li>
     *     <li>Footer mit Firmenblock + Seitennummer rechts oben</li>
     * </ul>
     * <p>
     * Vor dem Speichern wird das Dokument mit AES-256 verschlüsselt und
     * Kopieren/Einfügen ist deaktiviert.
     * </p>
     *
     * @param data       Zeileninhalte
     * @param headers    Spaltenüberschriften (Anzeigetexte)
     * @param outputPath Zielpfad der PDF-Datei
     * @throws IOException bei I/O-Fehlern
     */
    public void writeCustomData(List<RowData> data,
                                List<String> headers,
                                String outputPath) throws IOException {

        document = new PDDocument();
        pageIndex = 0;

        // Générer ID et hash du document
        documentId = UUID.randomUUID().toString();
        documentHash = generateDocumentHash(data, headers);

        Map<String, String> headerToKeyMap = OpListeFormatter.createHeaderToKeyMap(headers);

        // Erste Seite anlegen
        startNewPage();

        // Tabelle inkl. evtl. Seitenumbrüchen zeichnen
        addTable(data, headers, headerToKeyMap);

        // Footer auf der letzten Seite
        addFooterWithText();

        // Haupt-ContentStream schließen
        contentStream.close();

        // Zweiter Durchgang: Seitennummern rechts oben im Footer
        addPageNumbers();

        // PDF mit AES-256 verschlüsseln und Kopieren deaktivieren
        protectWithEncryption();

        // Speichern & schließen
        document.save(outputPath);
        document.close();
    }

    /**
     * Setzt PDF-Berechtigungen mit AES-256 Verschlüsselung.
     * Kopieren erlaubt, aber Bearbeitung erfordert Passwort.
     */
    private void protectWithEncryption() throws IOException {
        AccessPermission ap = new AccessPermission();

        // Bearbeitung verbieten (erfordert Owner-Passwort)
        ap.setCanModify(false);
        ap.setCanModifyAnnotations(false);
        ap.setCanFillInForm(false);

        // Kopieren/Lesen/Drucken ERLAUBT
        ap.setCanExtractContent(true);
        ap.setCanExtractForAccessibility(true);
        ap.setCanPrint(true);
        ap.setCanPrintFaithful(true);

        // Owner-Passwort für Bearbeitung
        String ownerPassword = "vias-op-owner";
        String userPassword = ""; // kein Passwort zum Öffnen

        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPassword, userPassword, ap);
        spp.setEncryptionKeyLength(256);  // AES-256
        spp.setPermissions(ap);

        document.protect(spp);
    }


    /**
     * Legt eine neue Querformat-A4-Seite an, erzeugt einen ContentStream
     * und zeichnet den Header (kleines Logo links). Die Y-Position für den
     * Tabellenanfang wird aktualisiert.
     */
    private void startNewPage() throws IOException {
        PDRectangle a4 = PDRectangle.A4;
        currentPage = new PDPage(new PDRectangle(a4.getHeight(), a4.getWidth()));
        document.addPage(currentPage);

        contentStream = new PDPageContentStream(document, currentPage);
        pageIndex++;

        float pageWidth = currentPage.getMediaBox().getWidth();
        tableWidth = pageWidth - 2 * margin;

        // Header mit kleinem Logo LINKS zeichnen
        yPosition = addHeaderWithSmallLogoLeft(currentPage, "images/header.png");
    }

    /**
     * Zeichnet ein KLEINES Logo im Kopfbereich der Seite LINKS,
     * und liefert die Y-Position zurück, ab der der Tabelleninhalt
     * beginnen kann.
     */
    private float addHeaderWithSmallLogoLeft(PDPage page, String imagePath) throws IOException {
        float pageHeight = page.getMediaBox().getHeight();
        float topY = pageHeight - margin;

        try (InputStream is = getClass().getResourceAsStream("/" + imagePath)) {
            if (is == null) {
                return topY;
            }

            PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, is.readAllBytes(), imagePath);

            // PETIT Logo - hauteur maximale réduite à 35px (était 70px)
            float maxHeaderHeight = 35f;
            float maxHeaderWidth = 120f;  // Largeur maximale limitée

            float imageRatio = (float) pdImage.getHeight() / pdImage.getWidth();
            float headerWidth = maxHeaderWidth;
            float headerHeight = headerWidth * imageRatio;

            // Si la hauteur dépasse le max, ajuster
            if (headerHeight > maxHeaderHeight) {
                headerHeight = maxHeaderHeight;
                headerWidth = headerHeight / imageRatio;
            }

            // Position à GAUCHE (au lieu de centré)
            float xPosition = margin;
            float yPositionHeader = topY - headerHeight;

            contentStream.drawImage(pdImage, xPosition, yPositionHeader, headerWidth, headerHeight);

            // Abstand zwischen Logo und Tabelle
            return yPositionHeader - 15f;
        }
    }

    /**
     * Zeichnet den Footer mit Firmenblock (ohne Seitennummer) auf der aktuellen Seite.
     */
    private void addFooterWithText() throws IOException {
        if (currentPage == null) {
            return;
        }

        float pageWidth = currentPage.getMediaBox().getWidth();
        float usableWidth = pageWidth - 2 * margin;

        final String footerText =
                "Carl Schröter GmbH & Co. KG\\Johann-Reiners-Platz 3\\D-28217 Bremen\\Postadresse: Postfach 101606\\D-28016 Bremen\\Telefon: +49 (0) 421 369 09-0 Telefax: +49 (0) 421 369 09-99 91\\E-Mail: mail@carlschroeter.de\\Amtsgericht Bremen HRA 27162PHG: Carl Schröter Verwaltungs-GmbH\\Amtsgericht Bremen HRB 30323\\GF: Sabine Blume, Moritz Dimter, Stefan Rogge, Markus Willmann Ust-IdNr.: DE 313999930\\Oldenburgische Landesbank AG\\IBAN: DE97 28020050 4669 9823 01\\BIC: OLBODEH2XXX";

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 6f;
        float leading = fontSize * 1.30f;
        float textY = margin;
        float textX = margin;

        List<String> wrappedLines = new ArrayList<>();
        String[] paragraphs = footerText.split("\\r?\\n", -1);
        for (String paragraph : paragraphs) {
            wrappedLines.addAll(wrapText(paragraph, font, fontSize, usableWidth));
        }

        float ascent = font.getFontDescriptor().getAscent() / 1000f * fontSize;
        float ruleY = textY + ascent + 3f;

        // Trennlinie über dem Footer
        contentStream.setStrokingColor(0f / 255f, 70f / 255f, 173f / 255f);
        contentStream.setLineWidth(0.5f);
        contentStream.moveTo(margin, ruleY);
        contentStream.lineTo(pageWidth - margin, ruleY);
        contentStream.stroke();

        // Footer-Text
        contentStream.beginText();
        contentStream.setNonStrokingColor(0f / 255f, 70f / 255f, 173f / 255f);

        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(0.3f);
        contentStream.setGraphicsStateParameters(gs);

        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(textX, textY);

        for (String line : wrappedLines) {
            contentStream.showText(line);
            contentStream.newLineAtOffset(0, -leading);
        }
        contentStream.endText();

        // Farben zurücksetzen
        contentStream.setNonStrokingColor(0, 0, 0);
    }

    /**
     * Fügt auf jeder Seite eine Seitennummer "N/Total" hinzu.
     * Position: RECHTS, direkt ÜBER der Signatur/Footer-Linie
     * Farbe: SCHWARZ, gut lesbar
     * Taille: petite mais lisible (8pt)
     */
    private void addPageNumbers() throws IOException {
        int totalPages = document.getNumberOfPages();
        if (totalPages == 0) {
            return;
        }

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 6f;  // Taille lisible mais petite

        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            float pageWidth = page.getMediaBox().getWidth();

            String label = "Seite " + (i + 1) + " / " + totalPages;
            float textWidth = font.getStringWidth(label) / 1000f * fontSize;

            // Position: à DROITE, au-dessus de la ligne de signature
            float x = pageWidth - margin - textWidth;
            // Juste au-dessus de la ligne de footer (qui est à margin + quelques pixels)
            float footerLineY = margin + 10f;  // La ligne de signature est environ ici
            float y = footerLineY + 8f;  // 8 pixels au-dessus de la ligne

            try (PDPageContentStream cs = new PDPageContentStream(
                    document,
                    page,
                    AppendMode.APPEND,
                    true,
                    true)) {

                // PAS de transparence - texte noir bien lisible
                cs.setNonStrokingColor(0f, 0f, 0f);  // NOIR

                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(x, y);
                cs.showText(label);
                cs.endText();
            }
        }
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float width = font.getStringWidth(testLine) / 1000f * fontSize;
            if (width > maxWidth) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private void addTable(List<RowData> data,
                          List<String> headers,
                          Map<String, String> headerToKeyMap) throws IOException {

        if (currentPage == null) {
            throw new IllegalStateException("Keine aktuelle Seite initialisiert – startNewPage() vor addTable() aufrufen.");
        }

        float pageWidth = currentPage.getMediaBox().getWidth();
        tableWidth = pageWidth - 2 * margin;
        float cellHeight = 12f;
        float headerFontSize = 5.5f;
        float cellFontSize = 5.5f;
        PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font cellFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        float cellWidth = tableWidth / Math.max(headers.size(), 1);

        float tableY = yPosition - 30;

        // --- Spaltenüberschriften ---
        contentStream.setFont(headerFont, headerFontSize);

        for (int i = 0; i < headers.size(); i++) {
            String rawHeaderText = headers.get(i);
            String headerText = fitTextToCell(rawHeaderText, headerFont, headerFontSize, cellWidth);

            contentStream.beginText();
            contentStream.newLineAtOffset(margin + i * cellWidth, tableY);
            contentStream.showText(headerText);
            contentStream.endText();
        }

        // Trennlinie direkt unter dem Header
        float lineY = tableY - 2f;
        contentStream.setStrokingColor(0f, 0f, 0f);
        contentStream.setLineWidth(0.3f);
        contentStream.moveTo(margin, lineY);
        contentStream.lineTo(pageWidth - margin, lineY);
        contentStream.stroke();

        tableY -= cellHeight;

        // --- Datenzeilen ---
        contentStream.setFont(cellFont, cellFontSize);

        for (RowData row : data) {
            boolean highlight = shouldHighlightRow(row.getValues());

            // Seitenumbruch, falls kein Platz mehr
            if (tableY < margin + cellHeight + 30) {  // +30 pour laisser de la place pour la numérotation
                // aktuelle Seite mit Footer abschließen
                addFooterWithText();
                contentStream.close();

                // neue Seite
                startNewPage();

                // Maße für neue Seite neu berechnen
                pageWidth = currentPage.getMediaBox().getWidth();
                tableWidth = pageWidth - 2 * margin;
                cellWidth = tableWidth / Math.max(headers.size(), 1);
                tableY = yPosition - 30;

                // Header auch auf neuer Seite
                contentStream.setFont(headerFont, headerFontSize);
                for (int i = 0; i < headers.size(); i++) {
                    String rawHeaderText = headers.get(i);
                    String headerText = fitTextToCell(rawHeaderText, headerFont, headerFontSize, cellWidth);

                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin + i * cellWidth, tableY);
                    contentStream.showText(headerText);
                    contentStream.endText();
                }
                float lineY2 = tableY - 2f;
                contentStream.setStrokingColor(0f, 0f, 0f);
                contentStream.setLineWidth(0.3f);
                contentStream.moveTo(margin, lineY2);
                contentStream.lineTo(pageWidth - margin, lineY2);
                contentStream.stroke();

                tableY -= cellHeight;

                // zurück zu Zellen-Font
                contentStream.setFont(cellFont, cellFontSize);
            }

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String key = headerToKeyMap.getOrDefault(header, header);
                String value = row.getValues().getOrDefault(key, "");

                if (highlight && RED_COLUMNS.contains(header)) {
                    contentStream.setNonStrokingColor(1f, 0f, 0f);
                } else {
                    contentStream.setNonStrokingColor(0f, 0f, 0f);
                }

                String cellText = fitTextToCell(value, cellFont, cellFontSize, cellWidth);

                contentStream.beginText();
                contentStream.newLineAtOffset(margin + i * cellWidth, tableY);
                contentStream.showText(cellText);
                contentStream.endText();
            }
            tableY -= cellHeight;
        }

        yPosition = tableY;
        writeTotals(data, headers, headerToKeyMap);
    }

    private void writeTotals(List<RowData> data, List<String> headers, Map<String, String> headerToKeyMap) throws IOException {
        contentStream.setStrokingColor(0, 0, 0);
        contentStream.setLineWidth(1);
        contentStream.moveTo(margin, yPosition + 5);
        contentStream.lineTo(margin + tableWidth, yPosition + 5);
        contentStream.stroke();
        yPosition -= 15;

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        contentStream.newLineAtOffset(margin, yPosition);
        contentStream.showText("Total:");
        contentStream.endText();

        float cellWidth = tableWidth / headers.size();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String key = headerToKeyMap.getOrDefault(header, header);

            if (header.contains("Abrechnungsbetrag") || header.contains("Zahlbetrag") || header.contains("SALDO") ||
                    header.contains("Settlement amount") || header.contains("Payment amount") || header.contains("Balance")) {

                double total = data.stream()
                        .mapToDouble(row -> {
                            try {
                                return OpListeFormatter.parseDouble(row.getValues().get(headerToKeyMap.get(header)));
                            } catch (NumberFormatException e) {
                                return 0.0;
                            }
                        })
                        .sum();

                String formattedTotal = String.format(Locale.GERMAN, "%,.2f €", total);

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                contentStream.newLineAtOffset(margin + i * cellWidth, yPosition);
                contentStream.showText(formattedTotal);
                contentStream.endText();
            }
        }
        yPosition -= 20;
    }

    @Override
    public void writeHeader(List<String> headers) {
    }

    @Override
    public void writeRow(RowData row) {
    }

    @Override
    public void writeFormattedRecord(List<String> formattedValues) {
    }

    @Override
    public void close() {
    }
}