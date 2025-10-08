package service.contract.repository;

import model.RowData;
import model.contract.CoverDetails;
import service.interfaces.DatabaseService;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * CoverDetailsRepository
 * <p>
 * Lädt Detailinformationen (Blöcke 0–7) für EINEN COVER-Vertrag per VSN.
 * <p>
 * Joins:
 * - MAKLERV (Makler-Name)
 * - LU_MASKEP (Versicherungsnehmer)
 * - MAP_ALLE_BETSTAT / OPZ / STA / GBEREICH (Label-Texte)
 * - MAP_ALLE_COVERRIS (Baustein-Typ-Text)
 */
public class CoverDetailsRepository {

    private final DatabaseService databaseService;

    public CoverDetailsRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Lädt alle relevanten Detailfelder (Blöcke 0–7) per VSN.
     *
     * @param vsn Versicherungsschein-Nr (Schlüssel, entspricht COVER.LU_VSN).
     * @return CoverDetails oder null, wenn nicht gefunden.
     */
    public CoverDetails fetchDetailsByVsn(String vsn) {
        String sql = buildDetailsSqlByVsn(vsn);
        List<RowData> rows = executeQuery(sql);
        if (rows.isEmpty()) return null;
        return mapToDetails(rows.get(0));
    }

    // =====================================================================================
    // SQL (alle Quellfelder mit LU_-Präfix; VIAS-freundliche Alias-Ausgabe)
    // =====================================================================================
    private String buildDetailsSqlByVsn(String vsn) {
        String v = escape(vsn);

        return ""
                + "SELECT \n"
                // Block 0 – Versicherungsnehmer
                + "  LUM.LU_NAM                          AS Versicherungsnehmer_Name,\n"
                + "  LUM.LU_VOR                          AS Versicherungsnehmer_Vorname,\n"
                + "  LUM.LU_NA2                          AS Versicherungsnehmer_Name2,\n"
                + "  LUM.LU_NA3                          AS Versicherungsnehmer_Name3,\n"
                + "  LUM.LU_STRASSE                      AS Versicherungsnehmer_Strasse,\n"
                + "  LUM.LU_PLZ                          AS Versicherungsnehmer_PLZ,\n"
                + "  LUM.LU_ORT                          AS Versicherungsnehmer_Ort,\n"
                + "  LUM.LU_NAT                          AS Versicherungsnehmer_Nation,\n"
                // Block 1
                + "  COVER.LU_VSN                        AS Versicherungsschein_Nr,\n"
                + "  COVER.LU_VSN_VR                     AS VSN_Versicherer,\n"
                + "  COVER.LU_VSN_MAKLER                 AS VSN_Makler,\n"
                + "  COVER.LU_RIS_ID                     AS Versicherungsart_Code,\n"
                + "  COVER.LU_RIS                        AS Versicherungsart_Text,\n"
                + "  COVER.LU_ART                        AS Vertragsparte_Code,\n"
                + "  COVER.LU_ART_Text                   AS Vertragsparte_Text,\n"
                + "  COVER.LU_BAUST_RIS                  AS Baustein_Typ_Code,\n"
                + "  MAC.TAB_VALUE                       AS Baustein_Typ_Text,\n"
                // Block 2
                + "  COVER.LU_NEUVERTRAG_JN              AS Beginn_Neuvertrag_JN,\n"
                + "  COVER.LU_BEG                        AS Beginn_Datum,\n"
                + "  COVER.LU_BEGUHR                     AS Beginn_Uhrzeit,\n"
                + "  COVER.LU_ABL                        AS Ablauf_Datum,\n"
                + "  COVER.LU_ABLUHR                     AS Ablauf_Uhrzeit,\n"
                + "  COVER.LU_HFL                        AS Hauptfaelligkeit,\n"
                + "  COVER.LU_LFZ                        AS Laufzeit_Jahre,\n"
                + "  COVER.LU_VERTRAG_ART                AS Laufender_Vertrag,\n"
                + "  COVER.LU_A99_JN                     AS Buchung_nach_Ablaufdatum,\n"
                + "  COVER.LU_VERS_GRUND                 AS Versionierungsgrund,\n"
                + "  COVER.LU_FLG                        AS Version_von,\n"
                + "  COVER.LU_FLZ                        AS Version_bis,\n"
                + "  COVER.LU_STATUS                     AS Version_Status,\n"
                // Block 3
                + "  COVER.LU_VUF_KDR                    AS Fuehrender_Versicherer,\n"
                + "  COVER.LU_FUF_ANTEIL                 AS Anteil_VR_Prozent,\n"
                + "  COVER.LU_VUF_BEM                    AS Bemerkung_VR,\n"
                + "  COVER.LU_VUA_KDR                    AS Assekuradeur,\n"
                + "  COVER.LU_VUA_ANTEIL                 AS Anteil_Assek_Prozent,\n"
                + "  COVER.LU_VUA_BEM                    AS Bemerkung_Assek,\n"
                // Block 4
                + "  COVER.LU_VORGANG_ID                 AS Vorgang_ID,\n"
                + "  COVER.LU_GES                        AS Gesellschaft_Code,\n"
                + "  COVER.LU_GES_Text                   AS Gesellschaft_Name,\n"
                + "  COVER.LU_VMT                        AS MaklerNr,\n"
                + "  MAK.LU_NAM                          AS Makler,\n"
                + "  COVER.LU_VMT2                       AS Altmakler,\n"
                + "  COVER.LU_SPAKZ                      AS Courtagesatz,\n"
                + "  COVER.LU_AGT                        AS Prov_Vereinbarung,\n"
                // Block 5
                + "  COVER.LU_BET_STAT                   AS Beteiligungsform_Code,\n"
                + "  MAB.TAB_VALUE                       AS Beteiligungsform_Text,\n"
                + "  COVER.LU_POOLNR                     AS Pool_Nr,\n"
                + "  COVER.LU_OPZ                        AS Vertragsstatus_Code,\n"
                + "  MAO.TAB_VALUE                       AS Vertragsstatus_Text,\n"
                + "  COVER.LU_STA                        AS Vertragsstand_Code,\n"
                + "  MAS.TAB_VALUE                       AS Vertragsstand_Text,\n"
                + "  COVER.LU_FRONTING_JN                AS Fronting,\n"
                + "  COVER.LU_BASTAND                    AS Bearbeitungsstand,\n"
                + "  COVER.LU_GBEREICH                   AS OP_Gruppe_Code,\n"
                + "  MAG.MTEXT                           AS OP_Gruppe_Text,\n"
                + "  COVER.LU_KUEFRIV_DAT                AS Kuendigungsfristverkuerzung_zum,\n"
                + "  COVER.LU_KUEFRIV_DURCH              AS Veranlasst_durch,\n"
                // Zuständigkeiten
                + "  COVER.LU_SACHBEA_VT                 AS SB_Vertr,\n"
                + "  COVER.LU_SACHBEA_SC                 AS SB_Schad,\n"
                + "  COVER.LU_SACHBEA_RG                 AS SB_Rechnung,\n"
                + "  COVER.LU_SACHBEA_GL                 AS GL_Prokurist,\n"
                + "  COVER.LU_SACHBEA_DOK                AS SB_Doku,\n"
                + "  COVER.LU_SACHBEA_BUH                AS SB_BuHa,\n"
                // Block 6
                + "  COVER.LU_EDA                        AS Letzte_Aenderung,\n"
                + "  COVER.LU_EGR                        AS Grund,\n"
                + "  COVER.LU_SACHBEA_EGR                AS Grund_durch,\n"
                + "  COVER.LU_DST                        AS Storno_zum,\n"
                + "  COVER.LU_AGR                        AS Storno_Grund,\n"
                + "  COVER.LU_SACHBEA_AGR                AS Storno_durch,\n"
                // Block 7
                + "  COVER.LU_WVL1                       AS Wiedervorlage_Datum_1,\n"
                + "  COVER.LU_WVG1                       AS Wiedervorlage_Grund_1,\n"
                + "  COVER.LU_SACHBEA_WVG1               AS Wiedervorlage_durch_1,\n"
                + "  COVER.LU_WVG1_PRIO                  AS Wiedervorlage_Prio_1,\n"
                + "  COVER.LU_WVL2                       AS Wiedervorlage_Datum_2,\n"
                + "  COVER.LU_WVG2                       AS Wiedervorlage_Grund_2,\n"
                + "  COVER.LU_SACHBEA_WVG2               AS Wiedervorlage_durch_2,\n"
                + "  COVER.LU_WVG2_PRIO                  AS Wiedervorlage_Prio_2\n"
                + "FROM LU_ALLE AS COVER\n"
                + "LEFT JOIN MAKLERV              AS MAK ON COVER.LU_VMT        = MAK.LU_VMTNR\n"
                + "LEFT JOIN LU_MASKEP            AS LUM ON COVER.PPointer      = LUM.PPointer\n"
                + "LEFT JOIN MAP_ALLE_COVERRIS    AS MAC ON COVER.LU_BAUST_RIS  = MAC.TAB_ID\n"
                + "LEFT JOIN MAP_ALLE_BETSTAT     AS MAB ON COVER.LU_BET_STAT   = MAB.TAB_ID\n"
                + "LEFT JOIN MAP_ALLE_OPZ         AS MAO ON COVER.LU_OPZ        = MAO.TAB_ID\n"
                + "LEFT JOIN MAP_ALLE_STA         AS MAS ON COVER.LU_STA        = MAS.TAB_ID\n"
                + "LEFT JOIN MAP_ALLE_GBEREICH    AS MAG ON COVER.LU_GBEREICH   = MAG.TAB_ID\n"
                + "WHERE COVER.Sparte LIKE '%COVER' AND COVER.LU_VSN = '" + v + "'\n";
    }

