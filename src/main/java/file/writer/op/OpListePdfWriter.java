package file.writer.op;

import file.writer.PdfWriter;

import formatter.op.OpListeFormatter;
import model.RowData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OpListePdfWriter extends PdfWriter {

    private static final java.util.Set<String> RED_COLUMNS = new java.util.HashSet<>(java.util.List.of(
            "Invoice No.", "Policy No.", "Year", "Policy holder", "Invoice date", "Due date", "Currency",
            "Settlement amount", "Payment amount/Partial payment", "Balance",
            "Rg-NR", "Policen-Nr", "Zeichnungsjahr", "Versicherungsnehmer", "Rg-Datum", "Fälligkeit", "Währung",
            "Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO"
    ));

    public OpListePdfWriter() {
        super();
    }

    public void writeCustomData(List<RowData> data, List<String> headers, String outputPath) throws IOException {
        document = new PDDocument();
        pageIndex = 0;

        Map<String, String> headerToKeyMap = OpListeFormatter.createHeaderToKeyMap(headers);

        startNewPage();
        addTable(data, headers, headerToKeyMap);
        addFooterWithText();
        contentStream.close();
        addPageNumbers();
        protectWithEncryption();

        document.save(outputPath);
        document.close();
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

    private boolean shouldHighlightRow(Map<String, String> v) {
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

    private void addTable(List<RowData> data, List<String> headers, Map<String, String> headerToKeyMap) throws IOException {
        if (currentPage == null) {
            throw new IllegalStateException("Keine aktuelle Seite initialisiert");
        }

        float pageWidth = currentPage.getMediaBox().getWidth();
        tableWidth = pageWidth - 2 * MARGIN;
        float cellHeight = 12f;
        float headerFontSize = 5.5f;
        float cellFontSize = 5.5f;
        PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font cellFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        float cellWidth = tableWidth / Math.max(headers.size(), 1);
        float tableY = yPosition - 30;

        contentStream.setFont(headerFont, headerFontSize);

        for (int i = 0; i < headers.size(); i++) {
            String rawHeaderText = headers.get(i);
            String headerText = fitTextToCell(rawHeaderText, headerFont, headerFontSize, cellWidth);

            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN + i * cellWidth, tableY);
            contentStream.showText(headerText);
            contentStream.endText();
        }

        float lineY = tableY - 2f;
        contentStream.setStrokingColor(0f, 0f, 0f);
        contentStream.setLineWidth(0.3f);
        contentStream.moveTo(MARGIN, lineY);
        contentStream.lineTo(pageWidth - MARGIN, lineY);
        contentStream.stroke();

        tableY -= cellHeight;
        contentStream.setFont(cellFont, cellFontSize);

        for (RowData row : data) {
            boolean highlight = shouldHighlightRow(row.getValues());

            if (tableY < MARGIN + cellHeight + 30) {
                addFooterWithText();
                contentStream.close();
                startNewPage();

                pageWidth = currentPage.getMediaBox().getWidth();
                tableWidth = pageWidth - 2 * MARGIN;
                cellWidth = tableWidth / Math.max(headers.size(), 1);
                tableY = yPosition - 30;

                contentStream.setFont(headerFont, headerFontSize);
                for (int i = 0; i < headers.size(); i++) {
                    String rawHeaderText = headers.get(i);
                    String headerText = fitTextToCell(rawHeaderText, headerFont, headerFontSize, cellWidth);

                    contentStream.beginText();
                    contentStream.newLineAtOffset(MARGIN + i * cellWidth, tableY);
                    contentStream.showText(headerText);
                    contentStream.endText();
                }

                float lineY2 = tableY - 2f;
                contentStream.setStrokingColor(0f, 0f, 0f);
                contentStream.setLineWidth(0.3f);
                contentStream.moveTo(MARGIN, lineY2);
                contentStream.lineTo(pageWidth - MARGIN, lineY2);
                contentStream.stroke();

                tableY -= cellHeight;
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
                contentStream.newLineAtOffset(MARGIN + i * cellWidth, tableY);
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
        contentStream.moveTo(MARGIN, yPosition + 5);
        contentStream.lineTo(MARGIN + tableWidth, yPosition + 5);
        contentStream.stroke();
        yPosition -= 15;

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("Total:");
        contentStream.endText();

        float cellWidth = tableWidth / headers.size();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);

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
                contentStream.newLineAtOffset(MARGIN + i * cellWidth, yPosition);
                contentStream.showText(formattedTotal);
                contentStream.endText();
            }
        }
        yPosition -= 20;
    }
}
