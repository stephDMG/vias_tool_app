package file.pivot;

import model.RowData;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Transformator für Pivot-Operationen (Vertikal → Horizontal).
 * Diese Klasse bietet statische Methoden zur Umwandlung von Daten aus einem
 * "vertikalen" Format (viele Zeilen pro Gruppierung, mit einer Pivot-Spalte)
 * in ein "horizontales" Format (eine Zeile pro Gruppierung, mit dynamisch
 * erzeugten Spalten aus der Pivot-Spalte). Es basiert auf der Logik zur
 * Datenaggregation und -transformation.
 *
 * @author Stephane Dongmo
 * @version 2.0
 * @since 15/07/2025
 */
public class PivotTransformer {

    /**
     * Transformiert eine Liste von {@link RowData}-Objekten von einem vertikalen
     * zu einem horizontalen (pivotisierten) Format.
     * Daten werden nach der {@code groupByColumn} gruppiert. Für jede Gruppe werden
     * dann neue Spalten basierend auf der {@code pivotColumn} erzeugt (z.B. Dokument1, Dokument2).
     * Angegebene {@code keepColumns} werden in jeder transformierten Zeile beibehalten.
     *
     * @param originalData  Die ursprünglichen Daten in vertikalem Format.
     * @param groupByColumn Der Name der Spalte, nach der die Daten gruppiert werden sollen (z.B. "Schaden Nr").
     * @param pivotColumn   Der Name der Spalte, deren Werte in neue, horizontale Spalten umgewandelt werden sollen (z.B. "Dokument").
     * @param keepColumns   Eine Liste von Spaltennamen, die in der transformierten Zeile beibehalten werden sollen.
     * @return Eine Liste von {@link RowData}-Objekten, die die transformierten Daten im horizontalen Format enthält.
     * Gibt eine leere Liste zurück, wenn {@code originalData} null oder leer ist.
     */
    public static List<RowData> transformToHorizontal(
            List<RowData> originalData,
            String groupByColumn,
            String pivotColumn,
            List<String> keepColumns) {

        if (originalData == null || originalData.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Daten nach der groupByColumn gruppieren
        // LinkedHashMap::new wird verwendet, um die Einfügereihenfolge der Gruppen beizubehalten.
        Map<String, List<RowData>> groupedData = originalData.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getValues().getOrDefault(groupByColumn, ""),
                        LinkedHashMap::new, // Behält die Reihenfolge der Gruppenschlüssel bei
                        Collectors.toList()
                ));

        // 2. Die maximale Anzahl von Einträgen in einer Gruppe bestimmen.
        // Dies bestimmt, wie viele dynamische Pivot-Spalten (z.B. Dokument1, Dokument2, ...) benötigt werden.
        int maxPivotEntries = groupedData.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // 3. Transformation jeder Gruppe durchführen
        List<RowData> transformedData = new ArrayList<>();

        for (Map.Entry<String, List<RowData>> entry : groupedData.entrySet()) {
            String groupKey = entry.getKey(); // Der Wert der groupByColumn für diese Gruppe
            List<RowData> group = entry.getValue(); // Alle Originalzeilen für diese Gruppe

            RowData transformedRow = new RowData(); // Die neue horizontale Zeile

            // Keep-Spalten hinzufügen (Werte aus der ersten Zeile der aktuellen Gruppe übernehmen)
            if (!group.isEmpty()) {
                RowData firstRow = group.get(0);
                for (String keepColumn : keepColumns) {
                    String value = firstRow.getValues().getOrDefault(keepColumn, "");
                    transformedRow.put(keepColumn, value);
                }
            }

            // Die Gruppierungsspalte zur transformierten Zeile hinzufügen
            transformedRow.put(groupByColumn, groupKey);

            // Pivot-Spalten hinzufügen (Dokument1, Dokument2, etc.)
            // Die Schleife läuft bis zur maximalen Anzahl von Pivot-Einträgen,
            // um sicherzustellen, dass alle dynamischen Spalten erzeugt werden,
            // auch wenn eine Gruppe weniger Einträge hat.
            for (int i = 0; i < maxPivotEntries; i++) {
                String pivotColumnName = pivotColumn + (i + 1); // Z.B. "Dokument1", "Dokument2"
                String pivotValue = "";

                if (i < group.size()) {
                    // Wenn ein Eintrag für diesen Index in der Gruppe existiert, dessen Wert verwenden
                    pivotValue = group.get(i).getValues().getOrDefault(pivotColumn, "");
                }

                transformedRow.put(pivotColumnName, pivotValue);
            }

            transformedData.add(transformedRow);
        }

