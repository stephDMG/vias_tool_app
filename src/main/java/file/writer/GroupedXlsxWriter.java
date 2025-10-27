package file.writer;

import model.RowData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.util.*;

/**
 * Gruppierter XLSX-Writer - erstellt hierarchische Excel-Dateien wie in Image 1.
 *
 * Erzeugt Excel-Dateien mit:
 * - Hierarchischer Gruppierung (mit +/- Buttons zum Aus-/Einklappen)
 * - Gruppen-Header-Zeilen (blau hinterlegt)
 * - Eingerückte Datenzeilen unter jeder Gruppe
 * - Spalten-Header NUR in der ersten Zeile (nicht in jeder Gruppe)
 *
 * WICHTIG: Dies erzeugt das EXAKTE Format aus Image 1!
 *
 * @author Ihr Team
 * @version 3.0 - Hierarchische Darstellung
 */
public class GroupedXlsxWriter {

    private static final Logger logger = LoggerFactory.getLogger(GroupedXlsxWriter.class);
    private static final String GRUPPE_PREFIX = "Gruppe: ";

    /**
     * Schreibt Daten mit hierarchischer Gruppierung in eine XLSX-Datei.
     *
     * Wenn groupByKeys null oder leer: Flache Tabelle ohne Gruppierung
     * Wenn groupByKeys gesetzt: Hierarchische Struktur wie in Image 1
     *
     * @param rows Die zu exportierenden Zeilen
     * @param groupByKeys Liste der Spalten-Keys für Gruppierung (null/leer = keine Gruppierung)
     * @param outputPath Ausgabepfad der XLSX-Datei
     * @throws Exception bei Schreibfehlern
     */
    public void writeGrouped(List<RowData> rows, List<String> groupByKeys, String outputPath) throws Exception {
        if (rows == null || rows.isEmpty()) {
            logger.warn("Keine Daten zum Exportieren vorhanden");
            rows = Collections.emptyList();
        }

        logger.info("Starte hierarchischen XLSX-Export nach: {} (Gruppierung: {})", outputPath,
                groupByKeys != null && !groupByKeys.isEmpty() ? "Ja" : "Nein");

        // Ermittle Header aus der ersten Zeile
        List<String> headers = rows.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(rows.get(0).getValues().keySet());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export");

            // Erstelle Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle groupHeaderStyle = createGroupHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle indentedDataStyle = createIndentedDataStyle(workbook);

            int rowIndex = 0;

            if (groupByKeys == null || groupByKeys.isEmpty()) {
                // Flacher Export ohne Gruppierung
                rowIndex = writeFlatXlsx(sheet, headers, rows, headerStyle, dataStyle);
                logger.info("Flacher XLSX-Export abgeschlossen: {} Zeilen", rows.size());
            } else {
                // HIERARCHISCHER Export wie in Image 1
                rowIndex = writeHierarchicalXlsx(sheet, headers, rows, groupByKeys,
                        headerStyle, groupHeaderStyle, indentedDataStyle);
                logger.info("Hierarchischer XLSX-Export abgeschlossen: {} Gruppen",
                        groupRows(rows, groupByKeys).size());
            }

            // Auto-Größenanpassung aller Spalten
            autoSizeColumns(sheet, headers.size());

            // Freeze erste Zeile (Spalten-Header)
            if (rowIndex > 1) {
                sheet.createFreezePane(0, 1);
            }

            // Schreibe Datei
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }

