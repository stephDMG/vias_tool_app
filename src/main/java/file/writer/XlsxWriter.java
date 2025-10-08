package file.writer;

import formatter.op.OpListeFormatter;
import model.RowData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class XlsxWriter implements DataWriter {

    // Colonnes dont on colore les VALEURS (EN + DE)
    private static final Set<String> RED_COLUMNS = new HashSet<>(List.of(
            "Invoice No.", "Policy No.", "Year", "Policy holder", "Invoice date", "Due date", "Currency",
            "Settlement amount", "Payment amount/Partial payment", "Balance",
            "Rg-NR", "Policen-Nr", "Zeichnungsjahr", "Versicherungsnehmer", "Rg-Datum", "Fälligkeit", "Währung",
            "Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO"
    ));
    // Colonnes dates (EN + DE)
    private static final Set<String> DATE_HEADERS = new HashSet<>(List.of(
            "Rg-Datum", "Invoice date", "Fälligkeit", "Due date"
    ));
    // Colonnes montants (EN + DE)
    private static final Set<String> MONEY_HEADERS = new HashSet<>(List.of(
            "Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO",
            "Settlement amount", "Payment amount/Partial payment", "Balance"
    ));
    private final Workbook workbook;
    private final Sheet sheet;
    private final String outputPath;
    private final Map<Short, CellStyle> redStyleCacheByDataFormat = new HashMap<>();
    private int rowIndex = 0;
    private Font redFont;

    public XlsxWriter(String outputPath) {
        this.outputPath = outputPath;
        this.workbook = new XSSFWorkbook();
        this.sheet = workbook.createSheet("Export");
    }

    // ---------- Utils ----------
    private LocalDate parseAnyDate(String s) {
        if (s == null || s.isBlank()) return null;
        // 1) essai sur dd.MM.yyyy (c’est ce que tu produis)
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception ignore) {
        }
        // 2) fallback sur yyyyMMdd (format brut DB)
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception ignore) {
        }
        return null;
    }

    private boolean shouldHighlightRow(Map<String, String> v) {
        String inv = v.getOrDefault("Rg-Datum", v.getOrDefault("Invoice date", ""));
        String due = v.getOrDefault("Fälligkeit", v.getOrDefault("Due date", ""));

        LocalDate d1 = parseAnyDate(inv);
        LocalDate d2 = parseAnyDate(due);

        boolean old =
                (d1 != null && d1.getYear() <= 2024) ||
                        (d2 != null && d2.getYear() <= 2024) ||
                        (d1 == null && d2 == null &&
                                (v.getOrDefault("Year", v.getOrDefault("Zeichnungsjahr", "")).matches("\\d{4}") &&
                                        Integer.parseInt(v.getOrDefault("Year", v.getOrDefault("Zeichnungsjahr", "9999"))) <= 2024));

        double saldo = OpListeFormatter.parseDouble(v.getOrDefault("SALDO", v.getOrDefault("Balance", "0")));
        return old && (saldo > 0.0);
    }

    private List<Integer> targetColumnIndexes(List<String> headers) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) if (RED_COLUMNS.contains(headers.get(i))) idx.add(i);
        return idx;
    }

    private CellStyle redStyleFor(Cell cell) {
        if (redFont == null) {
            redFont = workbook.createFont();
            redFont.setColor(IndexedColors.RED.getIndex());
        }
        CellStyle base = cell.getCellStyle();
        short fmt = (base == null) ? (short) 0 : base.getDataFormat();
        return redStyleCacheByDataFormat.computeIfAbsent(fmt, f -> {
            CellStyle st = workbook.createCellStyle();
            if (base != null) st.cloneStyleFrom(base); // conserve format nb/date
            st.setFont(redFont);
            return st;
        });
    }

    private void paintCellsRed(Row row, List<Integer> cols) {
        for (int i : cols) {
            Cell c = row.getCell(i);
            if (c != null) c.setCellStyle(redStyleFor(c));
        }
    }

    // ---------- Header ----------
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

    // ---------- Ecriture cellule avec typage sûr ----------
    private void writeCell(Cell cell, String value, String header) {
        if (value == null || value.trim().isEmpty()) {
            cell.setCellValue("");
            return;
        }

        // 1) Date ?
        if (DATE_HEADERS.contains(header)) {
            LocalDate date = parseAnyDate(value);
            if (date != null) {
                cell.setCellValue(date);
                CellStyle dateStyle = workbook.createCellStyle();
                CreationHelper ch = workbook.getCreationHelper();
                dateStyle.setDataFormat(ch.createDataFormat().getFormat("dd.MM.yyyy"));
                cell.setCellStyle(dateStyle);
                return;
            }
            // si non parsable, on laisse en texte
            cell.setCellValue(value);
            return;
        }

        // 2) Montant ?
        if (MONEY_HEADERS.contains(header)) {
            double numericValue = OpListeFormatter.parseDouble(value);
            cell.setCellValue(numericValue);
            CellStyle money = workbook.createCellStyle();
            CreationHelper ch = workbook.getCreationHelper();
            money.setDataFormat(ch.createDataFormat().getFormat("#,##0.00"));
            cell.setCellStyle(money);
            return;
        }

        // 3) Sinon texte (ne pas convertir les N° de facture, policy, etc.)
        cell.setCellValue(value);
    }

    // ---------- API DataWriter ----------
    @Override
    public void writeRow(RowData row) throws IOException {
        // non utilisé dans ton flux – OK de laisser vide ou d’appeler une implémentation si besoin
    }

    @Override
    public void writeFormattedRecord(List<String> formattedValues) throws IOException {
        // Méthode "générique" SANS entêtes : on ne typpe pas agressivement (tout en texte)
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < formattedValues.size(); i++) {
            row.createCell(i).setCellValue(formattedValues.get(i) == null ? "" : formattedValues.get(i));
        }
    }

    @Override
    public void writeCustomData(List<RowData> data, List<String> headers) throws IOException {
        if (data == null || data.isEmpty()) {
            writeHeader(headers);
            return;
        }

        writeHeader(headers);
        var redIdx = targetColumnIndexes(headers);
        Map<String, String> headerToKeyMap = OpListeFormatter.createHeaderToKeyMap(headers);

        for (RowData rd : data) {
            Row newRow = sheet.createRow(rowIndex++);
            int col = 0;
            for (String header : headers) {
                String key = headerToKeyMap.getOrDefault(header, header); // map EN->DE si besoin
                String value = rd.getValues().getOrDefault(key, "");
                writeCell(newRow.createCell(col++), value, header);       // typage date/€ uniquement
            }
            if (shouldHighlightRow(rd.getValues())) {
                paintCellsRed(newRow, redIdx);                            // 🔴 valeurs des colonnes ciblées
            }
        }
    }

    private void writeTotals(List<RowData> data, List<String> headers, Map<String, String> headerToKeyMap) throws IOException {
        Row totalRow = sheet.createRow(rowIndex++);
        int columnIndex = 0;

        for (String header : headers) {
            Cell cell = totalRow.createCell(columnIndex);
            String key = headerToKeyMap.get(header);

            if (key != null && MONEY_HEADERS.contains(header)) {
                double total = data.stream()
                        .mapToDouble(row -> {
                            try {
                                return OpListeFormatter.parseDouble(row.getValues().get(key));
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
                CreationHelper ch = workbook.getCreationHelper();
                style.setDataFormat(ch.createDataFormat().getFormat("#,##0.00 €"));
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
        var redIdx = targetColumnIndexes(headers);

        for (RowData rd : data) {
            Row newRow = sheet.createRow(rowIndex++);
            int columnIndex = 0;
            for (String header : headers) {
                String key = headerToKeyMap.getOrDefault(header, header);
                String value = rd.getValues().getOrDefault(key, "");
                writeCell(newRow.createCell(columnIndex++), value, header);
            }
            if (shouldHighlightRow(rd.getValues())) {
                paintCellsRed(newRow, redIdx);
            }
        }
        writeTotals(data, headers, headerToKeyMap);
    }

    @Override
    public void close() throws IOException {
        for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
            sheet.autoSizeColumn(i);
        }
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            workbook.write(fos);
        }
        workbook.close();
    }
}
