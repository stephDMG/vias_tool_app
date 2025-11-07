package file.writer;

import model.RowData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gruppierter XLSX-Writer (Kolonnen-Ansicht / Typ 2).
 *
 * - Jede Gruppierungsebene bekommt eine Gruppen-Zeile in der passenden Spalte.
 * - Datenzeilen folgen mit optionaler Einrückung (über Indention) und Excel-Gruppierung (+/-).
 *
 * @author Team
 * @version 3.2 — Fix: Spaltenzuordnung über UI->Data-Key Mapping
 */
public class GroupedXlsxWriter {

    private static final Logger logger = LoggerFactory.getLogger(GroupedXlsxWriter.class);

    // Nur einmal die verfügbaren Keys loggen (sonst sehr viel Output)
    private static final AtomicBoolean LOGGED_KEYS = new AtomicBoolean(false);
    private static final boolean COLLAPSE_GROUPS_BY_DEFAULT = true;

    /** Mapping der UI-Gruppierlabels auf die tatsächlichen Keys in RowData.getValues() */
    private static final Map<String, String> UI_TO_DATA_KEY_MAP;
    static {
        Map<String, String> map = new HashMap<>();
        // Komponierte Keys
        map.put("Gesellschaft", "Gesellschaft_Name");
        map.put("Versicherungsart", "Versicherungsart_Text");
        map.put("Versicherungssparte", "Versicherungssparte_Text");
        map.put("Beteiligungsform", "Beteiligungsform_Text");
        map.put("Cover Art", "Vertragsparte_Text");
        map.put("Sachbearbeiter (Vertrag)", "SB_Vertr");
        map.put("Sachbearbeiter (Schaden)", "SB_Schad");
        map.put("Versicherungsschein Nr", "Versicherungsschein_Nr");
        map.put("Versicherungsnehmer", "Versicherungsnehmer_Name");
        // Identische UI/Data Keys
        map.put("Makler", "Makler");
        UI_TO_DATA_KEY_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Hauptmethode: schreibt XLSX gruppiert oder flach.
     *
     * @param rows         Datenzeilen (RowData.values() = Map<String,String>)
     * @param groupByKeys  UI-Labels der Gruppierung (null/leer -> flach)
     * @param outputPath   Zielpfad
     */
    public void writeGrouped(List<RowData> rows, List<String> groupByKeys, String outputPath) throws Exception {
        if (rows == null || rows.isEmpty()) {
            logger.warn("Keine Daten zum Exportieren vorhanden");
            rows = Collections.emptyList();
        }

        logger.info("Starte hierarchischen XLSX-Export nach: {} (Gruppierung: {})",
                outputPath, (groupByKeys != null && !groupByKeys.isEmpty()) ? "Ja" : "Nein");

        // Header aus der ersten Zeile ableiten (Reihenfolge = Reihenfolge der Map im RowData)
        final List<String> headers = rows.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(rows.get(0).getValues().keySet());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export");
            sheet.setRowSumsBelow(false);   // summary au-dessus
            sheet.setRowSumsRight(false);   // (optionnel) outline à gauche

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle groupStyle = createGroupHeaderStyle(workbook);

            int rowIndex;

            if (groupByKeys == null || groupByKeys.isEmpty()) {
                // Flach
                rowIndex = writeFlatXlsx(sheet, headers, rows, headerStyle, dataStyle);
                logger.info("Flacher XLSX-Export abgeschlossen: {} Zeilen", rows.size());
            } else {
                // Sicherheit: prüfen, ob alle UI-Keys auf existierende header-Keys mappen
                validateGroupByKeys(headers, groupByKeys);

                // Hierarchisch
                rowIndex = writeHierarchicalXlsx(sheet, headers, rows, groupByKeys,
                        headerStyle, groupStyle, dataStyle);
                logger.info("Hierarchischer XLSX-Export abgeschlossen");
            }

            // AutoSize + Freeze
            autoSizeColumns(sheet, headers.size());
            if (rowIndex > 1) {
                sheet.createFreezePane(0, 1);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }

        logger.info("XLSX-Datei erfolgreich gespeichert: {}", outputPath);
    }

    /* --------------------- Flat --------------------- */

    private int writeFlatXlsx(Sheet sheet, List<String> headers, List<RowData> rows,
                              CellStyle headerStyle, CellStyle dataStyle) {
        int rowIndex = 0;

        // Spalten-Header
        Row headerRow = sheet.createRow(rowIndex++);
        for (int c = 0; c < headers.size(); c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers.get(c));
            cell.setCellStyle(headerStyle);
        }

        // Daten
        for (RowData rowData : rows) {
            Row excelRow = sheet.createRow(rowIndex++);
            Map<String, String> vals = rowData.getValues();

            if (LOGGED_KEYS.compareAndSet(false, true)) {
                logger.warn("VERFÜGBARE RowData Keys: {}", vals.keySet());
            }

            for (int c = 0; c < headers.size(); c++) {
                Cell cell = excelRow.createCell(c);
                String value = safe(vals.get(headers.get(c)));
                cell.setCellValue(value);
                cell.setCellStyle(dataStyle);
            }
        }

        return rowIndex;
    }

