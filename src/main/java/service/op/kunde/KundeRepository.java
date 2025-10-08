package service.op.kunde;

import model.RowData;
import service.interfaces.DatabaseService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generisches Repository für die Abfrage von Kundendaten aus der Datenbank.
 * <p>
 * Führt die SQL-Abfrage aus und liefert strukturierte {@link Kunde}-Objekte,
 * gruppiert nach Land und Policen-Nr. Keine kunden-spezifische Logik hier.
 * </p>
 */
public class KundeRepository {

    private final DatabaseService databaseService;

    public KundeRepository(DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
    }

    private static String safeGet(RowData row, String key) {
        if (row == null || row.getValues() == null) return "";
        String v = row.getValues().get(key);
        return v == null ? "" : v.trim();
    }

    /**
     * Führt die Abfrage für einen bestimmten Kunden aus und gruppiert die Ergebnisse.
     *
     * @param likeName Suchmuster (z. B. "Hartrodt" → wird als '%Hartrodt%' verwendet)
     */
    public Map<String, Map<String, List<Kunde>>> getGroupedByLandAndPolicy(String likeName) throws Exception {
        final String sql = """
                SELECT
                    LAL.LU_VSN      AS "Police Nr.",
                    LUM.LU_NAM      AS "Firma/Name",
                    V05.LU_LANDNAME AS "Land",
                    LUM.LU_ORT      AS "Ort"
                FROM LU_ALLE LAL
                INNER JOIN LU_MASKEP LUM ON LAL.PPointer = LUM.PPointer
                INNER JOIN VIASS005 V05   ON LUM.LU_NAT  = V05.LU_INTKZ
                WHERE LAL.Sparte LIKE '%COVER'
                  AND LAL.LU_STA = 'A'
                  AND LAL.LU_SACHBEA_RG = 'CBE'
                  AND UPPER(LUM.LU_NAM) LIKE ?
                ORDER BY V05.LU_LANDNAME ASC, LAL.LU_VSN ASC
                """;

        String pattern = "%" + (likeName == null ? "" : likeName.trim().toUpperCase()) + "%";

        List<RowData> rawData = databaseService.executeRawQuery(sql, pattern);
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

        return kundeList.stream().collect(Collectors.groupingBy(
                Kunde::getLand, LinkedHashMap::new,
                Collectors.groupingBy(Kunde::getPoliceNr, LinkedHashMap::new, Collectors.toList())
        ));
    }
}
