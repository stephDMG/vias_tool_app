package service.impl;

import config.DatabaseConfig;
import model.RowData;
import model.enums.ExportFormat;
import model.enums.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.DatabaseService;
import service.interfaces.FileService;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Standard-Implementierung des {@link service.interfaces.DatabaseService}.
 * Kapselt JDBC-Zugriffe (Query-Ausführung, Export, Dashboard-Kennzahlen) und
 * nutzt {@link service.interfaces.FileService} für Dateiexporte.
 */
public class DatabaseServiceImpl implements DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseServiceImpl.class);
    private final FileService fileService;

    public DatabaseServiceImpl(FileService fileService) {
        this.fileService = fileService;
    }

    // --- Bestehende Methoden (unverändert) ---
    @Override
    public void executeQuery(String sql, Consumer<RowData> processor) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            while (rs.next()) {
                RowData row = new RowData();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getString(i));
                }
                processor.accept(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("Fehler bei SQL-Abfrage: " + sql, e);
        }
    }

    @Override
    public void exportToFile(String sql, String outputPath, ExportFormat format) {
        List<RowData> data = new ArrayList<>();
        executeQuery(sql, data::add);
        fileService.writeFile(data, outputPath, format);
    }

    // --- Methoden für vordefinierte Abfragen ---
    @Override
    public List<RowData> executeQuery(QueryRepository query, List<String> parameters) throws Exception {
        // Verwendet jetzt die neue Hilfsmethode
        return executeRawQueryWithParameters(query.getSql(), parameters);
    }

    @Override
    public void exportToFile(QueryRepository query, List<String> parameters, String outputPath, ExportFormat format) throws Exception {
        List<RowData> fullResults = executeQuery(query, parameters);
        fileService.writeFile(fullResults, outputPath, format);
    }


    // --- NEUE Implementierungen für den KI-Assistenten ---
    @Override
    public List<RowData> executeRawQuery(String sql) throws Exception {
        // Führt eine Abfrage ohne Parameter aus
        return executeRawQueryWithParameters(sql, new ArrayList<>());
    }

    @Override
    public void exportRawQueryToFile(String sql, String outputPath, ExportFormat format) throws Exception {
        List<RowData> fullResults = executeRawQuery(sql);
        fileService.writeFile(fullResults, outputPath, format);
    }

    // --- NEUE Implementierung für das Dashboard ---
    @Override
    public Map<String, Integer> getDashboardStatistics() throws Exception {
        Map<String, Integer> stats = new LinkedHashMap<>(); // LinkedHashMap, um die Reihenfolge beizubehalten

        // Annahme: 'G' steht für einen gültigen/aktiven Status in Ihrer Datenbank. Passen Sie dies bei Bedarf an.
        String activeCoversSql = "SELECT COUNT(*) FROM LU_ALLE WHERE Sparte LIKE '%COVER' AND LU_STA = 'A' AND  LU_FLZ = '' ";
        stats.put("Aktive Verträge", executeCountQuery(activeCoversSql));

        // Annahme: Ein Status ungleich 'E' (Erledigt) bedeutet, dass ein Schaden offen ist.
        String openDamagesSql = "SELECT COUNT(*) FROM LU_SVA WHERE Sparte = 'SVA' AND LU_SVSTATUS = 'O'";
        stats.put("Offene Schäden", executeCountQuery(openDamagesSql));

        String totalBrokersSql = "SELECT COUNT(LU_VMT) FROM VERMITTLER";
        stats.put("Anzahl Makler", executeCountQuery(totalBrokersSql));

        return stats;
    }


    @Override
    public Map<String, RowData> getSchadenDetailsByMaklerSnrBulk(List<String> snrMaklerList) throws Exception {
        if (snrMaklerList == null || snrMaklerList.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(",", Collections.nCopies(snrMaklerList.size(), "?"));
        String baseSql = QueryRepository.SCHADEN_DETAILS_BY_MAKLER_SNR.getSql();
        String sql = String.format(baseSql, placeholders);

        Map<String, RowData> resultsMap = new HashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < snrMaklerList.size(); i++) {
                stmt.setString(i + 1, snrMaklerList.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    RowData row = new RowData();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getString(i) != null ? rs.getString(i).trim() : "");
                    }

                    String key = rs.getString(1);
                    if (key != null) {
                        resultsMap.put(key.trim(), row);
                    }
                }
            }
        }
        return resultsMap;
    }

    @Override
    public List<RowData> executeHartrodtQuery() throws Exception {
        String sql = """
                    SELECT
                        LAL.LU_VSN AS "Police Nr.",
                        LUM.LU_NAT  AS "Land Code",
                        LUM.LU_NAM AS "Firma/Name",
                        V05.LU_LANDNAME AS "Land",
                        LUM.LU_ORT AS "Ort"
                    FROM
                        LU_ALLE AS LAL
                        INNER JOIN LU_MASKEP AS LUM ON LAL.PPointer = LUM.PPointer
                        INNER JOIN VIASS005 AS V05 ON LUM.LU_NAT = V05.LU_INTKZ
                    WHERE LAL.Sparte LIKE '%COVER'
                    AND LAL.LU_SACHBEA_RG = 'CBE'
                    AND LUM.LU_NAM LIKE '%hartrodt%'
                    ORDER BY V05.LU_LANDNAME ASC
                """;
        return executeRawQuery(sql);
    }

    @Override
    public List<RowData> executeOpListeQuery(String policyNr) throws Exception {
        // Wir fügen hier die WHERE-Klausel für die Policennummer hinzu
        String sql = """
                    SELECT
                        A.LU_VMT, A.LU_RNR, A.LU_RNR_Makler, A.LU_RNR_R, A.LU_VSN, LA.LU_VSN_Makler, A.LU_ZJ,
                        LMP.LU_NAM, A.LU_RDT, A.LU_BDT, A.LU_FLG, A.LU_Waehrung, A.LU_VSTLD, A.LU_SD_WART,
                        MAX(A.LU_NET_100) AS LU_NET_100, AVG(A.LU_VST) AS LU_VST, SUM(A.LU_VSTBetrag) AS LU_VSTBetrag,
                        SUM(A.LU_Praemie) AS LU_Praemie, SUM(A.LU_OBT) AS LU_OBT, MAX(A.LU_SPAKZ) AS LU_SPAKZ,
                        SUM(A.LU_NET) AS LU_NET, SUM(A.LU_WProvision) AS LU_WProvision, SUM(A.LU_Restbetrag) AS LU_Restbetrag,
                        A.LU_INK, MAX(A.LU_MA1) AS LU_MA1, MAX(A.LU_MA2) AS LU_MA2, MAX(A.LU_MAHN_Bemerkung) AS LU_MAHN_Bemerkung,
                        MAX(LA.LU_STATISTIK_CODE1) AS STAT_CODE1, MAX(LA.LU_STATISTIK_CODE2) AS STAT_CODE2,
                        MAX(LA.LU_STATISTIK_CODE3) AS STAT_CODE3, MAX(LA.LU_STATISTIK_CODE4) AS STAT_CODE4,
                        MAX(LA.LU_STATISTIK_CODE5) AS STAT_CODE5, MAX(LA.LU_STATISTIK_CODE6) AS STAT_CODE6, A.LU_ABW
                    FROM ABRECHNUNG AS A
                    INNER JOIN LU_ALLE AS LA ON A.VPointer = LA.VPointer
                    INNER JOIN LU_MASKEP AS LMP ON A.PPointer = LMP.PPointer
                    WHERE A.LU_TES IN ('SO','SOT','GR')
                    AND A.LU_Restbetrag <> '0'
                    AND A.LU_VSN = ?  -- Filter für die Policennummer
                    GROUP BY
                        A.APointer, A.LU_VMT, A.LU_RNR, A.LU_RNR_Makler, A.LU_RNR_R, A.LU_VSN, LA.LU_VSN_Makler,
                        A.LU_ZJ, LMP.LU_NAM, A.LU_RDT, A.LU_BDT, A.LU_FLG, A.LU_Waehrung, A.LU_VSTLD,
                        A.LU_SD_WART, A.LU_INK, A.LU_ABW
                    ORDER BY
                        A.LU_VMT, A.LU_VSN, A.LU_ZJ
                """;
        return executeRawQueryWithParameters(sql, List.of(policyNr));
    }

    @Override
    public List<dto.AbRow> fetchAbrechnungMinimal() throws Exception {
        String sql = """
                    SELECT
                        A.APointer, A.VPointer, A.PPointer,
                        A.LU_VMT, A.LU_RNR, A.LU_RNR_Makler, A.LU_RNR_R,
                        A.LU_VSN, A.LU_ZJ, A.LU_RDT, A.LU_BDT, A.LU_FLG,
                        A.LU_Waehrung, A.LU_VSTLD, A.LU_SD_WART, A.LU_INK, A.LU_ABW, A.LU_TES,
                        A.LU_NET_100, A.LU_VST, A.LU_VSTBetrag, A.LU_Praemie, A.LU_OBT, A.LU_SPAKZ,
                        A.LU_NET, A.LU_WProvision, A.LU_Restbetrag, A.LU_MA1, A.LU_MA2, A.LU_MAHN_Bemerkung
                    FROM ABRECHNUNG A
                    WHERE A.LU_TES IN ('SO','SOT','GR')
                """;

        List<dto.AbRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = tunedReadOnlyStatement(conn, sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                dto.AbRow r = new dto.AbRow(
                        rs.getLong("APointer"),
                        rs.getLong("VPointer"),
                        rs.getLong("PPointer"),
                        rs.getString("LU_VMT"),
                        rs.getString("LU_RNR"),
                        rs.getString("LU_RNR_Makler"),
                        rs.getString("LU_RNR_R"),
                        rs.getString("LU_VSN"),
                        rs.getString("LU_ZJ"),
                        rs.getString("LU_RDT"),
                        rs.getString("LU_BDT"),
                        rs.getString("LU_FLG"),
                        rs.getString("LU_Waehrung"),
                        rs.getString("LU_VSTLD"),
                        rs.getString("LU_SD_WART"),
                        rs.getString("LU_INK"),
                        rs.getString("LU_ABW"),
                        rs.getString("LU_TES"),
                        rs.getDouble("LU_NET_100"),
                        rs.getDouble("LU_VST"),
                        rs.getDouble("LU_VSTBetrag"),
                        rs.getDouble("LU_Praemie"),
                        rs.getDouble("LU_OBT"),
                        rs.getString("LU_SPAKZ"),
                        rs.getDouble("LU_NET"),
                        rs.getDouble("LU_WProvision"),
                        rs.getDouble("LU_Restbetrag"),
                        rs.getString("LU_MA1"),
                        rs.getString("LU_MA2"),
                        rs.getString("LU_MAHN_Bemerkung")
                );
                rows.add(r);
            }
        }
        return rows;
    }

    @Override
    public List<dto.LaRow> fetchLuAlleMinimal() throws Exception {
        String sql = """
                    SELECT
                        LA.VPointer,
                        LA.LU_VSN_Makler,
                        LA.LU_STATISTIK_CODE1, LA.LU_STATISTIK_CODE2, LA.LU_STATISTIK_CODE3,
                        LA.LU_STATISTIK_CODE4, LA.LU_STATISTIK_CODE5, LA.LU_STATISTIK_CODE6
                    FROM LU_ALLE LA
                """;

        List<dto.LaRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = tunedReadOnlyStatement(conn, sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                dto.LaRow r = new dto.LaRow(
                        rs.getLong("VPointer"),
                        rs.getString("LU_VSN_Makler"),
                        rs.getString("LU_STATISTIK_CODE1"),
                        rs.getString("LU_STATISTIK_CODE2"),
                        rs.getString("LU_STATISTIK_CODE3"),
                        rs.getString("LU_STATISTIK_CODE4"),
                        rs.getString("LU_STATISTIK_CODE5"),
                        rs.getString("LU_STATISTIK_CODE6")
                );
                rows.add(r);
            }
        }
        return rows;
    }

    public List<dto.LmpRow> fetchLuMaskepMinimal() throws Exception {
        String sql = """
                    SELECT
                        LMP.PPointer,
                        LMP.LU_NAM
                    FROM LU_MASKEP LMP
                """;

        List<dto.LmpRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = tunedReadOnlyStatement(conn, sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                dto.LmpRow r = new dto.LmpRow(
                        rs.getLong("PPointer"),
                        rs.getString("LU_NAM")
                );
                rows.add(r);
            }
        }
        return rows;
    }

    @Override
    public List<RowData> executeRawQuery(String sql, String... params) throws Exception {
        return executeRawQueryWithParameters(sql,
                (params == null) ? List.of() : Arrays.asList(params));
    }

    @Override
    public void invalidateCache() {
        //TO DO
    }

    /**
     * Private Hilfsmethode, um eine einfache COUNT(*)-Abfrage auszuführen und ein einzelnes Ergebnis zurückzugeben.
     */
    private int executeCountQuery(String sql) throws Exception {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        // Gibt 0 zurück, wenn die Abfrage kein Ergebnis liefert oder ein Fehler auftritt.
        return 0;
    }

    // --- JDBC Tuning (nur für große Leseabfragen) ---
    private PreparedStatement tunedReadOnlyStatement(Connection conn, String sql) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
                sql,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY
        );
        stmt.setFetchSize(1000);     // 500–2000 je nach Treiber/Netz testen
        stmt.setQueryTimeout(600);   // 120s sicherheitshalber
        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return stmt;
    }


    /**
     * Zentrale Methode zur Ausführung von Abfragen (mit oder ohne Parameter).
     */
    private List<RowData> executeRawQueryWithParameters(String sql, List<String> parameters) throws Exception {
        List<RowData> results = new ArrayList<>();
        String finalSql = sql;
        //Prüfen, ob eine dynamische IN-Klausel werden muss
        if (sql.contains("IN (%s)")) {
            // Wenn keine Parameter vorhanden sind, kann eine IN-Klausel nicht funktionieren.
            // Wir geben eine leere Liste zurück, um Fehler zu vermeiden.
            if (parameters == null || parameters.isEmpty()) {
                return results;
            }
            //Erstelle einbe Kette von Fragezeichen z.B. "?, ?, ?"
            String placeholders = String.join(",", Collections.nCopies(parameters.size(), "?"));
            // Ersetze die Platzhalter in der SQL-Abfrage
            finalSql = sql.replace("%s", placeholders);
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = tunedReadOnlyStatement(conn, finalSql)) {

            setStatementParameters(stmt, parameters); // Setzt die Parameter

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    RowData row = new RowData();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getString(i));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }

    private void setStatementParameters(PreparedStatement stmt, List<String> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            stmt.setString(i + 1, parameters.get(i));
        }
    }
}
