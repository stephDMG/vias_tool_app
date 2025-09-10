package service.op.kunde;

import model.RowData;
import service.interfaces.DatabaseService;
import service.op.Hartrodt;

import java.util.*;
import java.util.stream.Collectors;

public class KundeRepository {
    private final DatabaseService databaseService;

    public KundeRepository (DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
    }

    private static String safeGet(RowData row, String key) {
        if (row == null || row.getValues() == null) return "";
        String v = row.getValues().get(key);
        return v == null ? "" : v.trim();
    }


    public Map<String, Map<String, List<Kunde>>> getGroupedByLandAndPolicy(String likeName) throws Exception {
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
                      AND LUM.LU_NAM LIKE '%likeName%'
                    ORDER BY V05.LU_LANDNAME ASC, LAL.LU_VSN ASC
                """;

        List<RowData> rawData = databaseService.executeRawQuery(sql);
        if (rawData == null || rawData.isEmpty()) {
            return Collections.emptyMap();
        }


        List<Kunde> kundeList = new ArrayList<>();
        for (RowData row : rawData) {
            String policeNr = safeGet(row, "Police Nr.");
            if (policeNr.isEmpty()) continue;

            String name = safeGet(row, "Firma/Name");
            String land = safeGet(row, "Land");
            String ort = safeGet(row, "Ort");

            kundeList.add(new Kunde(likeName, policeNr, name, land, ort));
        }

        // Gruppierung Land -> PoliceNr -> Liste
        return kundeList.stream()
                .collect(Collectors.groupingBy(
                        Kunde::getLand,
                        LinkedHashMap::new, // Reihenfolge nach Land
                        Collectors.groupingBy(
                                Kunde::getPoliceNr,
                                LinkedHashMap::new, // Reihenfolge nach PoliceNr
                                Collectors.toList()
                        )
                ));
    }
}

