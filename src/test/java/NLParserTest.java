import model.ai.AiReportTemplate;
import model.ai.ir.*;
import model.ai.nlp.NLParser;
import model.ai.planning.CoverPlanner;
import model.ai.planning.SqlPlan;
import model.ai.register.AiKnowledgeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class NLParserTest {

    private NLParser parser;

    @BeforeEach
    void setUp() {
        parser = new NLParser();
    }

    @Test
    void testSimpleMaklerName() {
        QueryIR ir = parser.parse("COVER für Makler Name Gründemann");
        assertEquals(ContextType.COVER, ir.context, "Der Kontext sollte COVER sein.");
        assertEquals(1, ir.filters.get(0).predicates.size(), "Es sollte ein Filter gefunden werden.");
        Predicate p = ir.filters.get(0).predicates.get(0);
        assertEquals("makler name", p.field);
        assertEquals(Op.LIKE, p.op);
        assertEquals("gründemann", p.value);
    }

    @Test
    void testVsnFilter1() {
        QueryIR ir = parser.parse("COVER mit VSN 12345678");
        assertEquals(ContextType.COVER, ir.context, "Der Kontext sollte COVER sein.");
        assertEquals(1, ir.filters.get(0).predicates.size(), "Es sollte ein VSN-Filter gefunden werden.");
    }

    @Test
    void testExcludeProjections() {
        QueryIR ir = parser.parse("Verträge für Makler 100120 außer Firma, Land");
        assertEquals(ContextType.COVER, ir.context, "Der Kontext sollte COVER sein.");
        assertEquals(1, ir.filters.get(0).predicates.size(), "Es sollte ein Makler-ID-Filter gefunden werden.");
        assertEquals(2, ir.projections.size(), "Es sollten zwei ausgeschlossene Projektionen gefunden werden.");
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("firma") && p.exclude));
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("land") && p.exclude));
    }

    @Test
    void testOrderBy() {
        QueryIR ir = parser.parse("COVER für Makler Name Gründemann order by Firma asc, Land desc");
        assertEquals(ContextType.COVER, ir.context, "Der Kontext sollte COVER sein.");
        assertEquals(1, ir.filters.get(0).predicates.size(), "Es sollte ein Makler-Name-Filter gefunden werden.");
        assertEquals(2, ir.sortOrders.size(), "Es sollten zwei Sortierkriterien gefunden werden.");

        Sort sort1 = ir.sortOrders.get(0);
        assertEquals("firma", sort1.field);
        assertEquals(Direction.ASC, sort1.direction);

        Sort sort2 = ir.sortOrders.get(1);
        assertEquals("land", sort2.field);
        assertEquals(Direction.DESC, sort2.direction);
    }

    @Test
    void testMaklerIdPredicateFieldIsCorrect() {
        QueryIR ir = parser.parse("Verträge für Makler 100120");
        assertEquals(1, ir.filters.get(0).predicates.size());
        Predicate p = ir.filters.get(0).predicates.get(0);
        // Erwartet "makler_nr", bekommt aber "makler id"
        assertEquals("makler_nr", p.field);
    }

    @Test
    void testComplexPromptWithFiltersProjectionsAndSorting() {
        String prompt = "Verträge für Makler Name Gründemann* mit Feldern Firma, Land zuerst VSN order by Firma desc";
        QueryIR ir = parser.parse(prompt);

        // 1. Überprüfe den Filter
        assertEquals(1, ir.filters.get(0).predicates.size(), "Es sollte genau ein Makler-Name-Filter gefunden werden.");
        Predicate filter = ir.filters.get(0).predicates.get(0);
        assertEquals("makler name", filter.field);
        assertEquals(Op.LIKE, filter.op);
        assertEquals("gründemann%", filter.value);

        // 2. Überprüfe die Projektionen (Spaltenauswahl)
        assertEquals(3, ir.projections.size(), "Es sollten 3 Projektionen (Firma, Land, VSN) gefunden werden.");
        Projection firstProj = ir.projections.stream().filter(p -> p.field.equals("vsn")).findFirst().orElse(null);
        assertNotNull(firstProj, "VSN sollte als Projektion gefunden werden.");
        assertEquals(0, firstProj.order, "VSN sollte als 'zuerst' markiert sein.");

        // 3. Überprüfe die Sortierung
        assertEquals(1, ir.sortOrders.size(), "Es sollte genau ein Sortierkriterium gefunden werden.");
        Sort sortOrder = ir.sortOrders.get(0);
        assertEquals("firma", sortOrder.field);
        assertEquals(Direction.DESC, sortOrder.direction);
    }

    @Test
    void testComplexPrompt_IRUnderstanding() {
        String prompt = "Verträge für Makler Name Gründemann* mit Feldern Firma, Land zuerst VSN order by Firma desc";
        QueryIR ir = parser.parse(prompt);

        // 1. Überprüfe den Kontext
        assertEquals(ContextType.COVER, ir.context);

        // 2. Überprüfe den Filter
        assertEquals(1, ir.filters.get(0).predicates.size(), "Es sollte genau ein Filter gefunden werden.");
        Predicate filter = ir.filters.get(0).predicates.get(0);
        assertEquals("makler name", filter.field);
        assertEquals(Op.LIKE, filter.op);
        assertEquals("gründemann%", filter.value);

        // 3. Überprüfe die Projektionen (Spaltenauswahl)
        assertEquals(3, ir.projections.size(), "Es sollten 3 Projektionen (Firma, Land, VSN) gefunden werden.");
        Projection vsnProj = ir.projections.stream().filter(p -> p.field.equals("vsn")).findFirst().orElse(null);
        assertNotNull(vsnProj, "VSN sollte als Projektion gefunden werden.");
        assertEquals(0, vsnProj.order, "VSN sollte als 'zuerst' markiert sein.");
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("firma") && !p.exclude));
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("land") && !p.exclude));

        // 4. Überprüfe die Sortierung
        assertEquals(1, ir.sortOrders.size(), "Es sollte genau ein Sortierkriterium gefunden werden.");
        Sort sortOrder = ir.sortOrders.get(0);
        assertEquals("firma", sortOrder.field);
        assertEquals(Direction.DESC, sortOrder.direction);
    }

    @Test
    void testComplexPrompt_SQLExecution() {
        // Annahme: Der NLParser funktioniert korrekt
        String prompt = "Verträge für Makler Name Gründemann* mit Feldern Firma, Land zuerst VSN order by Firma desc";
        QueryIR ir = parser.parse(prompt);

        // Initialisiere den CoverPlanner (mit der Annahme, dass das Template korrekt geladen wird)
        AiReportTemplate template = AiKnowledgeRegistry.getAllTemplates().get(0);
        CoverPlanner planner = new CoverPlanner(template);

        // Führe die Planung aus
        SqlPlan plan = planner.fromIR(ir);
        String sql = plan.sql;

        // 1. Überprüfe die SELECT-Klausel (nur die angeforderten Felder sollten vorhanden sein)
        assertTrue(sql.contains("COALESCE(RTRIM(LTRIM(LUM.LU_NAM)), '') AS \"Firma/Name\""), "SELECT-Klausel enthält nicht die Spalte 'Firma/Name'.");
        assertTrue(sql.contains("COALESCE(RTRIM(LTRIM(V05.LU_LANDNAME)), '') AS \"Land\""), "SELECT-Klausel enthält nicht die Spalte 'Land'.");
        assertTrue(sql.contains("COALESCE(RTRIM(LTRIM(LAL.LU_VSN)), '') AS \"VSN\""), "SELECT-Klausel enthält nicht die Spalte 'VSN'.");

        // 2. Überprüfe den WHERE-Filter
        String expectedWhere = "WHERE LAL.Sparte LIKE '%COVER' AND UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE UPPER(?)";
        assertTrue(sql.contains(expectedWhere), "WHERE-Klausel enthält nicht den Makler-Name-Filter.");
        assertEquals("gründemann%", plan.params.get(0), "Parameter sollte 'gründemann%' sein.");

        // 3. Überprüfe die ORDER BY-Klausel
        String expectedOrderBy = "ORDER BY LUM.LU_NAM DESC";
        assertTrue(sql.contains(expectedOrderBy), "ORDER BY-Klausel ist falsch oder fehlt.");
    }

    @Test
    void testCombinedFilters() {
        QueryIR ir = parser.parse("COVER mit Status a und aus Land Deu");
        assertEquals(ContextType.COVER, ir.context, "Der Kontext sollte COVER sein.");
        assertEquals(2, ir.filters.get(0).predicates.size(), "Es sollten zwei Filter (Status und Land) gefunden werden.");
    }


    // =========================================================================
    // === 1. TESTEN DER REGEX-BASIERTEN FILTER-KLAUSELN =======================
    // =========================================================================

    @Test
    void testMaklerIdFilter() {
        QueryIR ir = parser.parse("Verträge für makler id 100120");
        assertEquals(ContextType.COVER, ir.context);
        assertEquals(1, ir.filters.get(0).predicates.size());
        Predicate p = ir.filters.get(0).predicates.get(0);
        assertEquals("makler_nr", p.field);
        assertEquals(Op.EQUALS, p.op);
        assertEquals("100120", p.value);
    }

    @Test
    void testMaklerNameFilter() {
        QueryIR ir = parser.parse("Verträge für Makler Name Gründemann");
        assertEquals(ContextType.COVER, ir.context);
        assertEquals(1, ir.filters.get(0).predicates.size());
        Predicate p = ir.filters.get(0).predicates.get(0);
        assertEquals("makler name", p.field);
        assertEquals(Op.LIKE, p.op);
        assertEquals("gründemann", p.value);
    }

    @Test
    void testVsnFilter() {
        QueryIR ir = parser.parse("Verträge mit VSN 12345678");
        assertEquals(ContextType.COVER, ir.context);
        assertEquals(1, ir.filters.get(0).predicates.size());
        Predicate p = ir.filters.get(0).predicates.get(0);
        assertEquals("vsn", p.field);
        assertEquals(Op.EQUALS, p.op);
        assertEquals("12345678", p.value);
    }

    @Test
    void testLandAndStatusFilters() {
        QueryIR ir = parser.parse("Verträge mit Status A und aus Land DEU");
        assertEquals(ContextType.COVER, ir.context);
        assertEquals(2, ir.filters.get(0).predicates.size());
        // Der Rest des Tests würde die einzelnen Prädikate überprüfen
    }

    // =========================================================================
    // === 2. TESTEN DER KEYWORD-VERARBEITUNG IM PROJEKTIONS-BLOCK =============
    // =========================================================================

    @Test
    void testProjectionKeywords() {
        // Der Prompt enthält eine Mischung aus Einzelwörtern, mehrwortigen Keywords und Keywords mit Bindestrichen
        String prompt = "Verträge mit Feldern vsn, makler nr, partner-typ, sb gl";
        QueryIR ir = parser.parse(prompt);

        assertEquals(4, ir.projections.size(), "Es sollten 4 Projektionen gefunden werden.");

        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("makler_nr")));
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("partner_typ")));
        assertTrue(ir.projections.stream().anyMatch(p -> p.field.equals("sb gl")));

    }
}