    // =====================================================================================
    // Mapping (RowData → CoverDetails) — Keys = Aliasnamen
    // =====================================================================================
    private CoverDetails mapToDetails(RowData row) {
        Map<String, String> m = row.getValues();
        CoverDetails d = new CoverDetails();

        // Block 0 – Versicherungsnehmer
        d.setInsuredName(m.get("Versicherungsnehmer_Name"));
        d.setInsuredFirstname(m.get("Versicherungsnehmer_Vorname"));
        d.setInsuredName2(m.get("Versicherungsnehmer_Name2"));
        d.setInsuredName3(m.get("Versicherungsnehmer_Name3"));
        d.setInsuredStreet(m.get("Versicherungsnehmer_Strasse"));
        d.setInsuredPlz(m.get("Versicherungsnehmer_PLZ"));
        d.setInsuredCity(m.get("Versicherungsnehmer_Ort"));
        d.setInsuredNation(m.get("Versicherungsnehmer_Nation"));

        // Block 1
        d.setVsn(m.get("Versicherungsschein_Nr"));
        d.setVsnVr(m.get("VSN_Versicherer"));
        d.setVsnMakler(m.get("VSN_Makler"));
        d.setRisId(m.get("Versicherungsart_Code"));
        d.setRisText(m.get("Versicherungsart_Text"));
        d.setArt(m.get("Vertragsparte_Code"));
        d.setArtText(m.get("Vertragsparte_Text"));
        d.setBausteinRis(m.get("Baustein_Typ_Code"));

        // Block 2
        d.setNeuvertragJn(m.get("Beginn_Neuvertrag_JN"));
        d.setBeg(m.get("Beginn_Datum"));
        d.setBegUhr(m.get("Beginn_Uhrzeit"));
        d.setAbl(m.get("Ablauf_Datum"));
        d.setAblUhr(m.get("Ablauf_Uhrzeit"));
        d.setHfl(m.get("Hauptfaelligkeit"));
        d.setLfz(m.get("Laufzeit_Jahre"));
        d.setVertragArt(m.get("Laufender_Vertrag"));
        d.setA99Jn(m.get("Buchung_nach_Ablaufdatum"));
        d.setVersGrund(m.get("Versionierungsgrund"));
        d.setFlg(m.get("Version_von"));
        d.setFlz(m.get("Version_bis"));
        d.setStatus(m.get("Version_Status"));

        // Block 3
        d.setVufKdr(m.get("Fuehrender_Versicherer"));
        d.setFufAnteil(m.get("Anteil_VR_Prozent"));
        d.setVufBem(m.get("Bemerkung_VR"));
        d.setVuaKdr(m.get("Assekuradeur"));
        d.setVuaAnteil(m.get("Anteil_Assek_Prozent"));
        d.setVuaBem(m.get("Bemerkung_Assek"));

        // Block 4
        d.setVorgangId(m.get("Vorgang_ID"));
        d.setGes(m.get("Gesellschaft_Code"));
        d.setGesText(m.get("Gesellschaft_Name"));
        d.setVmt(m.get("MaklerNr"));
        d.setVmt2(m.get("Altmakler"));
        d.setCourtageSatz(m.get("Courtagesatz"));
        d.setAgt(m.get("Prov_Vereinbarung"));

        // Block 5
        d.setBetStat(m.get("Beteiligungsform_Code"));
        d.setPoolNr(m.get("Pool_Nr"));
        d.setOpz(m.get("Vertragsstatus_Code"));
        d.setSta(m.get("Vertragsstand_Code"));
        d.setFrontingJn(m.get("Fronting"));
        d.setBaStand(m.get("Bearbeitungsstand"));
        d.setgBereich(m.get("OP_Gruppe_Code"));
        d.setKueFrivDat(m.get("Kuendigungsfristverkuerzung_zum"));
        d.setKueFrivDurch(m.get("Veranlasst_durch"));

        // Zuständigkeiten
        d.setSbVertrag(m.get("SB_Vertr"));
        d.setSbSchaden(m.get("SB_Schad"));
        d.setSbRechnung(m.get("SB_Rechnung"));
        d.setSbGl(m.get("GL_Prokurist"));
        d.setSbDoku(m.get("SB_Doku"));
        d.setSbBuha(m.get("SB_BuHa"));

        // Block 6
        d.setEda(m.get("Letzte_Aenderung"));
        d.setEgr(m.get("Grund"));
        d.setSachbearbEgr(m.get("Grund_durch"));
        d.setDst(m.get("Storno_zum"));
        d.setAgr(m.get("Storno_Grund"));
        d.setSachbearbAgr(m.get("Storno_durch"));

        // Block 7
        d.setWvl1(m.get("Wiedervorlage_Datum_1"));
        d.setWvlg1(m.get("Wiedervorlage_Grund_1"));
        d.setSbWvg1(m.get("Wiedervorlage_durch_1"));
        d.setWvg1Prio(m.get("Wiedervorlage_Prio_1"));
        d.setWvl2(m.get("Wiedervorlage_Datum_2"));
        d.setWvlg2(m.get("Wiedervorlage_Grund_2"));
        d.setSbWvg2(m.get("Wiedervorlage_durch_2"));
        d.setWvg2Prio(m.get("Wiedervorlage_Prio_2"));

        return d;
    }