        logger.info("XLSX-Datei erfolgreich gespeichert: {}", outputPath);
    }

    /**
     * Schreibt flache XLSX ohne Gruppierung.
     */
    private int writeFlatXlsx(Sheet sheet, List<String> headers, List<RowData> rows,
                              CellStyle headerStyle, CellStyle dataStyle) {
        int rowIndex = 0;

        // Schreibe Spalten-Header
        Row headerRow = sheet.createRow(rowIndex++);
        for (int c = 0; c < headers.size(); c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers.get(c));
            cell.setCellStyle(headerStyle);
        }

        // Schreibe Datenzeilen
        for (RowData rowData : rows) {
            Row excelRow = sheet.createRow(rowIndex++);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = excelRow.createCell(c);
                String value = safe(rowData.getValues().get(headers.get(c)));
                cell.setCellValue(value);
                cell.setCellStyle(dataStyle);
            }
        }

        return rowIndex;
    }

    /**
     * Schreibt HIERARCHISCHE XLSX wie in Image 1!
     *
     * Struktur:
     * 1. Zeile: Spalten-Header (grau, fixiert)
     * 2. Zeile: Gruppen-Header (blau, fett, mit Gruppe-Prefix)
     * 3-N: Datenzeilen (eingerückt, gruppiert mit Outline Level 1)
     * [+/-] Button am linken Rand für jede Gruppe
     *
     * Nächste Gruppe...
     */
    private int writeHierarchicalXlsx(Sheet sheet, List<String> headers, List<RowData> rows,
                                      List<String> groupByKeys, CellStyle headerStyle,
                                      CellStyle groupHeaderStyle, CellStyle indentedDataStyle) {
        int rowIndex = 0;

        // 1. GLOBALE Spalten-Header-Zeile (nur einmal ganz oben!)
        Row mainHeaderRow = sheet.createRow(rowIndex++);
        for (int c = 0; c < headers.size(); c++) {
            Cell cell = mainHeaderRow.createCell(c);
            cell.setCellValue(headers.get(c));
            cell.setCellStyle(headerStyle);
        }

        // 2. Gruppiere die Daten
        Map<String, List<RowData>> groups = groupRows(rows, groupByKeys);
        logger.info("XLSX-Export: {} Gruppen erstellt", groups.size());

        // 3. Schreibe jede Gruppe hierarchisch
        for (Map.Entry<String, List<RowData>> entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            List<RowData> groupRows = entry.getValue();

            int groupStartRow = rowIndex;

            // 3a. Gruppen-Header-Zeile (blau hinterlegt, über alle Spalten)
            Row groupHeaderRow = sheet.createRow(rowIndex++);
            Cell groupCell = groupHeaderRow.createCell(0);
            groupCell.setCellValue(GRUPPE_PREFIX + groupKey + " /");
            groupCell.setCellStyle(groupHeaderStyle);

            // Merge Gruppen-Header über alle Spalten
            if (headers.size() > 1) {
                sheet.addMergedRegion(new CellRangeAddress(
                        groupStartRow, groupStartRow, 0, headers.size() - 1));
            }

            // 3b. Datenzeilen der Gruppe (OHNE nochmalige Spalten-Header!)
            int dataStartRow = rowIndex;
            for (RowData rowData : groupRows) {
                Row dataRow = sheet.createRow(rowIndex++);

                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = dataRow.createCell(c);
                    String value = safe(rowData.getValues().get(headers.get(c)));
                    cell.setCellValue(value);
                    cell.setCellStyle(indentedDataStyle);
                }
            }

            // 3c. Excel-Gruppierung erstellen (für +/- Buttons)
            // WICHTIG: groupRow erstellt die Hierarchie UND die +/- Buttons
            if (rowIndex > dataStartRow) {
                // Gruppiere die Datenzeilen (nicht den Gruppen-Header!)
                sheet.groupRow(dataStartRow, rowIndex - 1);

                // Standardmäßig AUFGEKLAPPT (wie in Image 1)
                // false = aufgeklappt, true = eingeklappt
                sheet.setRowGroupCollapsed(dataStartRow, false);
            }

            logger.debug("Gruppe '{}' exportiert: {} Zeilen (Zeilen {}-{})",
                    groupKey, groupRows.size(), groupStartRow + 1, rowIndex);
        }

        return rowIndex;
    }

    /**
     * Gruppiert Zeilen nach den angegebenen Schlüsseln.
     * Verwendet LinkedHashMap um die Einfügereihenfolge beizubehalten.
     */
    private Map<String, List<RowData>> groupRows(List<RowData> rows, List<String> groupByKeys) {
        Map<String, List<RowData>> groups = new LinkedHashMap<>();

        for (RowData row : rows) {
            String groupKey = buildGroupKey(row, groupByKeys);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(row);
        }

        return groups;
    }

    /**
     * Erstellt einen Gruppenschlüssel aus den Werten der angegebenen Keys.
     * Mehrere Keys werden mit " / " verbunden.
     */
    private String buildGroupKey(RowData row, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            String value = row.getValues().get(keys.get(i));
            sb.append(safe(value));
        }
        return sb.toString();
    }

    /**
     * Erstellt Style für globale Spalten-Header (erste Zeile).
     * Grau hinterlegt, mittig, mit Rahmen.
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        // Hintergrundfarbe (hellgrau)
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Rahmen
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        // Ausrichtung
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    /**
     * Erstellt Style für Gruppen-Header-Zeilen.
     * Blau hinterlegt, weiße Schrift, fett, linksbündig.
     */
    private CellStyle createGroupHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        // Hintergrundfarbe (dunkelblau wie in Image 1)
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Ausrichtung
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Rahmen
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);

        return style;
    }

    /**
     * Erstellt Style für normale Daten-Zeilen (flache Tabelle).
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // Dünne Rahmen
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderLeft(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);

        // Ausrichtung
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(false);

        return style;
    }

    /**
     * Erstellt Style für eingerückte Daten-Zeilen (hierarchische Gruppen).
     * Leicht eingerückt, mit dünnen Rahmen.
     */
    private CellStyle createIndentedDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // Dünne Rahmen
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderLeft(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);

        // Ausrichtung
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(false);

        // Leichte Einrückung (für visuell hierarchische Darstellung)
        style.setIndention((short) 1);

        return style;
    }

    /**
     * Passt die Breite aller Spalten automatisch an.
     */
    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int c = 0; c < Math.max(1, columnCount); c++) {
            try {
                sheet.autoSizeColumn(c);
                // Setze maximale Breite um zu große Spalten zu vermeiden
                int currentWidth = sheet.getColumnWidth(c);
                int maxWidth = 15000; // ca. 100 Zeichen
                if (currentWidth > maxWidth) {
                    sheet.setColumnWidth(c, maxWidth);
                }
                // Setze minimale Breite
                int minWidth = 2000; // ca. 13 Zeichen
                if (currentWidth < minWidth) {
                    sheet.setColumnWidth(c, minWidth);
                }
            } catch (Exception e) {
                logger.warn("Konnte Spalte {} nicht auto-sizen: {}", c, e.getMessage());
            }
        }
    }

    /**
     * Gibt einen sicheren String zurück (null wird zu Leerstring).
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }
}