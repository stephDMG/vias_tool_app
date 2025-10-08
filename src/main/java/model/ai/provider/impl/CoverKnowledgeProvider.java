package model.ai.provider.impl;

import model.ai.AiColumnSpec;
import model.ai.AiReportTemplate;
import model.ai.provider.interfaces.AiKnowledgeProvider;

import java.util.List;
import java.util.Map;

/**
 * The central knowledge library for the local AI.
 * Contains all definitions for AI-driven reports, now based on the comprehensive LU_ALLE table.
 */
public class CoverKnowledgeProvider implements AiKnowledgeProvider {

    private static final AiReportTemplate DYNAMIC_COVER_REPORT_TEMPLATE;

    static {
        Map<String, AiColumnSpec> standardColumns = Map.ofEntries(
                // === Identifiers & Numbers ===
                Map.entry("vsn", new AiColumnSpec("LAL.LU_VSN", "VSN", "", List.of("vsn", "versicherungsschein", "policeNr"))),
                Map.entry("vsn_makler", new AiColumnSpec("LAL.LU_VSN_Makler", "VSN Makler", "", List.of("vsn makler", "makler-vsn", "policeNr makler"))),
                Map.entry("vsn_vr", new AiColumnSpec("LAL.LU_VSN_VR", "VSN Versicherer", "", List.of("vsn vr", "vsn versicherer", "policeNr vr"))),
                Map.entry("makler_nr", new AiColumnSpec("LAL.LU_VMT", "Makler-Nr", "", List.of("makler-nr", "makler nr", "vmt"))),
                Map.entry("altmakler_nr", new AiColumnSpec("LAL.LU_VMT2", "Altmaker-Nr", "", List.of("altmakler", "vmt2"))),
                Map.entry("gesellschaft_nr", new AiColumnSpec("LAL.LU_GES", "Ges. Nr", "", List.of("ges nr", "gesellschaftsnummer"))),
                Map.entry("vorgang_id", new AiColumnSpec("LAL.LU_VORGANG_ID", "Vorgang ID", "", List.of("vorgang id", "vorgangsnummer"))),
                Map.entry("risiko_id", new AiColumnSpec("LAL.LU_RIS_ID", "Risiko ID", "", List.of("risiko id"))),

                // === Partner & Address Details ===
                Map.entry("partner_typ", new AiColumnSpec("LUM.LU_TYP", "Partner-Typ", "", List.of("partnertyp", "partner-typ"))),
                Map.entry("firma", new AiColumnSpec("LUM.LU_NAM", "Firma/Name", "", List.of("firma", "name", "kunde"))),
                Map.entry("name2", new AiColumnSpec("LUM.LU_NA2", "Namenszusatz 1", "", List.of("name2", "namenszusatz 1"))),
                Map.entry("name3", new AiColumnSpec("LUM.LU_NA3", "Namenszusatz 2", "", List.of("name3", "namenszusatz 2"))),
                Map.entry("vorname", new AiColumnSpec("LUM.LU_VOR", "Vorname", "", List.of("vorname"))),
                Map.entry("strasse", new AiColumnSpec("LUM.LU_STRASSE", "Strasse", "", List.of("strasse", "adresse"))),
                Map.entry("hausnummer", new AiColumnSpec("LUM.LU_STRASSE_NR", "Hausnummer", "", List.of("hausnummer", "str nr"))),
                Map.entry("plz", new AiColumnSpec("LUM.LU_PLZ", "PLZ", "", List.of("plz", "postleitzahl"))),
                Map.entry("ort", new AiColumnSpec("LUM.LU_ORT", "Ort", "", List.of("ort", "stadt"))),
                Map.entry("land", new AiColumnSpec("V05.LU_LANDNAME", "Land", "", List.of("land", "landname"))),
                Map.entry("land_code", new AiColumnSpec("LUM.LU_NAT", "Land Code", "", List.of("land code", "nat"))),
                Map.entry("makler_name", new AiColumnSpec("MAK.LU_NAM", "Makler Name", "", List.of("makler name", "makler"))),

                // === Contract Details ===
                Map.entry("beginn", new AiColumnSpec("LAL.LU_BEG", "Beginn", "", List.of("beginn", "anfang"))),
                Map.entry("ablauf", new AiColumnSpec("LAL.LU_ABL", "Ablauf", "", List.of("ablauf", "ende"))),

                Map.entry("laufzeit", new AiColumnSpec("LAL.LU_LFZ", "Laufzeit in Jahren", "", List.of("laufzeit", "lfz"), true)),
                Map.entry("vertragsart", new AiColumnSpec("LAL.LU_ART_Text", "Vertragsart", "", List.of("vertragsart", "art"))),
                Map.entry("vertragsstatus", new AiColumnSpec("MAO.TAB_VALUE", "Vertragsstatus", "", List.of("vertragsstatus"))),
                Map.entry("vertragsstand", new AiColumnSpec("MAS.TAB_VALUE", "Vertragsstand", "", List.of("vertragsstand", "stand"))),
                Map.entry("beteiligungsform", new AiColumnSpec("MAB.TAB_VALUE", "Beteiligungsform", "", List.of("beteiligungsform", "beteiligung"))),
                Map.entry("risiko", new AiColumnSpec("LAL.LU_RIS", "Risiko", "", List.of("risiko"))),
                Map.entry("baustein_typ", new AiColumnSpec("MAC.TAB_VALUE", "Baustein Typ", "", List.of("baustein", "bausteintyp"))),
                Map.entry("gesellschaft_name", new AiColumnSpec("LAL.LU_GES_Text", "Ges. Name", "", List.of("gesellschaft", "ges name"))),

                // === Versioning ===
                Map.entry("hauptfaelligkeit", new AiColumnSpec("LAL.LU_HFL", "Hauptfälligkeit", "", List.of("hauptfälligkeit", "hauptfall"))),
                Map.entry("faelligkeit_von", new AiColumnSpec("LAL.LU_FLG", "Fälligkeit von", "", List.of("fälligkeit von", "version von"))),
                Map.entry("faelligkeit_bis", new AiColumnSpec("LAL.LU_FLZ", "Fälligkeit bis", "", List.of("fälligkeit bis", "version bis"))),
                Map.entry("version_status", new AiColumnSpec("LAL.LU_STATUS", "Version Status", "", List.of("version status", "v-status"))),


                // === Clerks (Sachbearbeiter) - Complex Fields ===
                Map.entry("sb_vertrag", new AiColumnSpec("(SELECT SB.LU_SB_VOR + ' ' + SB.LU_SB_NAM FROM SACHBEA SB WHERE LAL.LU_SACHBEA_VT = SB.LU_SB_KURZ)", "SB Vertrag", "", List.of("sb vertrag, SB Vertr."))),
                Map.entry("sb_schaden", new AiColumnSpec("(SELECT SB.LU_SB_VOR + ' ' + SB.LU_SB_NAM FROM SACHBEA SB WHERE LAL.LU_SACHBEA_SC = SB.LU_SB_KURZ)", "SB Schaden", "", List.of("sb schaden, SB Scha."))),
                Map.entry("sb_rechnung", new AiColumnSpec("(SELECT SB.LU_SB_VOR + ' ' + SB.LU_SB_NAM FROM SACHBEA SB WHERE LAL.LU_SACHBEA_RG = SB.LU_SB_KURZ)", "SB Rechnung", "", List.of("sb rechnung", "rechnung"))),
                Map.entry("sb_gl", new AiColumnSpec("(SELECT SB.LU_SB_VOR + ' ' + SB.LU_SB_NAM FROM SACHBEA SB WHERE LAL.LU_SACHBEA_GL = SB.LU_SB_KURZ)", "SB GL/Prokurist", "", List.of("sb gl", "prokurist"))),
                Map.entry("sb_buha", new AiColumnSpec("(SELECT SB.LU_SB_VOR + ' ' + SB.LU_SB_NAM FROM SACHBEA SB WHERE LAL.LU_SACHBEA_BUH = SB.LU_SB_KURZ)", "SB BuHa", "", List.of("sb buha", "buchhaltung")))

        );

        DYNAMIC_COVER_REPORT_TEMPLATE = new AiReportTemplate(
                "Dynamischer Cover-Bericht",
                List.of("verträge", "vertrag", "cover"),
                standardColumns,
                // The new, more powerful base query
                """
                        SELECT
                            {COLUMNS}
                        FROM LU_ALLE AS LAL
                            INNER JOIN LU_MASKEP AS LUM ON LAL.PPointer = LUM.PPointer
                            INNER JOIN VIASS005 AS V05 ON LUM.LU_NAT = V05.LU_INTKZ
                            INNER JOIN MAP_ALLE_STA AS MAS ON LAL.LU_STA = MAS.TAB_ID
                            INNER JOIN MAP_ALLE_COVERRIS AS MAC ON LAL.LU_BAUST_RIS = MAC.TAB_ID
                            INNER JOIN MAKLERV AS MAK ON LAL.LU_VMT = MAK.LU_VMTNR
                            INNER JOIN MAP_ALLE_BETSTAT AS MAB ON LAL.LU_BET_STAT = MAB.TAB_ID
                            INNER JOIN MAP_ALLE_OPZ AS MAO ON LAL.LU_OPZ = MAO.TAB_ID
                        WHERE LAL.Sparte LIKE '%COVER' AND {CONDITIONS}
                        """
        );
    }

    /**
     * Gibt alle "Cover"-bezogenen Berichtsvorlagen zurück.
     */
    @Override
    public List<AiReportTemplate> getReportTemplates() {
        return List.of(DYNAMIC_COVER_REPORT_TEMPLATE);
    }
}