package file.extrator;

import config.DatabaseConfig;
import model.VersicherungsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Service zur Validierung von extrahierten Versicherungsdaten gegen die VIAS-Datenbank.
 * Diese Klasse bietet Methoden, um die Existenz von Versicherungsnummern zu prüfen
 * und die extrahierten Daten mit den Daten in der Datenbank abzugleichen.
 *
 * @author Stephane Dongmo
 * @since 16/07/2025
 */
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    /**
     * Validiert eine Liste von extrahierten Versicherungsdaten.
     * Jedes Element in der Liste wird einzeln gegen die Datenbank geprüft.
     *
     * @param dataList Eine Liste von {@link VersicherungsData}-Objekten, die validiert werden sollen.
     * @return Eine Liste von {@link ValidationResult}-Objekten, die die Validierungsergebnisse für jede Datenreihe enthalten.
     */
    public List<ValidationResult> validate(List<VersicherungsData> dataList) {
        List<ValidationResult> results = new ArrayList<>();

        for (VersicherungsData data : dataList) {
            ValidationResult result = validateSingle(data);
            results.add(result);
        }

        return results;
    }

    /**
     * Validiert ein einzelnes {@link VersicherungsData}-Objekt gegen die Datenbank.
     * Dies beinhaltet die Prüfung, ob die Versicherungsnummer (VSN) existiert und, falls ja,
     * den Abgleich der vollständigen Daten des Versicherungsnehmers.
     *
     * @param data Das {@link VersicherungsData}-Objekt, das validiert werden soll.
     * @return Ein {@link ValidationResult}-Objekt, das das Ergebnis der Validierung enthält,
     * einschließlich Informationen über die Existenz der VSN, eventuelle Abweichungen
     * und den Erfolg der Validierung.
     */
    public ValidationResult validateSingle(VersicherungsData data) {
        ValidationResult result = new ValidationResult(data);

        try {
            // 1. Überprüfen, ob die Versicherungsnummer (VSN) in der Datenbank existiert.
            boolean vsnExists = isVSNPresentInDatabase(data.getVersicherungsscheinNr());
            result.setVsnExists(vsnExists);

            if (vsnExists) {
                // 2. Wenn die VSN existiert, die vollständigen Daten aus der Datenbank abrufen und vergleichen.
                VersicherungsData dbData = getDataFromDatabase(data.getVersicherungsscheinNr());
                if (dbData != null) {
                    result.setDbData(dbData);
                    // Unterschiede zwischen extrahierten und Datenbankdaten finden.
                    result.setDifferences(findDifferences(data, dbData));
                    // Festlegen, ob eine vollständige Übereinstimmung vorliegt (keine Unterschiede).
                    result.setFullMatch(result.getDifferences().isEmpty());
                }
            }

            result.setValidationSuccess(true); // Validierung erfolgreich, auch wenn Unterschiede gefunden wurden.

        } catch (Exception e) {
            logger.error("❌ Fehler bei Validierung für VSN: {}", data.getVersicherungsscheinNr(), e);
            result.setValidationSuccess(false); // Validierung fehlgeschlagen im Falle einer Ausnahme.
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Überprüft, ob eine gegebene Versicherungsnummer (VSN) in der Datenbank vorhanden ist.
     * Die Abfrage sucht in der Tabelle `LU_ALLE` nach der VSN und berücksichtigt nur Einträge mit
     * einem `Sparte` Feld, das 'COVER' enthält.
     *
     * @param vsn Die zu überprüfende Versicherungsnummer.
     * @return {@code true}, wenn die VSN in der Datenbank existiert und der Sparte entspricht,
     * andernfalls {@code false}.
     */
    public boolean isVSNPresentInDatabase(String vsn) {
        if (vsn == null || vsn.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM LU_VERKH_COVER WHERE LU_VSN = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, vsn.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean exists = rs.getInt(1) > 0;
                    logger.debug("🔍 VSN {} existiert: {}", vsn, exists);
                    return exists;
                }
            }

        } catch (Exception e) {
            logger.error("❌ Fehler bei VSN-Überprüfung: {}", vsn, e);
        }

        return false;
    }

    /**
     * Ruft die vollständigen Versicherungsdaten für eine gegebene Versicherungsnummer (VSN)
     * aus der Datenbank ab. Die Daten werden aus den Tabellen `LU_ALLE` und `LU_MASKEP`
     * unter Verwendung eines JOINs abgerufen.
     *
     * @param vsn Die Versicherungsnummer, für die Daten abgerufen werden sollen.
     * @return Ein {@link VersicherungsData}-Objekt mit den aus der Datenbank abgerufenen Daten,
     * oder {@code null}, wenn keine Daten gefunden wurden oder ein Fehler auftrat.
     */
    private VersicherungsData getDataFromDatabase(String vsn) {
        String sql = """
                    SELECT
                        LVC.LU_VSN AS "Versicherungsnummer",
                        LVC.LU_RIS AS Versicherungsart,
                        KLA.LU_KLINFO AS PoliceInfo,
                
                        LUM.LU_NAM AS "Firma/Name",
                        LUM.LU_STRASSE AS Strasse,
                        LUM.LU_STRASSE_NR AS StrasseNr,
                        LUM.LU_PLZ AS PLZ,
                        LUM.LU_ORT AS Ort,
                        V005.LU_LANDNAME AS Land,
                
                        LVC.LU_VHV_SUM_216 AS MaxJahresLeistung,
                        LVC.LU_VHV_SUM_205 AS MaxSchadenLeistung,
                        LVC.LU_Waehrung AS Waehrung,
                
                        LVC.LU_GES_Text AS Gesellschaft
                
                    FROM LU_VERKH_COVER AS LVC
                    INNER JOIN KLAUSELN AS KLA ON LVC.LU_AR02 = KLA.LU_KLRAUSELNR
                    INNER JOIN LU_MASKEP AS LUM ON LVC.PPointer = LUM.PPointer
                    INNER JOIN VIASS005  AS V005 ON LUM.LU_NAT = V005.LU_INTKZ
                
                    WHERE LVC.LU_VSN = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, vsn);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    VersicherungsData data = new VersicherungsData();
                    data.setVersicherungsscheinNr(vsn);
                    data.setVersicherungsart(rs.getString("Versicherungsart"));
                    data.setPoliceInfo(rs.getString("PoliceInfo"));
                    data.setFirmaName(rs.getString("Firma/Name"));
                    data.setStrasse(rs.getString("Strasse"));
                    data.setStrasseNr(rs.getString("StrasseNr"));
                    data.setPlz(rs.getString("PLZ"));
                    data.setOrt(rs.getString("Ort"));
                    data.setLand(rs.getString("Land"));
                    data.setMaxJahresleistung(rs.getString("MaxJahresLeistung"));
                    data.setMaxSchadenleistung(rs.getString("MaxSchadenleistung"));
                    data.setWaehrung(rs.getString("Waehrung"));
                    data.setGesellschaft(rs.getString("Gesellschaft"));
                    return data;
                }
            }

        } catch (Exception e) {
            logger.error("❌ Erreur récupération données complètes DB pour VSN: {}", vsn, e);
        }

        return null;
    }

    /**
     * Findet und listet die Unterschiede zwischen zwei {@link VersicherungsData}-Objekten auf.
     * Es werden spezifische Felder (Versicherungsnehmer, Straße, PLZ, Ort, Land) verglichen.
     *
     * @param extracted Das extrahierte {@link VersicherungsData}-Objekt.
     * @param fromDB    Das aus der Datenbank abgerufene {@link VersicherungsData}-Objekt.
     * @return Eine Liste von Strings, die die gefundenen Unterschiede beschreiben. Jede Zeichenkette
     * hat das Format "Feldname: 'Wert aus Extraktion' vs 'Wert aus Datenbank'".
     */
    private List<String> findDifferences(VersicherungsData extracted, VersicherungsData fromDB) {
        List<String> differences = new ArrayList<>();

        compareField(differences, "Versicherungsart", extracted.getVersicherungsart(), fromDB.getVersicherungsart());
        compareField(differences, "PoliceInfo", extracted.getPoliceInfo(), fromDB.getPoliceInfo());
        compareField(differences, "Firma/Name", extracted.getFirmaName(), fromDB.getFirmaName());
        compareField(differences, "Strasse", extracted.getStrasse(), fromDB.getStrasse());
        compareField(differences, "StrasseNr", extracted.getStrasseNr(), fromDB.getStrasseNr());
        compareField(differences, "PLZ", extracted.getPlz(), fromDB.getPlz());
        compareField(differences, "Ort", extracted.getOrt(), fromDB.getOrt());
        compareField(differences, "Land", extracted.getLand(), fromDB.getLand());
        compareField(differences, "MaxJahresleistung", extracted.getMaxJahresleistung(), fromDB.getMaxJahresleistung());
        compareField(differences, "MaxSchadenleistung", extracted.getMaxSchadenleistung(), fromDB.getMaxSchadenleistung());
        compareField(differences, "Waehrung", extracted.getWaehrung(), fromDB.getWaehrung());
        compareField(differences, "Gesellschaft", extracted.getGesellschaft(), fromDB.getGesellschaft());

        return differences;
    }

    /**
     * Vergleicht zwei String-Werte für ein bestimmtes Feld und fügt eine Beschreibung
     * der Differenz zur Liste hinzu, falls die Werte nicht übereinstimmen.
     * Die Werte werden vor dem Vergleich getrimmt.
     *
     * @param differences Die Liste, der die Differenzen hinzugefügt werden sollen.
     * @param fieldName Der Name des Feldes, das verglichen wird (für die Fehlermeldung).
     * @param extracted Der Wert des Feldes aus den extrahierten Daten.
     * @param fromDB Der Wert des Feldes aus den Datenbankdaten.
     */
    private void compareField(List<String> differences, String fieldName, String extracted, String fromDB) {
        String extractedClean = extracted != null ? extracted.trim() : "";
        String dbClean = fromDB != null ? fromDB.trim() : "";

        if (!extractedClean.equals(dbClean)) {
            differences.add(String.format("%s: '%s' vs '%s'", fieldName, extractedClean, dbClean));
        }
    }

    /**
     * Eine innere statische Klasse, die die Ergebnisse einer Validierung kapselt.
     * Sie enthält die extrahierten Daten, die optionalen Datenbankdaten, den Status der VSN-Existenz,
     * ob eine vollständige Übereinstimmung vorliegt, den Validierungserfolg, eine Fehlermeldung
     * und eine Liste der gefundenen Unterschiede.
     */
    public static class ValidationResult {
        private final VersicherungsData extractedData;
        private VersicherungsData dbData;
        private boolean vsnExists;
        private boolean fullMatch;
        private boolean validationSuccess;
        private String errorMessage;
        private List<String> differences = new ArrayList<>();

        /**
         * Konstruktor für ValidationResult.
         *
         * @param extractedData Die ursprünglichen extrahierten Daten, die validiert wurden.
         */
        public ValidationResult(VersicherungsData extractedData) {
            this.extractedData = extractedData;
        }

        // --- Getters und Setters ---

        /**
         * Gibt die ursprünglich extrahierten {@link VersicherungsData} zurück.
         * @return Die extrahierten Daten.
         */
        public VersicherungsData getExtractedData() { return extractedData; }

        /**
         * Gibt die aus der Datenbank abgerufenen {@link VersicherungsData} zurück.
         * Kann {@code null} sein, wenn die VSN nicht existiert oder ein Fehler auftrat.
         * @return Die Datenbankdaten.
         */
        public VersicherungsData getDbData() { return dbData; }

        /**
         * Setzt die aus der Datenbank abgerufenen {@link VersicherungsData}.
         * @param dbData Die Datenbankdaten.
         */
        public void setDbData(VersicherungsData dbData) { this.dbData = dbData; }

        /**
         * Prüft, ob die Versicherungsnummer (VSN) in der Datenbank existiert.
         * @return {@code true}, wenn die VSN existiert, sonst {@code false}.
         */
        public boolean isVsnExists() { return vsnExists; }

        /**
         * Setzt den Status, ob die Versicherungsnummer (VSN) in der Datenbank existiert.
         * @param vsnExists {@code true}, wenn die VSN existiert, sonst {@code false}.
         */
        public void setVsnExists(boolean vsnExists) { this.vsnExists = vsnExists; }

        /**
         * Prüft, ob eine vollständige Übereinstimmung zwischen den extrahierten Daten und den Datenbankdaten vorliegt.
         * Dies ist der Fall, wenn keine Unterschiede gefunden wurden.
         * @return {@code true}, wenn eine vollständige Übereinstimmung vorliegt, sonst {@code false}.
         */
        public boolean isFullMatch() { return fullMatch; }

        /**
         * Setzt den Status der vollständigen Übereinstimmung.
         * @param fullMatch {@code true}, wenn eine vollständige Übereinstimmung vorliegt, sonst {@code false}.
         */
        public void setFullMatch(boolean fullMatch) { this.fullMatch = fullMatch; }

        /**
         * Prüft, ob die Validierung als Ganzes erfolgreich war (d.h. keine unerwarteten Fehler aufgetreten sind).
         * @return {@code true}, wenn die Validierung erfolgreich war, sonst {@code false}.
         */
        public boolean isValidationSuccess() { return validationSuccess; }

        /**
         * Setzt den Status des Validierungserfolgs.
         * @param validationSuccess {@code true}, wenn die Validierung erfolgreich war, sonst {@code false}.
         */
        public void setValidationSuccess(boolean validationSuccess) { this.validationSuccess = validationSuccess; }

        /**
         * Gibt eine Fehlermeldung zurück, falls während der Validierung ein Fehler aufgetreten ist.
         * @return Die Fehlermeldung, oder {@code null}, wenn kein Fehler aufgetreten ist.
         */
        public String getErrorMessage() { return errorMessage; }

        /**
         * Setzt die Fehlermeldung, falls während der Validierung ein Fehler aufgetreten ist.
         * @param errorMessage Die Fehlermeldung.
         */
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        /**
         * Gibt eine Liste der gefundenen Unterschiede zwischen den extrahierten Daten und den Datenbankdaten zurück.
         * @return Eine Liste von Strings, die die Unterschiede beschreiben.
         */
        public List<String> getDifferences() { return differences; }

        /**
         * Setzt die Liste der gefundenen Unterschiede.
         * @param differences Die Liste der Unterschiede.
         */
        public void setDifferences(List<String> differences) { this.differences = differences; }
    }
}