package file.writer;

import formatter.ColumnValueFormatter;
import formatter.OpListeFormatter;
import model.RowData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Excel Writer - basiert auf Ihrem funktionierenden Code.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class XlsxWriter implements DataWriter {

    private final Workbook workbook;
    private final Sheet sheet;
    private final String outputPath;
    private int rowIndex = 0;

    public XlsxWriter(String outputPath) {
        this.outputPath = outputPath;
        this.workbook = new XSSFWorkbook();
        this.sheet = workbook.createSheet("Export");
    }

    @Override
    public void writeHeader(List<String> headers) throws IOException {
        Row headerRow = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(createProfessionalHeaderStyle());
        }

        sheet.createFreezePane(0, rowIndex);
    }

    private CellStyle createProfessionalHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    @Override
    public void writeRow(RowData row) throws IOException {
        writeFormattedRow(row); // ✅ Formatierung verwenden
    }

    @Override
    public void writeFormattedRecord(List<String> formattedValues) throws IOException {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < formattedValues.size(); i++) {
            String value = formattedValues.get(i);
            Cell cell = row.createCell(i);
            try {
                double numericValue = Double.parseDouble(value.replace(",", "."));
                cell.setCellValue(numericValue);
            } catch (NumberFormatException e) {
                cell.setCellValue(value);
            }
        }
    }

    private void writeCell(Cell cell, String value, String header) {
        if (value == null || value.trim().isEmpty()) {
            cell.setCellValue("");
            return;
        }

        List<String> moneyHeaders = List.of(
                "Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO",
                "Settlement amount", "Payment amount/Partial payment", "Balance"
        );


        try {
            double numericValue = Double.parseDouble(value.replace(",", "."));
            cell.setCellValue(numericValue);

            if (moneyHeaders.contains(header)) {
                CellStyle style = workbook.createCellStyle();
                CreationHelper createHelper = workbook.getCreationHelper();
                style.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00"));
                cell.setCellStyle(style);
            }
        } catch (NumberFormatException e) {
            try {
                LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd"));
                cell.setCellValue(date);
                CellStyle dateStyle = workbook.createCellStyle();
                CreationHelper createHelper = workbook.getCreationHelper();
                dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd.MM.yyyy"));
                cell.setCellStyle(dateStyle);
            } catch (DateTimeParseException dateEx) {
                cell.setCellValue(value);
            }
        }
    }


    @Override
    public void writeCustomData(List<RowData> data, List<String> headers) throws IOException {
        if (data == null || data.isEmpty()) {
            writeHeader(headers);
            return;
        }

        writeHeader(headers);

        for (RowData row : data) {
            List<String> formattedValues = headers.stream()
                    .map(header -> row.getValues().getOrDefault(header, ""))
                    .toList();
            writeFormattedRecord(formattedValues);
        }
    }

    private void writeTotals(List<RowData> data, List<String> headers, Map<String, String> headerToKeyMap) throws IOException {
        Row totalRow = sheet.createRow(rowIndex++);
        int columnIndex = 0;

        List<String> totalizableKeys = List.of(  "Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO",
                "Settlement amount", "Payment amount/Partial payment", "Balance");

        for (String header : headers) {
            Cell cell = totalRow.createCell(columnIndex);
            if (headerToKeyMap.get(header) != null && totalizableKeys.contains(headerToKeyMap.get(header))) {
                double total = data.stream()
                        .mapToDouble(row -> {
                            try {
                                return Double.parseDouble(row.getValues().get(headerToKeyMap.get(header)).replace(",", "."));
                            } catch (Exception e) {
                                return 0.0;
                            }
                        })
                        .sum();
                cell.setCellValue(total);

                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                CreationHelper createHelper = workbook.getCreationHelper();
                style.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00 €"));
                cell.setCellStyle(style);

            } else if (columnIndex == 0) {
                cell.setCellValue("Total:");
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }
            columnIndex++;
        }
    }


    public void writeOpList(List<RowData> data, List<String> headers) throws IOException {
        if (data == null || data.isEmpty()) {
            writeHeader(headers);
            return;
        }

        Map<String, String> headerToKeyMap = OpListeFormatter.createHeaderToKeyMap(headers);
        writeHeader(headers);

        for (RowData row : data) {
            Row newRow = sheet.createRow(rowIndex++);
            int columnIndex = 0;
            for (String header : headers) {
                String key = headerToKeyMap.getOrDefault(header, header);
                String value = row.getValues().getOrDefault(key, "");
                // Passer le header ici
                writeCell(newRow.createCell(columnIndex++), value, header);
            }
        }
        writeTotals(data, headers, headerToKeyMap);
    }


    @Override
    public void close() throws IOException {
        for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
            sheet.autoSizeColumn(i); //
        }

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            workbook.write(fos);
        }
        workbook.close();
    }
}
