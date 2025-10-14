package service.contract.repository;

import formatter.contract.CoverFormatter;
import model.RowData;
import model.contract.CoverRecord;
import model.contract.CoverStats;
import model.contract.filters.CoverFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.DatabaseService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CoverRepository
 *
 * Kapselt den Datenbankzugriff für COVER-bezogene Abfragen (Listen, Counts, KPIs, Dictionaries).
 */
public class CoverRepository {
    private static final Logger log = LoggerFactory.getLogger(CoverRepository.class);

    private final DatabaseService databaseService;
    private final CoverFormatter coverFormatter;


    public CoverRepository(DatabaseService databaseService,
                           CoverFormatter coverFormatter) {
        this.databaseService = databaseService;
        this.coverFormatter = coverFormatter;
    }

    // =====================================================================================
    // Hauptfunktionen
    // =====================================================================================

    public List<CoverRecord> fetchPage(CoverFilter filter, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.max(1, size);
        int limit = p * s;

        String sql = buildPagedListSql(filter, limit);
        List<RowData> allUpToRequested = executeQuery(sql);

        int fromIdx = (p - 1) * s;
        int toIdx = Math.min(allUpToRequested.size(), fromIdx + s);

        if (fromIdx >= allUpToRequested.size()) {
            return List.of();
        }

        List<CoverRecord> out = new ArrayList<>(Math.max(0, toIdx - fromIdx));
        for (RowData r : allUpToRequested.subList(fromIdx, toIdx)) {
            out.add(coverFormatter.toCoverRecord(r));
        }
        return out;
    }

    public List<RowData> fetchPageRaw(CoverFilter filter, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.max(1, size);
        int limit = (p + 1) * s;

        String sql = buildPagedListSql(filter, limit);
        List<RowData> allUpToRequested = executeQuery(sql);

        int fromIdx = p * s;
        int toIdx = Math.min(allUpToRequested.size(), fromIdx + s);

        if (fromIdx >= allUpToRequested.size()) {
            return List.of();
        }
        return new ArrayList<>(allUpToRequested.subList(fromIdx, toIdx));
    }

