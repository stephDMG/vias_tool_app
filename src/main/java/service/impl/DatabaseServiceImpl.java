package service.impl;

import config.DatabaseConfig;
import javafx.collections.ObservableList;
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

    /**
     * Zentrale Methode zur Ausführung von Abfragen (mit oder ohne Parameter).
     */
    private List<RowData> executeRawQueryWithParameters(String sql, List<String> parameters) throws Exception {
        List<RowData> results = new ArrayList<>();
        String finalSql = sql;
        //Prüfen, ob eine dynamische IN-Klausel werden muss
        if(sql.contains("IN (%s)")){
            // Wenn keine Parameter vorhanden sind, kann eine IN-Klausel nicht funktionieren.
            // Wir geben eine leere Liste zurück, um Fehler zu vermeiden.
            if(parameters == null || parameters.isEmpty()){
                return results;
            }
            //Erstelle einbe Kette von Fragezeichen z.B. "?, ?, ?"
            String placeholders = String.join(",", Collections.nCopies(parameters.size(), "?"));
            // Ersetze die Platzhalter in der SQL-Abfrage
            finalSql = sql.replace("%s", placeholders);
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(finalSql)) {

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
