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
 * Intelligenter Validierungsservice für Versicherungsdaten.
 * Verwendet eine text.contains() Strategie anstatt Feld-für-Feld Vergleiche.
 * Die Datenbank ist die einzige Quelle der Wahrheit.
 *
 * @author Stephane Dongmo
 * @since 17/07/2025
 */
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    /**
     * Validiert eine Liste von Versicherungsdaten intelligent.
     *
     * @param dataList Liste der zu validierenden Daten (nur VSN + PDF-Text)
     * @return Liste der intelligenten Validierungsergebnisse
     */
    public List<SmartValidationResult> validateSmart(List<VersicherungsData> dataList) {
        List<SmartValidationResult> results = new ArrayList<>();

        for (VersicherungsData data : dataList) {
            SmartValidationResult result = validateSingleSmart(data);
            results.add(result);
        }

        return results;
    }

    /**
     * Intelligente Validierung eines einzelnen Datensatzes.
     * Holt die DB-Daten und prüft ob sie im PDF-Text vorhanden sind.
     *
     * @param extractedData Extrahierte Daten (VSN + PDF-Text)
     * @return Intelligentes Validierungsergebnis
     */
    public SmartValidationResult validateSingleSmart(VersicherungsData extractedData) {
        String vsn = extractedData.getVersicherungsscheinNr();
        String pdfText = extractedData.getPdfText();

        SmartValidationResult result = new SmartValidationResult(vsn, pdfText);

        try {
            // 1. Daten aus Datenbank holen
            VersicherungsData dbData = getCompleteDataFromDatabase(vsn);

            if (dbData == null) {
                result.setValidationSuccess(false);
                result.setErrorMessage("VSN nicht in Datenbank gefunden");
                logger.warn("❓ VSN {} nicht in Datenbank gefunden", vsn);
                return result;
            }

            // 2. Intelligente Validierung: DB-Daten im PDF suchen
            boolean isValid = validateDataInPdfText(dbData, pdfText);

            if (isValid) {
                result.setValidationSuccess(true);
                result.setValidatedData(dbData);  // ← DATENBANK-DATEN verwenden!
                logger.info("✅ VSN {} intelligent validiert - DB-Daten werden verwendet", vsn);
            } else {
                result.setValidationSuccess(false);
                result.setErrorMessage("DB-Daten nicht im PDF-Text gefunden");
                logger.warn("❌ VSN {} - DB-Daten stimmen nicht mit PDF überein", vsn);
            }

        } catch (Exception e) {
            logger.error("❌ Fehler bei intelligenter Validierung für VSN: {}", vsn, e);
            result.setValidationSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Holt vollständige Daten aus der Datenbank mit der kompletten SQL-Abfrage.
     *
     * @param vsn Versicherungsschein-Nummer
     * @return Vollständige Datenbank-Daten oder null
     */
    private VersicherungsData getCompleteDataFromDatabase(String vsn) {
        String sql = """
            SELECT
                LVC.LU_RIS AS Versicherungsart,
                KLA.LU_KLINFO AS PoliceInfo,
                LUM.LU_NAM AS "Firma/Name",
                LUM.LU_STRASSE AS Strasse,
                LUM.LU_STRASSE_NR AS StrasseNr,
                LUM.LU_PLZ AS PLZ,
                LUM.LU_ORT AS Ort,
                V005.LU_LANDNAME AS Land,
                LVC.LU_VHV_SUM_216 AS MaxJahresLeistung,
                LVC.LU_VHV_SUM_205 AS MaxSchadeLeistung,
                LVC.LU_Waehrung AS Waehrung,
                GES.LU_VUN AS GesName,
                GES.LU_VUO AS GesOrt
            FROM LU_VERKH_COVER AS LVC
            INNER JOIN KLAUSELN AS KLA ON LVC.LU_AR02 = KLA.LU_KLRAUSELNR
            INNER JOIN LU_MASKEP AS LUM ON LVC.PPointer = LUM.PPointer
            INNER JOIN VIASS005 AS V005 ON LUM.LU_NAT = V005.LU_INTKZ
            INNER JOIN GESELLSCHAFT AS GES ON LVC.LU_GES = GES.LU_GNR
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
                    data.setMaxSchadenleistung(rs.getString("MaxSchadeLeistung"));
                    data.setWaehrung(rs.getString("Waehrung"));
                    data.setGesName(rs.getString("GesName"));
                    data.setGesOrt(rs.getString("GesOrt"));
                    return data;
                }
            }

        } catch (Exception e) {
            logger.error("❌ Fehler beim Laden der DB-Daten für VSN: {}", vsn, e);
        }

        return null;
    }

    /**
     * Intelligente Validierung: Prüft ob die DB-Daten im PDF-Text vorhanden sind.
     * Verwendet text.contains() Strategie.
     *
     * @param dbData Daten aus der Datenbank
     * @param pdfText Kompletter PDF-Text
     * @return true wenn DB-Daten im PDF gefunden werden
     */
    private boolean validateDataInPdfText(VersicherungsData dbData, String pdfText) {
        if (pdfText == null || pdfText.trim().isEmpty()) {
            logger.warn("⚠️ PDF-Text ist leer");
            return false;
        }
        List<String> criticalFields = new ArrayList<>();

        // Firma/Name
        if (dbData.getFirmaName() != null && !dbData.getFirmaName().trim().isEmpty()) {
            criticalFields.add(dbData.getFirmaName().trim());
        }

        // PLZ
        if (dbData.getPlz() != null && !dbData.getPlz().trim().isEmpty()) {
            criticalFields.add(dbData.getPlz().trim());
        }

        // Ort
        if (dbData.getOrt() != null && !dbData.getOrt().trim().isEmpty()) {
            criticalFields.add(dbData.getOrt().trim());
        }

        // Strasse
        if (dbData.getStrasse() != null && !dbData.getStrasse().trim().isEmpty()) {
            criticalFields.add(dbData.getStrasse().trim());
        }

        //Gesellschaftsname
        if (dbData.getGesName() != null && !dbData.getGesName().trim().isEmpty()) {
            criticalFields.add(dbData.getGesName().trim());
        }

        //GesellschaftsOrt
        if (dbData.getGesOrt() != null && !dbData.getGesOrt().trim().isEmpty()) {
            criticalFields.add(dbData.getGesOrt().trim());
        }

        // Alle kritischen Felder müssen im PDF-Text gefunden werden
        for (String field : criticalFields) {
            if (!pdfText.contains(field)) {
                logger.debug("❌ Feld '{}' nicht im PDF-Text gefunden", field);
                return false;
            } else {
                logger.debug("✅ Feld '{}' im PDF-Text gefunden", field);
            }
        }

        logger.info("✅ Alle kritischen DB-Felder im PDF-Text gefunden");
        return true;
    }

    /**
     * Ergebnis-Klasse für intelligente Validierung.
     */
    public static class SmartValidationResult {
        private final String vsn;
        private final String pdfText;
        private VersicherungsData validatedData;
        private boolean validationSuccess;
        private String errorMessage;

        public SmartValidationResult(String vsn, String pdfText) {
            this.vsn = vsn;
            this.pdfText = pdfText;
        }

        // Getters und Setters
        public String getVsn() { return vsn; }
        public String getPdfText() { return pdfText; }
        public VersicherungsData getValidatedData() { return validatedData; }
        public void setValidatedData(VersicherungsData validatedData) { this.validatedData = validatedData; }
        public boolean isValidationSuccess() { return validationSuccess; }
        public void setValidationSuccess(boolean validationSuccess) { this.validationSuccess = validationSuccess; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}