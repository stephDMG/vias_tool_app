package model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Definiert alle vordefinierten SQL-Abfragen, die in der GUI verfügbar sind.
 * Jede Abfrage hat einen Anzeigenamen, eine Liste der benötigten Parameter
 * und eine Beschreibung für die GUI.
 */
public enum QueryRepository {

    OFFENE_SCHAEDEN_TOP_25(
            "TOP 25 - offene Schäden",
            List.of(),
            """
            Dieser Bericht listet die TOP 25 der offenen Schadensfälle auf.
            Er enthält Details zu den Beträgen, Reserven und dem Gesamtschaden.
            """,
            """
            SELECT
                COALESCE(RTRIM(LTRIM(SVA.LU_SNR_TEXT)), '') AS SchadenNr,
                COALESCE(RTRIM(LTRIM(SVA.LU_VSN)), '') AS PolicenNr,
               (SELECT LMP.LU_NAM FROM LU_MASKEP AS LMP WHERE LMP.PPointer = SVA.PPointer) AS Versicherungsnehmer,
                COALESCE(RTRIM(LTRIM(VMT.LU_VNA)), '') +
                 CASE
                     WHEN COALESCE(RTRIM(LTRIM(VMT.LU_VMO)), '') <> '' THEN ' ,' + RTRIM(LTRIM(VMT.LU_VMO))
                     ELSE ''
                 END AS Makler,
                SVA.LU_ZAHG_ZE_SA AS "Bezahlte Schäden (Anteil CS)",
                ((SVA.LU_RESTRESERVE * SVA.LU_ANTEIL_CS) / 100 ) AS 'Reserve (Anteil CS)',
                ((SVA.LU_RESTRESERVE * SVA.LU_ANTEIL_CS) / 100 + SVA.LU_ZAHG_ZE_SA) AS Gesamt,
                SVA.LU_ANTEIL_CS AS "Anteil CS"
            FROM
                LU_SVA AS SVA
           
            INNER JOIN
               VERMITTLER AS VMT  ON SVA.LU_VMT = VMT.LU_VMT
            WHERE
                SVA.Sparte = 'SVA' AND SVA.LU_SVSTATUS = 'O'
            ORDER BY Gesamt DESC
            LIMIT 25;
            """
    ),

    COVER_REPORT_BY_MAKLER(
            "Cover-Daten pro Makler",
            List.of("Makler-ID"),
            """
            Dieser Bericht listet alle Cover-Vertragsdaten für eine bestimmte Makler-ID auf.
            Er enthält Details wie Versicherungsschein-Nummer, Laufzeit und Firmeninformationen.
            """,
            """
            SELECT
                LAL.LU_VSN AS "Versicherungsschein-Nr", LAL.LU_VSN_Makler AS "VSN Makler", LAL.LU_BEG AS "Beginn",
                LAL.LU_ABL AS "Ende", LAL.LU_BET_STAT AS "Beteiligungsform", LAL.LU_STA AS "Vertragsstatus",
                LAL.LU_SACHBEA_VT AS "SB Vertrag",
                -- KORRIGIERT: Concat-Operator von || auf + geändert für Zen SQL Engine
                LUM.LU_NAM + ' ' + LUM.LU_NA2 AS "Firma/Name",
                LUM.LU_STRASSE AS "Strasse, Nr.", LUM.LU_PLZ AS "PLZ", LUM.LU_ORT AS "Ort", LUM.LU_NAT AS "Land"
            FROM LU_ALLE AS LAL
            INNER JOIN LU_MASKEP AS LUM ON LAL.PPointer = LUM.PPointer
            WHERE LAL.Sparte LIKE '%COVER' AND LAL.LU_VMT = ?
            ORDER BY LAL.LU_VSN ASC
            LIMIT 25;
            """
    ),

