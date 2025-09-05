import model.ai.AiReportTemplate;
import model.ai.provider.impl.CoverKnowledgeProvider;
import model.ai.register.AiKnowledgeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import service.impl.CoverQueryBuilder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoverQueryBuilderTest {

    private static CoverQueryBuilder builder;

    @BeforeAll
    static void setup() {
        Locale.setDefault(Locale.GERMANY);
        builder = new CoverQueryBuilder();
        assertFalse(AiKnowledgeRegistry.getAllTemplates().isEmpty());
        boolean hasCover = new CoverKnowledgeProvider().getReportTemplates().stream()
                .map(AiReportTemplate::getReportName).anyMatch(n -> n.toLowerCase().contains("cover"));
        assertTrue(hasCover);
    }

    // ---------- 1) Makler Name avec % ----------
    @Test
    void shouldBuildWhereForMaklerNameWithPercent() {
        String sql = builder.generateQuery("Verträge für Makler Name Gründemann%");

        assertTrue(sql.contains("LAL.Sparte LIKE '%COVER'"));
        assertTrue(sql.toUpperCase().contains("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE 'GRÜNDEMANN%'"));
    }


    // ---------- 2) VSN ----------
    @Test
    void shouldFilterByVSN() {
        String sql = builder.generateQuery("Verträge mit VSN 12345678");
        assertTrue(sql.contains("RTRIM(LTRIM(LAL.LU_VSN)) = '12345678'"));
    }

    // ---------- 3) Makler-ID ----------
    @Test
    void shouldFilterByMaklerId() {
        String sql = builder.generateQuery("Verträge mit Makler 100438");
        assertTrue(sql.contains("RTRIM(LTRIM(LAL.LU_VMT)) = '100438'"));
    }

    // ---------- 4) Land / Land Code + ne pas avaler les champs ----------
    @Test
    void shouldFilterByLandAndKeepFieldsBlock() {
        String sql = builder.generateQuery("Verträge für Land Deutschland mit der Feldern strasse, name und land code");
        assertTrue(sql.contains("(RTRIM(LTRIM(V05.LU_LANDNAME)) = 'DEUTSCHLAND'"));
        assertTrue(sql.contains("OR RTRIM(LTRIM(LUM.LU_NAT)) = 'DEUTSCHLAND')"));
        // colonnes attendues (include-mode)
        assertTrue(sql.contains("AS \"Strasse\""));
        assertTrue(sql.contains("AS \"Firma/Name\""));
        assertTrue(sql.contains("AS \"Land Code\""));
        // et pas de colonnes “au hasard” (spot-check)
        assertFalse(sql.contains("AS \"Vertragsstatus\""));
    }

    // ---------- 5) Status + Land code ----------
    @Test
    void shouldFilterByStatusAndLandCode() {
        String sql = builder.generateQuery("Verträge mit Status a und aus Land code Deu mit den Feldern land");
        assertTrue(sql.contains("RTRIM(LTRIM(LAL.LU_STA)) = 'A'"));
        assertTrue(sql.contains("(RTRIM(LTRIM(V05.LU_LANDNAME)) = 'DEU'"));
        assertTrue(sql.contains("OR RTRIM(LTRIM(LUM.LU_NAT)) = 'DEU')"));
        // seulement Land en SELECT
        assertTrue(sql.contains("V05.LU_LANDNAME"));
        assertFalse(sql.contains("LUM.LU_NAM"));  // Firma/Name absent
    }

    // ---------- 6) Dates: Beginn ----------
    @Test
    void shouldFilterBeginnAfter() {
        String sql = builder.generateQuery("Verträge mit Beginn nach 01.01.2024");
        assertTrue(sql.contains("LAL.LU_BEG >= '20240101'"));
    }

    @Test
    void shouldFilterBeginnBefore() {
        String sql = builder.generateQuery("Verträge mit Beginn vor 31.12.2023");
        assertTrue(sql.contains("LAL.LU_BEG <= '20231231'"));
    }

    @Test
    void shouldFilterBeginnBetweenAndOtherFilters() {
        String sql = builder.generateQuery("Verträge mit Beginn zwischen 01.01.2023 und 31.12.2023 und mit status a und in Land Deutschland und zwar Land, vsn, Stand, anfang und beginn");
        assertTrue(sql.contains("LAL.LU_BEG BETWEEN '20230101' AND '20231231'"));
        assertTrue(sql.contains("RTRIM(LTRIM(LAL.LU_STA)) = 'A'"));
        assertTrue(sql.contains("(RTRIM(LTRIM(V05.LU_LANDNAME)) = 'DEUTSCHLAND'"));
        assertTrue(sql.contains("OR RTRIM(LTRIM(LUM.LU_NAT)) = 'DEUTSCHLAND')"));
        // include-mode: Land, VSN, Vertragsstand, Beginn
        assertTrue(sql.contains("AS \"Land\""));
        assertTrue(sql.contains("AS \"VSN\""));
        assertTrue(sql.contains("AS \"Vertragsstand\""));
        assertTrue(sql.contains("AS \"Beginn\""));
    }

    // ---------- 7) Dates: Ablauf ----------
    @Test
    void shouldFilterAblaufAfter() {
        String sql = builder.generateQuery("Verträge mit Ablauf nach 01.01.2025");
        assertTrue(sql.contains("LAL.LU_ABL >= '20250101'"));
    }

    @Test
    void shouldFilterAblaufBefore() {
        String sql = builder.generateQuery("Verträge mit Ablauf vor 31.12.2024");
        assertTrue(sql.contains("LAL.LU_ABL <= '20241231'"));
    }

    @Test
    void shouldFilterAblaufBetween() {
        String sql = builder.generateQuery("Verträge mit Ablauf zwischen 01.01.2024 und 31.12.2024");
        assertTrue(sql.contains("LAL.LU_ABL BETWEEN '20240101' AND '20241231'"));
    }

    // ---------- 8) Include-mode ----------
    @Test
    void shouldIncludeOnlyAskedFields() {
        String sql = builder.generateQuery("Verträge für Makler Name Gründemann% mit Feldern Firma, Land");
        assertTrue(sql.toUpperCase().contains("LIKE 'GRÜNDEMANN%'"));
        assertTrue(sql.contains("AS \"Firma/Name\""));
        assertTrue(sql.contains("AS \"Land\""));
        // ne pas ramener tout
        assertFalse(sql.contains("AS \"Vertragsstatus\""));
    }

    // ---------- 9) Cas flous -> message KI (legacy) ----------
    @Test
    void shouldReturnHelpfulMessageWhenNoStrongFilter() {
        String sql = builder.generateQuery("alle cover");
        assertTrue(sql.startsWith("-- LOKALER KI-FEHLER"), "Doit renvoyer un message d'aide quand aucun filtre fort n'est trouvé.");
    }
}