    public int fetchCount(CoverFilter filter) {
        String sql = buildCountSql(filter);
        List<RowData> rows = executeQuery(sql);
        if (rows.isEmpty()) return 0;
        String firstVal = rows.get(0).getValues().values().stream().findFirst().orElse("0");
        try {
            return Integer.parseInt(firstVal.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public CoverStats fetchStats(CoverFilter filter) {
        int total = fetchCount(filter);
        String today = LocalDate.now().toString();

        // Active
        String activeSql = buildCountSql(filter) + " AND (COVER.LU_ABL IS NULL OR COVER.LU_ABL >= '" + escape(today) + "')";
        int active = countFromSql(activeSql);
        int ended = Math.max(0, total - active);

        // Cancelled / Storno
        String cancelledSql = buildCountSql(filter) + " AND COVER.LU_STA = 'S'";
        int cancelled = countFromSql(cancelledSql);

        // Kündigungsfristverkürzung
        String shortSql = buildCountSql(filter) + " AND COVER.LU_BASTAND = '6'";
        int shortened = countFromSql(shortSql);

        CoverStats stats = new CoverStats();
        stats.setTotalContracts(total);
        stats.setActiveContracts(active);
        stats.setEndedContracts(ended);
        stats.setCancelledContracts(cancelled);
        stats.setShortenedNoticeContracts(shortened);

        // TODO: ergänzen mit SUM-Abfragen (Prämien, Schäden)
        stats.setAverageDurationYears(0.0);
        stats.setTotalCoverageAmount(0.0);
        stats.setTotalPremiumPaid(0.0);
        stats.setTotalPremiumExpected(0.0);
        stats.setTotalClaimsAmount(0.0);
        stats.setClaimRatio(0.0);

        return stats;
    }

    public Map<String, String> fetchDictionary(String dictName) {
        String upper = (dictName == null) ? "" : dictName.trim().toUpperCase(Locale.ROOT);

        if ("MAP_ALLE_BASTAND".equals(upper)) {
            Map<String, String> bastand = new LinkedHashMap<>();
            bastand.put("0", "Ohne Bearbeitungsstand");
            bastand.put("1", "Angebot");
            bastand.put("2", "Policierung");
            bastand.put("4", "Vertrag erstellt");
            bastand.put("5", "Vertrag beendet");
            bastand.put("6", "Kündigungsfristverkürzung");
            return bastand;
        }

        // NEU: Logik für StornoGrund (MAP_ALLE_AGR) mit Fallback
        if ("MAP_ALLE_AGR".equals(upper)) {
            try {
                // Versuch, die tatsächliche Dictionary-Tabelle zu laden (falls sie repariert wurde)
                String sqlAgr = "SELECT * FROM " + dictName;
                List<RowData> rows = executeQuery(sqlAgr);
                if (rows.isEmpty() || rows.get(0).getValues().size() < 2) {
                    throw new Exception("AGR map table empty or invalid, using fallback.");
                }
                // Verwendung des klassischen Mappings, falls die Tabelle OK ist
                return processDictionaryRows(rows);
            } catch (Exception ex) {
                log.warn("Failed to load dictionary MAP_ALLE_AGR directly. Using fallback on LU_ALLE.", ex);

                // FALLBACK: Werte direkt aus der LU_ALLE Tabelle lesen
                String fallbackSql = "SELECT DISTINCT LU_AGR AS CODE, LU_AGR AS TEXT FROM LU_ALLE WHERE LU_AGR IS NOT NULL AND LU_AGR <> ''";
                List<RowData> fallbackRows = executeQuery(fallbackSql);
                return processFallbackRows(fallbackRows);
            }
        }
        // ENDE NEU

        String sql = buildDictionarySql(dictName);
        List<RowData> rows = executeQuery(sql);

        Map<String, String> out = processDictionaryRows(rows);
        return out;
    }

    // NEU: Hilfsmethoden für die Dictionary-Verarbeitung
    private Map<String, String> processDictionaryRows(List<RowData> rows) {
        Map<String, String> out = new LinkedHashMap<>();
        for (RowData r : rows) {
            Map<String, String> m = r.getValues();
            String key = firstNonBlankCI(m, "TAB_ID", "LU_VMTNR", "CODE");
            if (key == null) key = firstColumn(m);
            String label = firstNonBlankCI(m, "TAB_VALUE", "TEXT", "MTEXT", "LU_NAM", "NAME", "NAM");
            if (label == null) label = secondColumnOr(key, m);
            if (key != null && !key.isBlank()) {
                out.put(key.trim(), label == null ? "" : label.trim());
            }
        }
        return out;
    }

    private Map<String, String> processFallbackRows(List<RowData> rows) {
        Map<String, String> out = new LinkedHashMap<>();
        for (RowData r : rows) {
            String value = r.getValues().values().stream().findFirst().orElse("").trim(); // Sollte CODE sein
            if (!value.isEmpty()) {
                out.put(value, value); // Key = Value, Label = Value
            }
        }
        return out;
    }
    // ENDE NEU

    // =====================================================================================
    // SQL-Build
    // =====================================================================================

    private String buildPagedListSql(CoverFilter filter, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT TOP ").append(limit).append("\n");

        // Block 0 – Versicherungsnehmer
        sql.append("  LUM.LU_NAM AS Versicherungsnehmer_Name,\n");
        sql.append("  LUM.LU_VOR AS Versicherungsnehmer_Vorname,\n");
        sql.append("  LUM.LU_NA2 AS Versicherungsnehmer_Name2,\n");
        sql.append("  LUM.LU_NA3 AS Versicherungsnehmer_Name3,\n");
        sql.append("  LUM.LU_STRASSE AS Versicherungsnehmer_Strasse,\n");
        sql.append("  LUM.LU_PLZ AS Versicherungsnehmer_PLZ,\n");
        sql.append("  LUM.LU_ORT AS Versicherungsnehmer_Ort,\n");
        sql.append("  LUM.LU_NAT AS Versicherungsnehmer_Nation,\n");

        // Block 1 – Basis/Art/Risiko
        sql.append("  COVER.LU_VSN AS Versicherungsschein_Nr,\n");
        sql.append("  COVER.LU_VSN_VR AS VSN_Versicherer,\n");
        sql.append("  COVER.LU_VSN_MAKLER AS VSN_Makler,\n");
        sql.append("  COVER.LU_RIS_ID AS Versicherungsart_Code,\n");
        sql.append("  COVER.LU_RIS AS Versicherungsart_Text,\n");
        sql.append("  COVER.LU_ART AS Vertragsparte_Code,\n");
        sql.append("  COVER.LU_ART_Text AS Vertragsparte_Text,\n");
        sql.append("  COVER.LU_BAUST_RIS AS Baustein_Typ_Code,\n");
        sql.append("  MCR.TAB_VALUE AS Baustein_Typ_Text,\n");

        // Block 2 – Laufzeit/Status
        sql.append("  COVER.LU_NEUVERTRAG_JN AS Beginn_Neuvertrag_JN,\n");
        sql.append("  COVER.LU_BEG AS Beginn_Datum,\n");
        sql.append("  COVER.LU_BEGUHR AS Beginn_Uhrzeit,\n");
        sql.append("  COVER.LU_ABL AS Ablauf_Datum,\n");
        sql.append("  COVER.LU_ABLUHR AS Ablauf_Uhrzeit,\n");
        sql.append("  COVER.LU_HFL AS Hauptfaelligkeit,\n");
        sql.append("  COVER.LU_LFZ AS Laufzeit_Jahre,\n");
        sql.append("  COVER.LU_VERTRAG_ART AS Laufender_Vertrag,\n");
        sql.append("  COVER.LU_A99_JN AS Buchung_nach_Ablaufdatum,\n");
        sql.append("  COVER.LU_VERS_GRUND AS Versionierungsgrund,\n");
        sql.append("  COVER.LU_FLG AS Version_von,\n");
        sql.append("  COVER.LU_FLZ AS Version_bis,\n");
        sql.append("  COVER.LU_STATUS AS Version_Status,\n");

        // Block 3 – Zusatzinfo
        sql.append("  COVER.LU_VUF_KDR AS Fuehrender_Versicherer,\n");
        sql.append("  COVER.LU_FUF_ANTEIL AS Anteil_VR_Prozent,\n");
        sql.append("  COVER.LU_VUF_BEM AS Bemerkung_VR,\n");
        sql.append("  COVER.LU_VUA_KDR AS Assekuradeur,\n");
        sql.append("  COVER.LU_VUA_ANTEIL AS Anteil_Assek_Prozent,\n");
        sql.append("  COVER.LU_VUA_BEM AS Bemerkung_Assek,\n");

        // Block 4 – Vorgang & Partner
        sql.append("  COVER.LU_VORGANG_ID AS Vorgang_ID,\n");
        sql.append("  COVER.LU_GES AS Gesellschaft_Code,\n");
        sql.append("  COVER.LU_GES_Text AS Gesellschaft_Name,\n");
        sql.append("  COVER.LU_VMT AS MaklerNr,\n");
        sql.append("  MAK.LU_NAM AS Makler,\n");
        sql.append("  COVER.LU_VMT2 AS Altmakler,\n");
        sql.append("  COVER.LU_SPAKZ AS Courtagesatz,\n");
        sql.append("  COVER.LU_AGT AS Prov_Vereinbarung,\n");

        // Block 5 – Status/Pool/Fronting/Bearbeitungsstand
        sql.append("  COVER.LU_BET_STAT AS Beteiligungsform_Code,\n");
        sql.append("  MABT.TAB_VALUE AS Beteiligungsform_Text,\n");
        sql.append("  COVER.LU_POOLNR AS Pool_Nr,\n");
        sql.append("  COVER.LU_OPZ AS Vertragsstatus_Code,\n");
        sql.append("  MAO.TAB_VALUE AS Vertragsstatus_Text,\n");
        sql.append("  COVER.LU_STA AS Vertragsstand_Code,\n");
        sql.append("  MASTA.TAB_VALUE AS Vertragsstand_Text,\n");
        sql.append("  COVER.LU_FRONTING_JN AS Fronting,\n");
        sql.append("  COVER.LU_BASTAND AS Bearbeitungsstand,\n");
        sql.append("  COVER.LU_GBEREICH AS OP_Gruppe_Code,\n");
        sql.append("  MAGB.MTEXT AS OP_Gruppe_Text,\n");
        sql.append("  COVER.LU_KUEFRIV_DAT AS Kuendigungsfristverkuerzung_zum,\n");
        sql.append("  COVER.LU_KUEFRIV_DURCH AS Veranlasst_durch,\n");

        // Zuständigkeiten
        sql.append("  COVER.LU_SACHBEA_VT AS SB_Vertr,\n");
        sql.append("  COVER.LU_SACHBEA_SC AS SB_Schad,\n");
        sql.append("  COVER.LU_SACHBEA_RG AS SB_Rechnung,\n");
        sql.append("  COVER.LU_SACHBEA_GL AS GL_Prokurist,\n");
        sql.append("  COVER.LU_SACHBEA_DOK AS SB_Doku,\n");
        sql.append("  COVER.LU_SACHBEA_BUH AS SB_BuHa,\n");

        // Block 6 – Änderung/Storno
        sql.append("  COVER.LU_EDA AS Letzte_Aenderung,\n");
        sql.append("  COVER.LU_EGR AS Grund,\n");
        sql.append("  COVER.LU_SACHBEA_EGR AS Grund_durch,\n");
        sql.append("  COVER.LU_DST AS Storno_zum,\n");
        sql.append("  COVER.LU_AGR AS Storno_Grund,\n");
        sql.append("  COVER.LU_SACHBEA_AGR AS Storno_durch,\n");

        // Block 7 – Wiedervorlagen
        sql.append("  COVER.LU_WVL1 AS Wiedervorlage_Datum_1,\n");
        sql.append("  COVER.LU_WVG1 AS Wiedervorlage_Grund_1,\n");
        sql.append("  COVER.LU_SACHBEA_WVG1 AS Wiedervorlage_durch_1,\n");
        sql.append("  COVER.LU_WVG1_PRIO AS Wiedervorlage_Prio_1,\n");
        sql.append("  COVER.LU_WVL2 AS Wiedervorlage_Datum_2,\n");
        sql.append("  COVER.LU_WVG2 AS Wiedervorlage_Grund_2,\n");
        sql.append("  COVER.LU_SACHBEA_WVG2 AS Wiedervorlage_durch_2,\n");
        sql.append("  COVER.LU_WVG2_PRIO AS Wiedervorlage_Prio_2\n");

        // FROM + JOINs
        sql.append("FROM LU_ALLE AS COVER\n");
        sql.append("LEFT JOIN LU_MASKEP AS LUM ON COVER.PPointer = LUM.PPointer\n");
        sql.append("LEFT JOIN MAKLERV AS MAK ON COVER.LU_VMT = MAK.LU_VMTNR\n");
        sql.append("LEFT JOIN MAP_ALLE_COVERRIS AS MCR ON COVER.LU_BAUST_RIS = MCR.TAB_ID\n");
        sql.append("LEFT JOIN MAP_ALLE_BETSTAT AS MABT ON COVER.LU_BET_STAT = MABT.TAB_ID\n");
        sql.append("LEFT JOIN MAP_ALLE_OPZ AS MAO ON COVER.LU_OPZ = MAO.TAB_ID\n");
        sql.append("LEFT JOIN MAP_ALLE_STA AS MASTA ON COVER.LU_STA = MASTA.TAB_ID\n");
        sql.append("LEFT JOIN MAP_ALLE_GBEREICH AS MAGB ON COVER.LU_GBEREICH = MAGB.TAB_ID\n");

        String where = buildWhere(filter);
        sql.append("WHERE COVER.Sparte LIKE '%COVER' ").append(where).append("\n");

        sql.append("ORDER BY COVER.LU_BEG DESC, COVER.LU_VSN");

        return sql.toString();
    }

    private String buildCountSql(CoverFilter filter) {
        StringBuilder from = new StringBuilder();
        from.append("FROM LU_ALLE AS COVER\n");
        String where = buildWhere(filter);
        if (where.contains("LUM.LU_NAM")) {
            from.append("LEFT JOIN LU_MASKEP AS LUM ON COVER.PPointer = LUM.PPointer\n");
        }
        return "SELECT COUNT(1) AS total\n" + from + "WHERE COVER.Sparte LIKE '%COVER' " + where;
    }


    // =====================================================
// NEUE Version der buildWhere-Methode
// =====================================================
    private String buildWhere(CoverFilter filter) {
        if (filter == null) return "";
        StringBuilder sb = new StringBuilder();

        // 🟢 1. Vertragsstand (LU_STA)
        // Wenn mehrere Statuswerte vorhanden → IN-Klausel
        if (filter.getContractStatusList() != null && !filter.getContractStatusList().isEmpty()) {
            String inClause = filter.getContractStatusList().stream()
                    .map(id -> "'" + escape(id) + "'")
                    .collect(Collectors.joining(","));
            sb.append(" AND COVER.LU_STA IN (").append(inClause).append(")");
        }
        // Wenn kein contractStatusList, aber einzelner Status gesetzt → einfache Gleichung
        else if (nz(filter.getStatus())) {
            sb.append(" AND COVER.LU_STA = '").append(escape(filter.getStatus())).append("'");
        }

        // 🟢 2. Vertragsstatus (LU_OPZ)
        if (nz(filter.getContractStatus())) {
            sb.append(" AND COVER.LU_OPZ = '").append(escape(filter.getContractStatus())).append("'");
        }

        // 🟢 3. Makler
        if (nz(filter.getBroker())) {
            sb.append(" AND COVER.LU_VMT = '").append(escape(filter.getBroker())).append("'");
        }

        // 🟢 4. Textsuche (Name oder VSN)
        if (nz(filter.getTextSearch())) {
            String t = "%" + escapeLike(filter.getTextSearch()) + "%";
            sb.append(" AND (LUM.LU_NAM LIKE '").append(t).append("' OR COVER.LU_VSN LIKE '").append(t).append("')");
        }

        // 🟢 5. Bearbeitungsstand (LU_BASTAND)
        if (filter.getBearbeitungsstandIds() != null && !filter.getBearbeitungsstandIds().isEmpty()) {
            String inClause = filter.getBearbeitungsstandIds().stream()
                    .map(id -> "'" + escape(id) + "'")
                    .collect(Collectors.joining(","));
            sb.append(" AND COVER.LU_BASTAND IN (").append(inClause).append(")");
        }

        // 🟢 6. Stornogründe (LU_AGR)
        // Falls keine Auswahl erfolgt → keine Filterung
        if (filter.getStornoGrundIds() != null && !filter.getStornoGrundIds().isEmpty()) {
            String inClause = filter.getStornoGrundIds().stream()
                    // Die Werte aus dem UI sind bereits LABELS, nicht IDs
                    .map(value -> "'" + escape(value) + "'")
                    .collect(Collectors.joining(","));
            sb.append(" AND COVER.LU_AGR IN (").append(inClause).append(")");
        }

        // 🟢 7. Kündigungsfristverkürzung
        if (filter.getKuendigVerkDatum() != null) {
            sb.append(" AND COVER.LU_KUEFRIV_DAT = '").append(filter.getKuendigVerkDatum()).append("'");
        }
        if (nz(filter.getKuendigVerkInitiator())) {
            sb.append(" AND COVER.LU_KUEFRIV_DURCH = '").append(escape(filter.getKuendigVerkInitiator())).append("'");
        }

        return sb.toString();
    }


    private String buildDictionarySql(String dictName) {
        String table = dictName == null ? "" : dictName.trim();
        if (table.isEmpty()) throw new IllegalArgumentException("Dictionary-Name darf nicht leer sein.");
        String upper = table.toUpperCase(Locale.ROOT);

        if (upper.startsWith("MAP_ALLE_")) {
            return "SELECT * FROM " + table;
        }
        if ("MAKLERV".equals(upper)) {
            return "SELECT LU_VMTNR, LU_NAM FROM MAKLERV";
        }
        return "SELECT * FROM " + table;
    }

    // =====================================================================================
    // DB-Utils
    // =====================================================================================

    @SuppressWarnings("unchecked")
    private List<RowData> executeQuery(String sql) {
        try {
            return databaseService.executeRawQuery(sql);   // ← DOIT appeler la DB
        } catch (Exception ex) {
            log.error("SQL failed.\nSQL:\n{}", sql, ex);
            throw new IllegalStateException("Datenbankabfrage fehlgeschlagen: " + ex.getMessage(), ex);
        }
    }


    private int countFromSql(String sql) {
        return 0;
    }

    private boolean nz(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''").trim();
    }

    private String escapeLike(String s) {
        return escape(s);
    }

    private String firstNonBlankCI(Map<String,String> m, String... names) {
        return null;
    }

    private String firstColumn(Map<String,String> m) {
        return null;
    }

    private String secondColumnOr(String fallback, Map<String,String> m) {
        return null;
    }

    // Suggest VSN
    public List<String> suggestVsnOptimized(String query, int limit) {
        return List.of();
    }

    private List<String> runVsnLike(String likePattern, int limit) {
        return List.of();
    }
}