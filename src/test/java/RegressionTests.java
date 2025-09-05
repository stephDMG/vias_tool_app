import model.ai.AiReportTemplate;
import model.ai.ir.Predicate;
import model.ai.ir.Projection;
import model.ai.ir.QueryIR;
import model.ai.nlp.NLParser;
import model.ai.planning.CoverPlanner;
import model.ai.planning.SqlPlan;
import model.ai.provider.impl.CoverKnowledgeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegressionTests {

    private NLParser parser;
    private CoverPlanner planner;

    @BeforeEach
    void setUp() {
        parser = new NLParser();
        AiReportTemplate template = new CoverKnowledgeProvider().getReportTemplates().get(0);
        planner = new CoverPlanner(template);
    }

    // =========================================================================
    // === 1. PARSING-FEHLER (NLParser) ========================================
    // =========================================================================

    @Test
    void testMaklerIdFilterIsCorrectlyParsed() {
        String prompt = "Verträge für Makler 100120";
        QueryIR ir = parser.parse(prompt);
        assertEquals(1, ir.filters.get(0).predicates.size(), "Es sollte nur ein Filter gefunden werden.");
        Predicate p = ir.filters.get(0).predicates.get(0);
        assertEquals("makler_nr", p.field, "Das Feld sollte korrekt zu 'makler_nr' aufgelöst werden.");
        assertEquals("100120", p.value);
    }

    @Test
    void testSimpleProjectionKeywordsAreCorrectlyParsed() {
        String prompt = "Verträge mit Feldern vsn, makler nr, partner-typ";
        QueryIR ir = parser.parse(prompt);
        assertEquals(3, ir.projections.size(), "Es sollten 3 Projektionen gefunden werden.");
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("vsn")));
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("makler_nr")));
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("partner_typ")));
    }

    @Test
    void testComplexProjectionsAreCorrectlyParsed() {
        String prompt = "Verträge für Makler 100120 mit Feldern Firma, Land zuerst VSN";
        QueryIR ir = parser.parse(prompt);
        assertEquals(3, ir.projections.size(), "Es sollten 3 Projektionen gefunden werden.");
        Projection vsnProj = ir.projections.stream().filter(p -> p.field.equals("vsn")).findFirst().orElse(null);
        assertNotNull(vsnProj, "VSN-Projektion sollte existieren.");
        assertEquals(0, vsnProj.order, "VSN sollte als 'zuerst' markiert sein.");
    }

    // =========================================================================
    // === 2. PLANUNGS-FEHLER (CoverPlanner) ===================================
    // =========================================================================

    @Test
    void testMaklerNameFilterIsCorrectlyPlanned() {
        String prompt = "Verträge für Makler Name Gründemann*";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE UPPER(?)"), "SQL sollte den Makler-Namen-Filter enthalten.");
        assertEquals("gründemann%", plan.params.get(0), "SQL-Parameter sollte korrekt sein.");
    }

    @Test
    void testWhitelistProjectionsAreCorrectlyPlanned() {
        String prompt = "Verträge für Makler 100120 mit Feldern Firma, Land";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        String expectedFirma = "COALESCE(RTRIM(LTRIM(LUM.LU_NAM)), '') AS \"Firma/Name\"";
        String expectedLand = "COALESCE(RTRIM(LTRIM(V05.LU_LANDNAME)), '') AS \"Land\"";

        assertTrue(plan.sql.contains(expectedFirma), "SQL sollte die Spalte 'Firma' enthalten.");
        assertTrue(plan.sql.contains(expectedLand), "SQL sollte die Spalte 'Land' enthalten.");
        assertFalse(plan.sql.contains("AS \"VSN\""), "SQL sollte die Spalte 'VSN' nicht enthalten.");
    }

    @Test
    void testOrderByClauseIsCorrectlyPlanned() {
        String prompt = "Verträge für Makler 100120 order by Makler Name desc";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("ORDER BY MAK.LU_NAM DESC"), "SQL sollte die korrekte ORDER BY-Klausel enthalten.");
    }

    // =========================================================================
    // === 3. TESTS FÜR IHRE PROMPTS ============================================
    // =========================================================================

    @Test
    void testKombinierteKlauselnMaklerDatum() {
        String prompt = "Verträge für Makler 100120 mit Beginn zwischen 01.01.2023 und 31.12.2023";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("RTRIM(LTRIM(LAL.LU_VMT)) = ?"), "Makler-Filter fehlt.");
        // Korrektur: Prüft auf Platzhalter und die params-Liste
        assertTrue(plan.sql.contains("LAL.LU_BEG BETWEEN ? AND ?"), "Datumsfilter fehlt.");
        assertEquals("20230101", plan.params.get(1));
        assertEquals("20231231", plan.params.get(2));
    }

    @Test
    void testKombinierteKlauselnStatusLand() {
        String prompt = "Verträge mit Status A und aus Land DEU";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        // Korrektur: Prüft auf Platzhalter und die params-Liste
        assertTrue(plan.sql.contains("RTRIM(LTRIM(LAL.LU_STA)) = ?"), "Status-Filter fehlt.");
        assertTrue(plan.sql.contains("(RTRIM(LTRIM(V05.LU_LANDNAME)) = ? OR RTRIM(LTRIM(LUM.LU_NAT)) = ?)"), "Land-Filter fehlt.");
        assertEquals("DEU", plan.params.get(0));
        assertEquals("DEU", plan.params.get(1));
        assertEquals("A", plan.params.get(2));
    }

    @Test
    void testKombinierteKlauselnMaklerLimit() {
        String prompt = "Verträge für Makler 100120 limit 500";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("RTRIM(LTRIM(LAL.LU_VMT)) = ?"), "Makler-Filter fehlt.");
        assertTrue(plan.sql.contains("LIMIT ?"), "Limit-Klausel fehlt.");
        assertEquals(500, plan.params.get(1));
    }

    @Test
    void testKomplexerSpaltenTestMitZuerst() {
        String prompt = "Verträge für Makler 100120 mit Feldern Firma, Land zuerst VSN";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("COALESCE(RTRIM(LTRIM(LAL.LU_VSN)), '') AS \"VSN\""), "VSN-Spalte fehlt.");
        assertTrue(plan.sql.contains("COALESCE(RTRIM(LTRIM(LUM.LU_NAM)), '') AS \"Firma/Name\""), "Firma-Spalte fehlt.");
        assertTrue(plan.sql.contains("COALESCE(RTRIM(LTRIM(V05.LU_LANDNAME)), '') AS \"Land\""), "Land-Spalte fehlt.");
        assertTrue(plan.sql.contains("RTRIM(LTRIM(LAL.LU_VMT)) = ?"), "Makler-Filter fehlt.");
    }
}