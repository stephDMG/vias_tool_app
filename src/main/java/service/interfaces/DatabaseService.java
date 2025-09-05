package service.interfaces;

import model.RowData;
import model.enums.ExportFormat;
import model.enums.QueryRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface DatabaseService {

    /**
     * Führt eine SQL-Abfrage aus und verarbeitet die Ergebnisse mit einem Consumer.
     *
     * @param sql       Die SQL-Abfrage, die ausgeführt werden soll.
     * @param processor Ein Consumer, der jede Zeile der Ergebnisse verarbeitet.
     */
    void executeQuery(String sql, Consumer<RowData> processor);

    /**
     * Führt eine SQL-Abfrage aus und exportiert die Ergebnisse in eine Datei.
     *
     * @param sql        Die SQL-Abfrage, die ausgeführt werden soll.
     * @param outputPath Der Pfad zur Ausgabedatei, in die die Ergebnisse exportiert werden.
     * @param format     Das Exportformat (z.B. CSV, XLSX).
     */
    void exportToFile(String sql, String outputPath, ExportFormat format);

    /**
     * Führt eine Abfrage aus und gibt die Ergebnisse als Liste von RowData zurück.
     *
     * @param query      Die Abfrage, die ausgeführt werden soll.
     * @param parameters Die Parameter für die Abfrage.
     * @return Eine Liste von RowData-Objekten, die die Ergebnisse der Abfrage repräsentieren.
     * @throws Exception bei einem Datenbankfehler.
     */
    List<RowData> executeQuery(QueryRepository query, List<String> parameters) throws Exception;

    /**
     * Führt eine Abfrage aus und exportiert die Ergebnisse in eine Datei.
     *
     * @param query      Die Abfrage, die ausgeführt werden soll.
     * @param parameters Die Parameter für die Abfrage.
     * @param outputPath Der Pfad zur Ausgabedatei.
     * @param format     Das Exportformat (z.B. CSV, XLSX).
     * @throws Exception bei einem Datenbank- oder Dateifehler.
     */
    void exportToFile(QueryRepository query, List<String> parameters, String outputPath, ExportFormat format) throws Exception;


    /**
     * Führt eine rohe SQL-Abfrage aus und gibt alle Ergebnisse zurück.
     *
     * @param sql Die auszuführende SQL-Abfrage.
     * @return Eine Liste von RowData-Objekten.
     * @throws Exception bei einem Datenbankfehler.
     */
    List<RowData> executeRawQuery(String sql) throws Exception;

    /**
     * Führt eine rohe SQL-Abfrage aus und exportiert alle Ergebnisse in eine Datei.
     *
     * @param sql        Die auszuführende SQL-Abfrage.
     * @param outputPath Der Pfad zur Ausgabedatei.
     * @param format     Das Exportformat.
     * @throws Exception bei einem Datenbank- oder Dateifehler.
     */
    void exportRawQueryToFile(String sql, String outputPath, ExportFormat format) throws Exception;

    /**
     * Ruft eine Sammlung von Schlüsselstatistiken für das Dashboard ab.
     *
     * @return Eine Map, bei der der Schlüssel der Name der Statistik ist (z.B. "Aktive Verträge")
     * und der Wert die entsprechende Zahl ist.
     * @throws Exception bei einem Datenbankfehler.
     */
    Map<String, Integer> getDashboardStatistics() throws Exception;

    /**
     * Ruft detaillierte Schadensinformationen aus der VIAS-Datenbank ab,
     * basierend auf der Schadennummer des Maklers.
     *
     * @param snrMaklerList Eine Liste von Schadennummern des Maklers, für die die Details abgerufen werden sollen.
     * @return Eine Map mit den angereicherten Daten (z.B. "Schadennummer CS", "CS Anteil", etc.)
     * oder eine leere Map, wenn keine Daten gefunden wurden.
     * @throws Exception bei einem Datenbankfehler.
     */
    Map<String, RowData> getSchadenDetailsByMaklerSnrBulk(List<String> snrMaklerList) throws Exception;


    /**
     * Ruft alle Hartrodt-Policen ab, die von Christian verwaltet werden.
     *
     * @return Eine Liste von RowData-Objekten mit Police Nr., Land, Firma usw.
     */
    List<RowData> executeHartrodtQuery() throws Exception;

    /**
     * Ruft detaillierte OP-Listen-Daten für eine spezifische Policennummer ab.
     *
     * @param policyNr Die zu suchende Policennummer.
     * @return Eine Liste von RowData-Objekten.
     */
    List<RowData> executeOpListeQuery(String policyNr) throws Exception;

    /**
     * Ruft eine minimale Abrechnungsliste ab, um RAM zu sparen.
     * nur die nötigsten Spalten mit WHERE LU_TES IN ('SO', 'SOT', 'GR') werden geladen.
     * Treibereinstellungen: forward-only, read-only, fetchSize angepasst.
     *
     * @return Liste von AbRow-Objekten.
     * @throws Exception bei einem Datenbankfehler.
     */
    List<dto.AbRow> fetchAbrechnungMinimal() throws Exception;

    /**
     * Ruft eine minimale LU_MASKEP-Liste ab, um RAM zu sparen.
     * nur die nötigsten Spalten werden geladen.
     *
     * @return Liste von LmpRow-Objekten.
     * @throws Exception bei einem Datenbankfehler.
     */
    List<dto.LaRow> fetchLuAlleMinimal() throws Exception;

    /**
     * Ruft eine minimale LU_MASKEP-Liste ab, um RAM zu sparen.
     * nur die nötigsten Spalten werden geladen.
     *
     * @return Liste von LmpRow-Objekten.
     * @throws Exception bei einem Datenbankfehler.
     */
    List<dto.LmpRow> fetchLuMaskepMinimal() throws Exception;


    void invalidateCache();
}
