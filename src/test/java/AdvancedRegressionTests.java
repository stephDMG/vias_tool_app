import model.ai.AiReportTemplate;
import model.ai.ir.*;
import model.ai.nlp.NLParser;
import model.ai.planning.CoverPlanner;
import model.ai.planning.SqlPlan;
import model.ai.provider.impl.CoverKnowledgeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedRegressionTests {

    private NLParser parser;
    private CoverPlanner planner;

    @BeforeEach
    void setUp() {
        parser = new NLParser();
        AiReportTemplate template = new CoverKnowledgeProvider().getReportTemplates().get(0);
        planner = new CoverPlanner(template);
    }

    // =========================================================================
    // === 1. TESTS FÜR DATUMSFILTER ===========================================
    // =========================================================================

    @Test
    void testBeginnFilterAfter() {
        String prompt = "Verträge mit Beginn nach 01.01.2024";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("LAL.LU_BEG >= ?"), "Filter für 'Beginn nach' fehlt.");
        assertEquals("20240101", plan.params.get(0));
    }

    @Test
    void testBeginnFilterBefore() {
        String prompt = "Verträge mit Beginn vor 31.12.2023";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("LAL.LU_BEG <= ?"), "Filter für 'Beginn vor' fehlt.");
        assertEquals("20231231", plan.params.get(0));
    }

    @Test
    void testAblaufFilterBetween() {
        String prompt = "Verträge mit Ablauf zwischen 01.01.2024 und 31.12.2024";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);

        assertTrue(plan.sql.contains("LAL.LU_ABL BETWEEN ? AND ?"), "Filter für 'Ablauf zwischen' fehlt.");
        assertEquals("20240101", plan.params.get(0));
        assertEquals("20241231", plan.params.get(1));
    }

    // =========================================================================
    // === 2. TEST FÜR MEHRERE SORTIERKRITERIEN =================================
    // =========================================================================

    @Test
    void testMultipleOrderByClauses() {
        String prompt = "Verträge für Makler 100120 order by Firma asc, Land desc";
        QueryIR ir = parser.parse(prompt);
        SqlPlan plan = planner.fromIR(ir);
        assertTrue(plan.sql.contains("ORDER BY LUM.LU_NAM ASC, V05.LU_LANDNAME DESC"), "Mehrfache Sortierung ist fehlerhaft.");
    }
}