    // =====================================================================================
    // DB-Ausführung (Reflexion wie vereinbart)
    // =====================================================================================
    @SuppressWarnings("unchecked")
    private List<RowData> executeQuery(String sql) {
        try {
            return databaseService.executeRawQuery(sql);
        } catch (NoSuchMethodError | NoSuchMethodException e) {
            try {
                for (String methodName : new String[]{"executeRawQuery", "executeQuery", "query", "runQuery", "execute"}) {
                    try {
                        Method m = databaseService.getClass().getMethod(methodName, String.class);
                        Object result = m.invoke(databaseService, sql);
                        if (result instanceof List<?>) {
                            return (List<RowData>) result;
                        }
                    } catch (NoSuchMethodException ignored) { /* next */ }
                }
            } catch (Exception inner) {
                throw new IllegalStateException("Datenbankabfrage fehlgeschlagen: " + inner.getMessage(), inner);
            }
            throw new IllegalStateException("Keine kompatible Query-Methode im DatabaseService gefunden.");
        } catch (Exception e) {
            String msg = (e.getCause() != null)
                    ? (e.getCause().getClass().getName() + ": " + e.getCause().getMessage())
                    : (e.getClass().getName() + ": " + e.getMessage());
            throw new IllegalStateException("Datenbankabfrage fehlgeschlagen: " + msg + " | SQL=" + sql, e);
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''").trim();
    }
}