        return transformedData;
    }

    /**
     * Generiert die Header für die transformierten (horizontalen) Daten.
     * Die Reihenfolge der Header ist: zuerst die {@code keepColumns}, dann die {@code groupByColumn},
     * und schließlich die dynamisch erzeugten Pivot-Spalten (z.B. Dokument1, Dokument2, ...).
     *
     * @param originalData  Die ursprünglichen Daten, die zur Bestimmung der maximalen Pivot-Einträge verwendet werden.
     * @param groupByColumn Der Name der Gruppierungsspalte.
     * @param pivotColumn   Der Name der Spalte, die pivotiert wird.
     * @param keepColumns   Eine Liste von Spaltennamen, die beibehalten werden sollen.
     * @return Eine Liste von Strings, die die Header in der korrekten Reihenfolge darstellt.
     * Gibt eine leere Liste zurück, wenn {@code originalData} null oder leer ist.
     */
    public static List<String> generateHorizontalHeaders(
            List<RowData> originalData,
            String groupByColumn,
            String pivotColumn,
            List<String> keepColumns) {

        if (originalData == null || originalData.isEmpty()) {
            return new ArrayList<>();
        }

        // Maximale Anzahl von Pivot-Einträgen bestimmen, um die dynamischen Header zu erzeugen.
        Map<String, List<RowData>> groupedData = originalData.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getValues().getOrDefault(groupByColumn, "")
                ));

        int maxPivotEntries = groupedData.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // Header-Liste erstellen und befüllen
        List<String> headers = new ArrayList<>();

        // 1. Beizubehaltende Spalten (keepColumns) hinzufügen
        headers.addAll(keepColumns);

        // 2. Gruppierungsspalte (groupByColumn) hinzufügen
        headers.add(groupByColumn);

        // 3. Dynamische Pivot-Spalten hinzufügen (z.B. "Dokument1", "Dokument2", ...)
        for (int i = 1; i <= maxPivotEntries; i++) {
            headers.add(pivotColumn + i);
        }

        return headers;
    }

    /**
     * Prüft, ob eine Pivot-Transformation mit den gegebenen Daten und Spaltennamen möglich ist.
     * Die Transformation ist möglich, wenn die Daten nicht leer sind und sowohl die
     * Gruppierungsspalte als auch die Pivot-Spalte in der ersten Zeile der Daten vorhanden sind.
     *
     * @param data          Die zu prüfenden Daten.
     * @param groupByColumn Die Spalte, nach der gruppiert werden soll.
     * @param pivotColumn   Die Spalte, die pivotiert werden soll.
     * @return {@code true}, wenn eine Transformation möglich ist, sonst {@code false}.
     */
    public static boolean canTransform(List<RowData> data, String groupByColumn, String pivotColumn) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        if (groupByColumn == null || pivotColumn == null) {
            return false;
        }

        // Prüfen, ob die benötigten Spalten in der ersten Zeile der Daten existieren
        RowData firstRow = data.get(0);
        return firstRow.getValues().containsKey(groupByColumn) &&
                firstRow.getValues().containsKey(pivotColumn);
    }

    /**
     * Gibt eine Statistik über die Pivot-Transformation zurück.
     * Dies beinhaltet Informationen über die Anzahl der originalen Zeilen, die Anzahl der Gruppen,
     * die maximale und durchschnittliche Anzahl der Einträge pro Gruppe sowie die erwartete
     * Anzahl der resultierenden Zeilen.
     *
     * @param originalData  Die ursprünglichen Daten.
     * @param groupByColumn Die Spalte, nach der gruppiert wird.
     * @return Ein formatierter String mit der Pivot-Statistik.
     * Gibt "Keine Daten vorhanden" zurück, wenn {@code originalData} null oder leer ist.
     */
    public static String getPivotStatistics(List<RowData> originalData, String groupByColumn) {
        if (originalData == null || originalData.isEmpty()) {
            return "Keine Daten vorhanden";
        }

        // Daten nach der Gruppierungsspalte gruppieren, um Statistiken zu erhalten.
        Map<String, List<RowData>> groupedData = originalData.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getValues().getOrDefault(groupByColumn, "Unbekannt")
                ));

        int totalGroups = groupedData.size(); // Gesamtzahl der eindeutigen Gruppen
        int maxEntriesPerGroup = groupedData.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0); // Maximale Anzahl von Einträgen in einer Gruppe
        double avgEntriesPerGroup = groupedData.values().stream()
                .mapToInt(List::size)
                .average()
                .orElse(0.0); // Durchschnittliche Anzahl von Einträgen pro Gruppe

        return String.format(
                "Pivot-Statistik:\n" +
                        "- Originale Zeilen: %d\n" +
                        "- Gruppen nach '%s': %d\n" +
                        "- Max. Einträge pro Gruppe: %d\n" +
                        "- Durchschn. Einträge pro Gruppe: %.1f\n" +
                        "- Resultierende Zeilen: %d",
                originalData.size(),
                groupByColumn,
                totalGroups,
                maxEntriesPerGroup,
                avgEntriesPerGroup,
                totalGroups
        );
    }
}