    SCHADEN_REPORT_BY_MAKLER(
            "Schaden-Daten pro Makler",
            List.of("Makler-ID"),
            """
            Dieser Bericht extrahiert alle Schadensfälle, die mit einer bestimmten Makler-ID verknüpft sind.
            Er enthält Schadendetails wie Transportbeginn, Schadentag und die beteiligten Parteien.
            """,
            String.format("""
            SELECT
                LU_VSN AS "vs_Nr", LU_SNR AS "Schaden-Nr(CS Kurz)", LU_STRABEG_DATUM AS "Transportbeginn",
                LU_SDA AS "Schadentag", LU_SACHBEA_SC AS "Sachbearbeiter", LU_SVSTATUS AS "Bearbeitungsstatus",
                LU_RESTRESERVE AS "Restreserve", LU_WAE AS "Währung",
                %s
            FROM LU_SVA
            WHERE Sparte = 'SVA' AND LU_VMT = ?
            ORDER BY LU_VSN, LU_SNR
            """, BeteiligteApply.generateAllSqlCases())
    ),
    SCHADEN_DETAILS_BY_MAKLER_SNR(
            "Schaden-Details pro Makler-SNR",
            List.of("LU_SNR_MAKLER"),
            """
            Dieser Bericht liefert detaillierte Informationen zu einem Schaden basierend auf der Makler-Schadennummer (LU_SNR_MAKLER).
            """,
            """
                  SELECT
                    s.LU_SNR_MAKLER,
                    s.LU_SNR_MAKLER_AUTO AS "Schadennummer CS",
                    s.LU_ANTEIL_CS AS "CS Anteil",
                    CASE WHEN s.LU_SVSTATUS = 'E' THEN 'Erledigt' ELSE 'Offen' END AS "Bearbeitungsstatus",
                    s.LU_SVINFO AS "Bearbeitungs-Info",
                    s.LU_SACHBEA_SC AS "Sachbearbeiter CS",
                    s.LU_RESTRESERVE AS "Restreserve",
                    (SELECT SUM(z.LU_ZBETRAG) FROM LU_Z_SV z WHERE z.LU_SNR_MAKLER = s.LU_SNR_MAKLER) AS "Zahlungen"
                    FROM LU_SVA s
                    WHERE s.Sparte = 'SVA' AND s.LU_SNR_MAKLER IN (%s)
            """
    ),

    DOCUMENT_LINKS_BY_SCHADEN(
            "Dokumenten-Links pro Schaden",
            List.of("VorgangsTyp", "Import LNR", "Bearbeiter"),
            """
            Dieser Bericht sucht nach Dokumenten, die mit Schäden verknüpft sind.
            Sie können nach Vorgangstyp (z.B. 'S011') UND nach Import-Kürzel (z.B. 'OCT') ODER Bearbeiter-Kürzel (z.B. 'CARLO') filtern.
            """,
            """
            SELECT
                LSV.LU_IMPORT_ID AS "Import ID", LSV.LU_SNR As "Schaden Nr", DC2.Betreff AS "Dokument"
            FROM LU_SVA AS LSV
            INNER JOIN DCPL0300 AS DC3 ON LSV.VPointer = DC3.VPointer
            INNER JOIN DCPL0200 AS DC2 ON DC3.Vorgang = DC2.Vorgang
            WHERE LSV.Sparte = 'SVA'
              AND DC3.VorgangsTyp = ?
              AND (LSV.LU_IMPORT_LNR = ? OR LSV.CreateBearb = ?)
            ORDER BY LSV.LU_SNR
            """
    ),

    COVER_DETAILS_BY_VSN(
            "Cover-Details pro VSN",
            List.of("VSN"),
            """
            Dieser Bericht liefert detaillierte Informationen zu einem einzelnen Cover basierend auf der Versicherungsschein-Nummer (VSN).
            Er enthält Daten wie Versicherungsart, Leistungssummen und Gesellschaftsinformationen.
            """,
            """
            SELECT
                LVC.LU_RIS AS Versicherungsart, KLA.LU_KLINFO AS PoliceInfo, LUM.LU_NAM AS "Firma/Name",
                LUM.LU_STRASSE AS Strasse, LUM.LU_STRASSE_NR AS StrasseNr, LUM.LU_PLZ AS PLZ, LUM.LU_ORT AS Ort,
                V005.LU_LANDNAME AS Land, LVC.LU_VHV_SUM_216 AS MaxJahresLeistung, LVC.LU_VHV_SUM_205 AS MaxSchadeLeistung,
                LVC.LU_Waehrung AS Waehrung, GES.LU_VUN AS GesName, GES.LU_VUO AS GesOrt
            FROM LU_VERKH_COVER AS LVC
            INNER JOIN KLAUSELN AS KLA ON LVC.LU_AR02 = KLA.LU_KLRAUSELNR
            INNER JOIN LU_MASKEP AS LUM ON LVC.PPointer = LUM.PPointer
            INNER JOIN VIASS005 AS V005 ON LUM.LU_NAT = V005.LU_INTKZ
            INNER JOIN GESELLSCHAFT AS GES ON LVC.LU_GES = GES.LU_GNR
            WHERE LVC.LU_VSN = ?
            """
    );

    private final String displayName;
    private final List<String> parameterNames;
    private final String description;
    private final String sql;


    QueryRepository(String displayName, List<String> parameterNames, String description, String sql) {
        this.displayName = displayName;
        this.parameterNames = parameterNames;
        this.description = description;
        this.sql = sql;
    }

    public String getDisplayName() { return displayName; }
    public List<String> getParameterNames() { return parameterNames; }
    public String getDescription() { return description; }
    public String getSql() { return sql; }


    public static List<String> getDisplayNames() {
        return Arrays.stream(values())
                .map(QueryRepository::getDisplayName)
                .collect(Collectors.toList());
    }

    public static QueryRepository fromDisplayName(String displayName) {
        return Arrays.stream(values())
                .filter(q -> q.getDisplayName().equals(displayName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Anzeigename: " + displayName));
    }

}