    /* --------------------- Hierarchical (Typ 2) --------------------- */

    private int writeHierarchicalXlsx(Sheet sheet,
                                      List<String> headers,
                                      List<RowData> rows,
                                      List<String> groupByKeys,
                                      CellStyle headerStyle,
                                      CellStyle groupStyle,
                                      CellStyle dataStyle) {

        int rowIndex = 0;

        // Globale Spalten-Header-Zeile
        Row mainHeaderRow = sheet.createRow(rowIndex++);
        for (int c = 0; c < headers.size(); c++) {
            Cell cell = mainHeaderRow.createCell(c);
            cell.setCellValue(headers.get(c));
            cell.setCellStyle(headerStyle);
        }

        List<String> lastPathSegments = new ArrayList<>();
        Map<String, Integer> groupStartRows = new LinkedHashMap<>(); // PfadKey -> Startzeile

        for (RowData rowData : rows) {
            Map<String, String> values = rowData.getValues();
            if (LOGGED_KEYS.compareAndSet(false, true)) {
                logger.warn("VERFÜGBARE RowData Keys: {}", values.keySet());
            }

            List<String> currentPath = buildPathFromKeys(rowData, groupByKeys);

            // Level finden, ab dem sich der Pfad ändert
            int changeLevel = findChangeLevel(lastPathSegments, currentPath);

            // Vorherige Gruppen schließen
            if (changeLevel < lastPathSegments.size()) {
                closeGroupsFromLevel(sheet, groupStartRows, lastPathSegments, changeLevel, rowIndex - 1);
            }

            // Neue Gruppenzeilen für die geänderten Ebenen schreiben
            for (int level = changeLevel; level < currentPath.size(); level++) {
                String segmentValue = currentPath.get(level);
                String uiKey = groupByKeys.get(level);
                String dataKey = UI_TO_DATA_KEY_MAP.getOrDefault(uiKey, uiKey);

                int columnIndex = headers.indexOf(dataKey); // <<— FIX: nutze dataKey
                Row groupRow = sheet.createRow(rowIndex);

                if (columnIndex != -1) {
                    Cell groupCell = groupRow.createCell(columnIndex);

                    CellStyle levelStyle = sheet.getWorkbook().createCellStyle();
                    levelStyle.cloneStyleFrom(groupStyle);
                    levelStyle.setIndention((short) level);

                    groupCell.setCellValue(segmentValue);
                    groupCell.setCellStyle(levelStyle);

                    // Optionale Ästhetik: andere Zellen "leeren"
                    for (int c = 0; c < headers.size(); c++) {
                        if (c != columnIndex) {
                            Cell emptyCell = groupRow.createCell(c);
                            emptyCell.setCellValue("");
                            emptyCell.setCellStyle(dataStyle);
                        }
                    }
                } else {
                    // Fallback: wenn die Spalte fehlt, schreibe in Spalte 0 mit Label
                    Cell fallback = groupRow.createCell(0);
                    CellStyle levelStyle = sheet.getWorkbook().createCellStyle();
                    levelStyle.cloneStyleFrom(groupStyle);
                    levelStyle.setIndention((short) level);
                    fallback.setCellValue(uiKey + ": " + segmentValue);
                    fallback.setCellStyle(levelStyle);
                }

                String pathKey = String.join("\u001F", currentPath.subList(0, level + 1));
                groupStartRows.put(pathKey, rowIndex);
                rowIndex++;
            }

            // Datenzeile
            Row dataRow = sheet.createRow(rowIndex);
            int dataLevel = currentPath.size();

            for (int c = 0; c < headers.size(); c++) {
                Cell cell = dataRow.createCell(c);
                String value = safe(values.get(headers.get(c)));
                cell.setCellValue(value);

                if (c == 0 && dataLevel > 0) {
                    CellStyle indented = sheet.getWorkbook().createCellStyle();
                    indented.cloneStyleFrom(dataStyle);
                    indented.setIndention((short) dataLevel);
                    cell.setCellStyle(indented);
                } else {
                    cell.setCellStyle(dataStyle);
                }
            }

            rowIndex++;
            lastPathSegments = currentPath;
        }

        // Letzte offenen Gruppen schließen
        if (!lastPathSegments.isEmpty()) {
            closeGroupsFromLevel(sheet, groupStartRows, lastPathSegments, 0, rowIndex - 1);
        }

        return rowIndex;
    }

