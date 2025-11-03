package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class GroupingUtil {

    private static final Map<String, String> GROUPBY_ALIAS_MAP = Map.of(
            "Cover Art", "Makler", "Gesellschaft", "Versicherungsart",
            "Versicherungssparte", "Beteiligungsform",
            "Sachbearbeiter (Vertrag)", "Sachbearbeiter (Schaden)",
            "Versicherungsschein Nr","Versicherungsnehmer"
    );


    public static Map<List<String>, List<RowData>> groupRows(List<RowData> rows, List<String> groupByLabels) {
        if (rows == null || rows.isEmpty() || groupByLabels == null || groupByLabels.isEmpty()) {
            return Map.of(List.of("Alle"), rows == null ? List.of() : rows);
        }

        return rows.stream().collect(Collectors.groupingBy(r -> {
            List<String> key = new ArrayList<>();
            for (String label : groupByLabels) {
                String col = GROUPBY_ALIAS_MAP.getOrDefault(label, label);
                key.add(r.getValues().getOrDefault(col, "-"));
            }
            return key; // 👉 clé = vraie liste de colonnes, pas un string concaténé
        }, LinkedHashMap::new, Collectors.toList()));
    }
    /**
    public static List<RowData> groupRows(List<RowData> rows, List<String> groupKeys) {
        if (rows == null || rows.isEmpty() || groupKeys == null || groupKeys.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        // clé composite robuste (jamais null)
        return rows.stream().collect(Collectors.groupingBy(r -> {
                    Map<String, String> v = r.getValues();
                    Map<String, Object> pk = new LinkedHashMap<>();
                    for (String k : groupKeys) {
                        String val = (v == null) ? null : v.get(k);
                        pk.put(k, (val == null || val.isBlank()) ? "—" : val);
                    }
                    return Map.copyOf(pk); // clé immuable et non nulle
                }))
                .values().stream()
                .flatMap(List::stream)
                .toList();
    }
     */

}
