package file.writer;

import formatter.OpListeFormatter;
import model.RowData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class PdfWriter implements DataWriter {

    private PDDocument document;
    private PDPageContentStream contentStream;
    private final float margin = 50;
    private float yPosition;
    private float tableWidth;

    public PdfWriter() {
    }

    public void writeCustomData(List<RowData> data, List<String> headers, String outputPath) throws IOException {
        document = new PDDocument();
        PDRectangle a4 = PDRectangle.A4;
        PDPage page = new PDPage(new PDRectangle(a4.getHeight(), a4.getWidth()));
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        tableWidth = page.getMediaBox().getWidth() - 2 * margin;
        yPosition = page.getMediaBox().getHeight() - margin;

        Map<String, String> headerToKeyMap = OpListeFormatter.createHeaderToKeyMap(headers);

        // 1. En-tête avec image
        addHeaderWithImage("images/header.png");

        // 2. Tableau de données
        addTable(data, headers, headerToKeyMap);

        // 3. Pied de page
        addFooterWithText();

        contentStream.close();
        document.save(outputPath);
        document.close();
    }

    private void addHeaderWithImage(String imagePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/" + imagePath)) {
            if (is != null) {
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, is.readAllBytes(), imagePath);

                PDPage firstPage = document.getPage(0);
                float pageWidth = firstPage.getMediaBox().getWidth()/3;
                float pageHeight = firstPage.getMediaBox().getHeight();

                float headerWidth = pageWidth - 2 * margin;

                float imageRatio = (float) pdImage.getHeight() / pdImage.getWidth();
                float headerHeight = headerWidth * imageRatio;

                float xPosition = margin;
                float yPositionHeader = pageHeight - margin - headerHeight;
                contentStream.drawImage(pdImage, xPosition, yPositionHeader, headerWidth, headerHeight);

                yPosition = yPositionHeader - 20;
            }
        }

    }

    private void addFooterWithText() throws IOException {
        PDPage currentPage = document.getPage(document.getNumberOfPages() - 1);
        float pageWidth = currentPage.getMediaBox().getWidth();
        float usableWidth = pageWidth - 2 * margin;

        final String footerText ="Carl Schröter GmbH & Co. KG\\Johann-Reiners-Platz 3\\D-28217 Bremen\\Postadresse: Postfach 101606\\D-28016 Bremen\\Telefon: +49 (0) 421 369 09-0 Telefax: +49 (0) 421 369 09-99 91\\E-Mail: mail@carlschroeter.de\\Amtsgericht Bremen HRA 27162PHG: Carl Schröter Verwaltungs-GmbH\\Amtsgericht Bremen HRB 30323\\GF: Sabine Blume, Moritz Dimter, Stefan Rogge, Markus Willmann Ust-IdNr.: DE 313999930\\Oldenburgische Landesbank AG\\IBAN: DE97 28020050 4669 9823 01\\BIC: OLBODEH2XXX"
                ;

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
        //contentStream.setNonStrokingColor(0f/255f, 70f/255f, 173f/255f);
        contentStream.setStrokingColor(0f/255f, 70f/255f, 173f/255f);
        contentStream.setLineWidth(0.5f);
        contentStream.moveTo(margin, ruleY);
        contentStream.lineTo(pageWidth - margin, ruleY);
        contentStream.stroke();

        contentStream.beginText();
        contentStream.setNonStrokingColor(0f/255f, 70f/255f, 173f/255f);

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


    private void addFooterWithImage(String imagePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/" + imagePath)) {
            if (is != null) {
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, is.readAllBytes(), imagePath);

                PDPage currentPage = document.getPage(document.getNumberOfPages() - 1);
                float pageWidth = currentPage.getMediaBox().getWidth();
                float pageHeight = currentPage.getMediaBox().getHeight();

                float footerWidth = pageWidth - 2 * margin;
                float imageRatio = (float) pdImage.getHeight() / pdImage.getWidth();
                float footerHeight = footerWidth * imageRatio;

                float xPosition = margin;
                float yPositionFooter = margin;

                contentStream.drawImage(pdImage, xPosition, yPositionFooter, footerWidth, footerHeight);
            } else {
                System.err.println("Footer image not found: " + imagePath);
            }
        }
    }


    private void addTable(List<RowData> data, List<String> headers, Map<String, String> headerToKeyMap) throws IOException {

        PDPage currentPage = document.getPage(document.getNumberOfPages() - 1);
        float pageWidth = currentPage.getMediaBox().getWidth();
        float tableWidth = pageWidth - 2 * margin;
        float cellHeight = 12;
        float cellWidth = tableWidth / headers.size();
        //List<String> moneyHeaders = List.of("Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO");

        float tableY = yPosition - 30;

        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), (float) 5.5);
        for (int i = 0; i < headers.size(); i++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(margin + i * cellWidth, tableY);

            String headerText = headers.get(i);
            if (headerText.length() > 20) {
                headerText = headerText.substring(0, 17) + "...";
            }
            contentStream.showText(headerText);
            contentStream.endText();
        }
        tableY -= cellHeight;

        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), (float) 5.5);
        for (RowData row : data) {
            if (tableY < margin + cellHeight) {
                contentStream.close();
                PDRectangle a4 = PDRectangle.A4;
                PDRectangle landscape = new PDRectangle(a4.getHeight(), a4.getWidth());
                PDPage newPage = new PDPage(landscape);
                document.addPage(newPage);

                contentStream = new PDPageContentStream(document, newPage);
                tableY = newPage.getMediaBox().getHeight() - margin;
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), (float) 5.5);
            }

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String key = headerToKeyMap.getOrDefault(header, header);
                String value = row.getValues().getOrDefault(key, "");

                contentStream.beginText();
                contentStream.newLineAtOffset(margin + i * cellWidth, tableY);

                String cellText = value;
                if (cellText.length() > 30) {
                    cellText = cellText.substring(0, 27) + "...";
                }
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
                            String value = row.getValues().getOrDefault(key, "0,00");
                            try {
                                return Double.parseDouble(value.replace(",", "."));
                            } catch (NumberFormatException e) {
                                return 0.0;
                            }
                        })
                        .sum();

                String formattedTotal = String.format(Locale.GERMAN, "%,.2f", total) + " €";

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
    public void writeHeader(List<String> headers) {}
    @Override
    public void writeRow(RowData row) {}
    @Override
    public void writeFormattedRecord(List<String> formattedValues) {}
    @Override
    public void close() {}
}