    /** Mappt UI-Labels auf Werte aus RowData und baut den Pfad (ein Segment pro Ebene). */
    private List<String> buildPathFromKeys(RowData row, List<String> keys) {
        List<String> path = new ArrayList<>();
        Map<String, String> rowValues = row.getValues();

        if (rowValues == null || rowValues.isEmpty()) {
            logger.error("RowData ist leer. Kann keine Pfade erstellen.");
            return path;
        }

        for (String uiKey : keys) {
            String dataKey = UI_TO_DATA_KEY_MAP.getOrDefault(uiKey, uiKey);
            String value = rowValues.getOrDefault(dataKey, "");
            if (value.isEmpty() && !rowValues.containsKey(dataKey)) {
                logger.warn("Mapping-FEHLER: UI-Schlüssel '{}' -> Data-Key '{}' existiert nicht in RowData.", uiKey, dataKey);
            }
            path.add(safe(value));
        }

        return path;
    }

    /** Erste Ebene ab der sich Pfade unterscheiden. */
    private int findChangeLevel(List<String> oldPath, List<String> newPath) {
        int min = Math.min(oldPath.size(), newPath.size());
        for (int i = 0; i < min; i++) {
            if (!Objects.equals(oldPath.get(i), newPath.get(i))) return i;
        }
        return min;
    }

    /** Schließt Excel-Gruppen ab fromLevel (inkl.) bis Ende. */
    private void closeGroupsFromLevel(Sheet sheet,
                                      Map<String, Integer> groupStartRows,
                                      List<String> path,
                                      int fromLevel,
                                      int endRow) {

        for (int level = path.size() - 1; level >= fromLevel; level--) {
            String pathKey = String.join("\u001F", path.subList(0, level + 1));
            Integer startRow = groupStartRows.get(pathKey);
            if (startRow == null) continue;

            // on groupe uniquement les lignes de détail, pas l’entête (startRow+1 … endRow)
            int firstDetail = startRow + 1;
            if (firstDetail <= endRow) {
                try {
                    sheet.groupRow(firstDetail, endRow);
                    // force l’apparition du bouton au dernier groupe aussi
                    sheet.setRowGroupCollapsed(firstDetail, COLLAPSE_GROUPS_BY_DEFAULT);
                } catch (Exception e) {
                    logger.warn("Konnte Gruppe nicht erstellen: Level={}, Start={}, Ende={}",
                            level, firstDetail, endRow, e);
                }
            } else {
                // aucun détail pour ce header — rien à regrouper
                logger.debug("Übersprungen: keine Details für Gruppe ab Zeile {}", startRow);
            }
            groupStartRows.remove(pathKey);
        }
    }
    /* --------------------- Styles / Utils --------------------- */

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createGroupHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderLeft(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(false);
        return style;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int c = 0; c < Math.max(1, columnCount); c++) {
            try {
                sheet.autoSizeColumn(c);
                int currentWidth = sheet.getColumnWidth(c);
                int maxWidth = 15000; // ~100 Zeichen
                if (currentWidth > maxWidth) sheet.setColumnWidth(c, maxWidth);
                int minWidth = 2000; // ~13 Zeichen
                if (currentWidth < minWidth) sheet.setColumnWidth(c, minWidth);
            } catch (Exception e) {
                logger.warn("Konnte Spalte {} nicht auto-sizen: {}", c, e.getMessage());
            }
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    /** Wirft eine klare Exception, wenn UI-Keys nicht auf existierende Header mappen. */
    private void validateGroupByKeys(List<String> headers, List<String> groupByKeys) {
        List<String> missing = new ArrayList<>();
        for (String uiKey : groupByKeys) {
            String dataKey = UI_TO_DATA_KEY_MAP.getOrDefault(uiKey, uiKey);
            if (!headers.contains(dataKey)) {
                missing.add(uiKey + " -> " + dataKey);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Gruppierung enthält nicht vorhandene Spalten (UI->Data): " + missing);
        }
    }
}
