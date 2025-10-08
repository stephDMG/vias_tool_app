package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class GroupingUtil {

    private static final Map<String, String> GROUPBY_ALIAS_MAP = Map.of(
            "Cover Art", "Vertragsparte_Text", // alias réel
            "Makler", "Makler",
            "Gesellschaft", "Gesellschaft_Name",
            "Versicherungsart", "Versicherungsart_Text",
            "Versicherungssparte", "Vertragsparte_Text", // en fait c’est LU_ART_Text
            "Beteiligungsform", "Beteiligungsform_Text",
            "Sachbearbeiter (Vertrag)", "SB_Vertr",
            "Sachbearbeiter (Schaden)", "SB_Schad"
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

}
