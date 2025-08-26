package model.ai.provider.impl;

import model.ai.AiColumnSpec;
import model.ai.AiReportTemplate;
import model.ai.provider.interfaces.AiKnowledgeProvider;

import java.util.List;
import java.util.Map;

/**
 * Stellt das Wissen über "Schäden" (Tabelle LU_SVA) für die KI bereit.
 * Diese Version enthält alle relevanten Felder aus der Beispieldatei sva_Schade.txt.
 */
public class SchadenKnowledgeProvider implements AiKnowledgeProvider {

    private static final AiReportTemplate DYNAMIC_SCHADEN_REPORT_TEMPLATE;

    static {
        // Die erweiterte Spaltenbibliothek für Schäden
        Map<String, AiColumnSpec> schadenColumns = Map.ofEntries(
                // === Identifikatoren & Nummern ===
                Map.entry("vsn", new AiColumnSpec("LU_VSN", "VS-Nr", "LS", List.of("vsn", "vs-nr", "versicherungsschein"))),
                Map.entry("schaden_nr", new AiColumnSpec("LU_SNR", "Schaden-Nr", "LS", List.of("schaden-nr", "schadennummer", "snr", "cs-nummer"))),
                Map.entry("makler_nr", new AiColumnSpec("LU_SNR_MAKLER", "Makler-Schaden-Nr", "LS", List.of("makler schaden-nr", "makler-snr"))),
                Map.entry("vorgang_id", new AiColumnSpec("LU_VORGANG_ID", "Vorgang-ID", "LS", List.of("vorgang-id", "vorgangsnummer"))),
                Map.entry("makler_id", new AiColumnSpec("LU_VMT", "Makler-ID", "LS", List.of("makler-id", "vmt", "vermittler"))),
                Map.entry("gesellschaft_nr", new AiColumnSpec("LU_GES", "Gesellschafts-Nr", "LS", List.of("gesellschaft-nr", "ges-nr"))),
                Map.entry("gesellschaft_name", new AiColumnSpec("LU_GES_Text", "Gesellschaft", "LS", List.of("gesellschaft", "gesellschaftsname"))),

                // === Wichtige Daten ===
                Map.entry("transport_beginn", new AiColumnSpec("LU_STRABEG_DATUM", "Transportbeginn", "LS", List.of("transportbeginn", "beginn transport"))),
                Map.entry("schadentag", new AiColumnSpec("LU_SDA", "Schadentag", "LS", List.of("schadentag", "schadendatum", "sda"))),
                Map.entry("anlage_datum", new AiColumnSpec("LU_SANL_DATUM", "Anlagedatum", "LS", List.of("anlagedatum", "erstellt am", "angelegt am"))),
                Map.entry("melde_datum", new AiColumnSpec("LU_SMELD_DATUM", "Meldedatum", "LS", List.of("meldedatum"))),
                Map.entry("erledigt_datum", new AiColumnSpec("LU_ERLEDIGT", "Erledigt am", "LS", List.of("erledigt", "erledigt am", "geschlossen am"))),
                Map.entry("verjaehrung_datum", new AiColumnSpec("LU_VERJ_DATUM", "Verjährung", "LS", List.of("verjährung", "verjaehrung"))),

                // === Status, Beträge & Währung ===
                Map.entry("status", new AiColumnSpec("LU_SVSTATUS", "Bearbeitungsstatus", "LS", List.of("status", "bearbeitungsstatus", "schadenstatus"))),
                Map.entry("sachbearbeiter", new AiColumnSpec("LU_SACHBEA_SC", "Sachbearbeiter", "LS", List.of("sachbearbeiter", "sb", "bearbeiter"))),
                Map.entry("sparte", new AiColumnSpec("LU_SPARTENNAME", "Sparte", "LS", List.of("sparte", "spartenname"))),
                Map.entry("waehrung", new AiColumnSpec("LU_WAE", "Währung", "LS", List.of("währung", "wae"))),
                Map.entry("restreserve", new AiColumnSpec("LU_RESTRESERVE", "Restreserve", "LS", List.of("restreserve"), true)),
                Map.entry("schadensumme", new AiColumnSpec("LU_SSU", "Schadensumme", "LS", List.of("schadensumme", "ssu"), true)),
                Map.entry("schaden_offen", new AiColumnSpec("LU_SSO", "Schaden Offen", "LS", List.of("schaden offen", "sso"), true)),
                Map.entry("reserve", new AiColumnSpec("LU_RESERVE", "Reserve", "LS", List.of("reserve"), true)),
                Map.entry("cs_anteil", new AiColumnSpec("LU_ANTEIL_CS", "CS-Anteil", "LS", List.of("cs-anteil", "anteil cs"), true)),

                // === Transport- & Waren-Details ===
                Map.entry("transportweg_start", new AiColumnSpec("LU_WR_TRANSWEG", "Transportweg Start", "LS", List.of("transportweg start", "von", "abgangsort"))),
                Map.entry("transportweg_ziel", new AiColumnSpec("LU_WR_TRANSWEG3", "Transportweg Ziel", "LS", List.of("transportweg ziel", "bis", "bestimmungsort", "zielort"))),
                Map.entry("warenbezeichnung", new AiColumnSpec("LU_WR_WAREBEZ", "Warenbezeichnung", "LS", List.of("ware", "warenbezeichnung"))),
                Map.entry("schiffsname", new AiColumnSpec("LU_WR_SCHIFFN", "Schiffsname", "LS", List.of("schiff", "schiffsname"))),
                Map.entry("frachtbrief", new AiColumnSpec("LU_WR_BLFR", "Frachtbrief-Nr", "LS", List.of("frachtbrief", "b/l-nr", "bl-nr"))),
                Map.entry("container_nr", new AiColumnSpec("LU_WR_CONTNR", "Container-Nr", "LS", List.of("container", "container-nr"))),

                // === Beteiligte Parteien (mit CASE-Logik) ===
                Map.entry("subunternehmer", new AiColumnSpec(
                        "CASE WHEN LS.LU_SAST_PNR IS NULL OR RTRIM(LTRIM(LS.LU_SAST_PNR)) = '' THEN 'Nein' ELSE LS.LU_SAST_PNR END",
                        "Subunternehmer", "", List.of("subunternehmer", "sast"))),
                Map.entry("verursacher", new AiColumnSpec(
                        "CASE WHEN LS.LU_VURS_PNR IS NULL OR RTRIM(LTRIM(LS.LU_VURS_PNR)) = '' THEN 'Nein' ELSE LS.LU_VURS_PNR END",
                        "Verursacher", "", List.of("verursacher", "vurs"))),
                Map.entry("anspruchsteller", new AiColumnSpec(
                        "CASE WHEN LS.LU_AST_PNR IS NULL OR RTRIM(LTRIM(LS.LU_AST_PNR)) = '' THEN 'Nein' ELSE LS.LU_AST_PNR END",
                        "Anspruchsteller", "", List.of("anspruchsteller", "ast"))),
                Map.entry("geschaedigter", new AiColumnSpec(
                        "CASE WHEN LS.LU_GESCH_PNR IS NULL OR RTRIM(LTRIM(LS.LU_GESCH_PNR)) = '' THEN 'Nein' ELSE LS.LU_GESCH_PNR END",
                        "Geschädigter", "", List.of("geschädigter", "geschaedigter", "gesch"))),
                Map.entry("surveyor", new AiColumnSpec("LS.LU_SURV_NAM_PNR", "Surveyor", "LS", List.of("surveyor", "gutachter", "besichtiger")))
        );

        DYNAMIC_SCHADEN_REPORT_TEMPLATE = new AiReportTemplate(
                "Dynamischer Schaden-Bericht",
                List.of("schäden", "schaden", "sva"), // Schlüsselwörter für diesen Berichtstyp
                schadenColumns,
                // Die SQL-Vorlage, `LS` ist der Alias für die Tabelle LU_SVA
                "SELECT {COLUMNS} FROM LU_SVA AS LS " +
                        "WHERE LS.Sparte = 'SVA' AND {CONDITIONS} " +
                        "ORDER BY LS.LU_VSN, LS.LU_SNR"
        );
    }

    @Override
    public List<AiReportTemplate> getReportTemplates() {
        return List.of(DYNAMIC_SCHADEN_REPORT_TEMPLATE);
    }
}