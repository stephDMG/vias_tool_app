package service.op;

import model.RowData;
import service.interfaces.DatabaseService;

import java.util.*;
import java.util.stream.Collectors;

public class HartrodtRepository {

    private final DatabaseService databaseService;

    public HartrodtRepository(DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
    }

    /**
     * Lädt alle Hartrodt-Policen und gruppiert:
     *   Land -> Policen-Nr -> Liste von Hartrodt-Einträgen (z. B. verschiedene Orte).
     */
    public Map<String, Map<String, List<Hartrodt>>> getGroupedByLandAndPolicy() throws Exception {
        final String sql = """
            SELECT
                LAL.LU_VSN        AS "Police Nr.",
                LUM.LU_NAM        AS "Firma/Name",
                V05.LU_LANDNAME   AS "Land",
                LUM.LU_ORT        AS "Ort"
            FROM LU_ALLE AS LAL
            INNER JOIN LU_MASKEP AS LUM ON LAL.PPointer = LUM.PPointer
            INNER JOIN VIASS005  AS V05 ON LUM.LU_NAT   = V05.LU_INTKZ
            WHERE LAL.Sparte LIKE '%COVER'
              AND LAL.LU_STA = 'A'
              AND LAL.LU_SACHBEA_RG = 'CBE'
              AND LUM.LU_NAM LIKE '%hartrodt%'
            ORDER BY V05.LU_LANDNAME ASC, LAL.LU_VSN ASC
        """;

        List<RowData> rawData = databaseService.executeRawQuery(sql);
        if (rawData == null || rawData.isEmpty()) {
            return Collections.emptyMap();
        }

        // RowData → Hartrodt Entities
        List<Hartrodt> hartrodtList = new ArrayList<>();
        for (RowData row : rawData) {
            String policeNr = safeGet(row, "Police Nr.");
            if (policeNr.isEmpty()) continue; // ohne Police-Nr überspringen

            String name = safeGet(row, "Firma/Name");
            String land = safeGet(row, "Land");
            String ort  = safeGet(row, "Ort");

            hartrodtList.add(new Hartrodt(policeNr, name, land, ort));
        }

        // Gruppierung Land -> PoliceNr -> Liste
        return hartrodtList.stream()
                .collect(Collectors.groupingBy(
                        Hartrodt::getLand,
                        LinkedHashMap::new, // Reihenfolge nach Land
                        Collectors.groupingBy(
                                Hartrodt::getPoliceNr,
                                LinkedHashMap::new, // Reihenfolge nach PoliceNr
                                Collectors.toList()
                        )
                ));
    }

    private static String safeGet(RowData row, String key) {
        if (row == null || row.getValues() == null) return "";
        String v = row.getValues().get(key);
        return v == null ? "" : v.trim();
    }
}
