package file.writer;

import model.RowData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public abstract class PdfWriter implements DataWriter {

    protected static final float MARGIN = 50;
    protected PDDocument document;
    protected PDPageContentStream contentStream;
    protected float yPosition;
    protected float tableWidth;
    protected PDPage currentPage;
    protected int pageIndex;

    public PdfWriter() {
    }

    protected void startNewPage() throws IOException {
        PDRectangle a4 = PDRectangle.A4;
        currentPage = new PDPage(new PDRectangle(a4.getHeight(), a4.getWidth()));
        document.addPage(currentPage);

        contentStream = new PDPageContentStream(document, currentPage);
        pageIndex++;

        float pageWidth = currentPage.getMediaBox().getWidth();
        tableWidth = pageWidth - 2 * MARGIN;

        yPosition = addHeaderWithSmallLogoLeft(currentPage, "images/header.png");
    }

    private float addHeaderWithSmallLogoLeft(PDPage page, String imagePath) throws IOException {
        float pageHeight = page.getMediaBox().getHeight();
        float topY = pageHeight - MARGIN;

        try (InputStream is = getClass().getResourceAsStream("/" + imagePath)) {
            if (is == null) {
                return topY;
            }

            PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, is.readAllBytes(), imagePath);

            float maxHeaderHeight = 35f;
            float maxHeaderWidth = 120f;

            float imageRatio = (float) pdImage.getHeight() / pdImage.getWidth();
            float headerWidth = maxHeaderWidth;
            float headerHeight = headerWidth * imageRatio;

            if (headerHeight > maxHeaderHeight) {
                headerHeight = maxHeaderHeight;
                headerWidth = headerHeight / imageRatio;
            }

            float xPosition = MARGIN;
            float yPositionHeader = topY - headerHeight;

            contentStream.drawImage(pdImage, xPosition, yPositionHeader, headerWidth, headerHeight);

            return yPositionHeader - 15f;
        }
    }

    protected void addFooterWithText() throws IOException {
        if (currentPage == null) {
            return;
        }

        float pageWidth = currentPage.getMediaBox().getWidth();
        float usableWidth = pageWidth - 2 * MARGIN;

        final String footerText =
                "Carl Schröter GmbH & Co. KG\\Johann-Reiners-Platz 3\\D-28217 Bremen\\Postadresse: Postfach 101606\\D-28016 Bremen\\Telefon: +49 (0) 421 369 09-0 Telefax: +49 (0) 421 369 09-99 91\\E-Mail: mail@carlschroeter.de\\Amtsgericht Bremen HRA 27162PHG: Carl Schröter Verwaltungs-GmbH\\Amtsgericht Bremen HRB 30323\\GF: Sabine Blume, Moritz Dimter, Stefan Rogge, Markus Willmann Ust-IdNr.: DE 313999930\\Oldenburgische Landesbank AG\\IBAN: DE97 28020050 4669 9823 01\\BIC: OLBODEH2XXX";

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 6f;
        float leading = fontSize * 1.30f;
        float textY = MARGIN;
        float textX = MARGIN;

        List<String> wrappedLines = new ArrayList<>();
        String[] paragraphs = footerText.split("\\r?\\n", -1);
        for (String paragraph : paragraphs) {
            wrappedLines.addAll(wrapText(paragraph, font, fontSize, usableWidth));
        }

        float ascent = font.getFontDescriptor().getAscent() / 1000f * fontSize;
        float ruleY = textY + ascent + 3f;

        contentStream.setStrokingColor(0f / 255f, 70f / 255f, 173f / 255f);
        contentStream.setLineWidth(0.5f);
        contentStream.moveTo(MARGIN, ruleY);
        contentStream.lineTo(pageWidth - MARGIN, ruleY);
        contentStream.stroke();

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

        contentStream.setNonStrokingColor(0, 0, 0);
    }

    protected void addPageNumbers() throws IOException {
        int totalPages = document.getNumberOfPages();
        if (totalPages == 0) {
            return;
        }

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 8f;

        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            float pageWidth = page.getMediaBox().getWidth();

            String label = "Seite " + (i + 1) + " / " + totalPages;
            float textWidth = font.getStringWidth(label) / 1000f * fontSize;

            float x = pageWidth - MARGIN - textWidth;
            float footerLineY = MARGIN + 10f;
            float y = footerLineY + 8f;

            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, AppendMode.APPEND, true, true)) {

                cs.setNonStrokingColor(0f, 0f, 0f);
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(x, y);
                cs.showText(label);
                cs.endText();
            }
        }
    }

    protected void protectWithEncryption() throws IOException {
        AccessPermission ap = new AccessPermission();

        ap.setCanModify(false);
        ap.setCanModifyAnnotations(false);
        ap.setCanFillInForm(false);

        ap.setCanExtractContent(true);
        ap.setCanExtractForAccessibility(true);
        ap.setCanPrint(true);
        ap.setCanPrintFaithful(true);

        String ownerPassword = "vias-op-owner";
        String userPassword = "";

        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPassword, userPassword, ap);
        spp.setEncryptionKeyLength(256);
        spp.setPermissions(ap);

        document.protect(spp);
    }

    protected List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
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

    protected String fitTextToCell(String text, PDFont font, float fontSize, float cellWidth) throws IOException {
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