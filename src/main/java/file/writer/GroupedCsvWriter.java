package file.writer;

import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Hierarchischer CSV-Writer - erstellt gruppierte CSV-Dateien.
 *
 * Erzeugt CSV-Dateien mit:
 * - Globalen Spalten-Header (nur einmal ganz oben)
 * - Gruppen-Header-Zeilen vor jeder Gruppe
 * - Datenzeilen unter jeder Gruppe
 * - Leerzeilen zwischen Gruppen für bessere Lesbarkeit
 *
 * Format:
 * Spalte1,Spalte2,Spalte3,...
 * # Gruppe: Wert1 / Wert2
 * Daten1,Daten2,Daten3,...
 * Daten1,Daten2,Daten3,...
 *
 * # Gruppe: Wert3 / Wert4
 * Daten1,Daten2,Daten3,...
 *
 * @author Ihr Team
 * @version 3.0 - Hierarchische Darstellung
 */
public class GroupedCsvWriter {

    private static final Logger logger = LoggerFactory.getLogger(GroupedCsvWriter.class);
    private static final String GRUPPE_PREFIX = "# Gruppe: ";

    /**
     * Schreibt Daten mit hierarchischer Gruppierung in eine CSV-Datei.
     *
     * Wenn groupByKeys null oder leer: Flache CSV ohne Gruppierung
     * Wenn groupByKeys gesetzt: Hierarchische CSV mit Gruppen-Headern
     *
     * @param rows Die zu exportierenden Zeilen
     * @param groupByKeys Liste der Spalten-Keys für Gruppierung (null/leer = keine Gruppierung)
     * @param outputPath Ausgabepfad der CSV-Datei
     * @throws Exception bei Schreibfehlern
     */
    public void writeGrouped(List<RowData> rows, List<String> groupByKeys, String outputPath) throws Exception {
        if (rows == null || rows.isEmpty()) {
            logger.warn("Keine Daten zum Exportieren vorhanden");
            rows = Collections.emptyList();
        }

        logger.info("Starte hierarchischen CSV-Export nach: {} (Gruppierung: {})", outputPath,
                groupByKeys != null && !groupByKeys.isEmpty() ? "Ja" : "Nein");

        // Ermittle Header aus der ersten Zeile
        List<String> headers = rows.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(rows.get(0).getValues().keySet());

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(outputPath, StandardCharsets.UTF_8))) {

            if (groupByKeys == null || groupByKeys.isEmpty()) {
                // Flacher Export ohne Gruppierung
                writeFlatCsv(writer, headers, rows);
                logger.info("Flacher CSV-Export abgeschlossen: {} Zeilen", rows.size());
            } else {
                // HIERARCHISCHER Export
                writeHierarchicalCsv(writer, headers, rows, groupByKeys);
                logger.info("Hierarchischer CSV-Export abgeschlossen: {} Gruppen",
                        groupRows(rows, groupByKeys).size());
            }
        }
    }

    /**
     * Schreibt flache CSV ohne Gruppierung.
     */
    private void writeFlatCsv(BufferedWriter writer, List<String> headers, List<RowData> rows)
            throws Exception {
        // Schreibe Header-Zeile
        writeLine(writer, headers);

        // Schreibe Datenzeilen
        for (RowData row : rows) {
            List<String> values = extractValues(row, headers);
            writeLine(writer, values);
        }
    }

    /**
     * Schreibt HIERARCHISCHE CSV ähnlich wie Excel-Format.
     *
     * Format:
     * Header-Zeile (nur einmal)
     * # Gruppe: Name
     * Datenzeile 1
     * Datenzeile 2
     * [Leerzeile]
     * # Gruppe: Name2
     * Datenzeile 3
     * ...
     */
    private void writeHierarchicalCsv(BufferedWriter writer, List<String> headers,
                                      List<RowData> rows, List<String> groupByKeys) throws Exception {
        // 1. GLOBALE Header-Zeile (nur einmal ganz oben!)
        writeLine(writer, headers);

        // 2. Gruppiere die Zeilen
        Map<String, List<RowData>> groups = groupRows(rows, groupByKeys);
        logger.info("CSV-Export: {} Gruppen erstellt", groups.size());

        // 3. Schreibe jede Gruppe
        int groupIndex = 0;
        for (Map.Entry<String, List<RowData>> entry : groups.entrySet()) {
            groupIndex++;
            String groupKey = entry.getKey();
            List<RowData> groupRows = entry.getValue();

            // 3a. Gruppen-Header-Zeile (als Kommentar mit #)
            writer.write(GRUPPE_PREFIX + groupKey + " /");
            writer.newLine();

            // 3b. Datenzeilen der Gruppe (OHNE nochmalige Header!)
            for (RowData row : groupRows) {
                List<String> values = extractValues(row, headers);
                writeLine(writer, values);
            }

            // 3c. Leerzeile zwischen Gruppen (außer nach der letzten Gruppe)
            if (groupIndex < groups.size()) {
                writer.newLine();
            }

            logger.debug("Gruppe {}/{} '{}' exportiert: {} Zeilen",
                    groupIndex, groups.size(), groupKey, groupRows.size());
        }
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
     * Extrahiert Werte aus einer Zeile in der Reihenfolge der Headers.
     */
    private List<String> extractValues(RowData row, List<String> headers) {
        List<String> values = new ArrayList<>(headers.size());
        for (String header : headers) {
            String value = row.getValues().get(header);
            values.add(safe(value));
        }
        return values;
    }

    /**
     * Schreibt eine Zeile mit korrektem CSV-Escaping.
     */
    private void writeLine(BufferedWriter writer, List<String> columns) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (String column : columns) {
            if (!first) {
                sb.append(',');
            }
            sb.append(escapeCsv(column));
            first = false;
        }

        writer.write(sb.toString());
        writer.newLine();
    }

    /**
     * Gibt einen sicheren String zurück (null wird zu Leerstring).
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Escaped einen String für CSV (Quotes verdoppeln, bei Bedarf in Quotes einschließen).
     */
    private String escapeCsv(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }

        // Ersetze alle Quotes durch doppelte Quotes
        String escaped = s.replace("\"", "\"\"");

        // Wenn der String Kommas, Quotes, Zeilenumbrüche enthält, in Quotes einschließen
        if (escaped.contains(",") || escaped.contains("\"") ||
                escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }

        return escaped;
    }
}