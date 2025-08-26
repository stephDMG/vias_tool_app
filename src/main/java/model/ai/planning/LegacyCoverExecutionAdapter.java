package model.ai.planning;

import service.interfaces.AiQueryBuilder;

public class LegacyCoverExecutionAdapter {
    private final AiQueryBuilder legacyBuilder;

    public LegacyCoverExecutionAdapter(AiQueryBuilder legacyBuilder) {
        this.legacyBuilder = legacyBuilder;
    }

    // Wichtig: Diese Methode generiert SQL aus einem einfachen String
    public SqlPlan planFromLegacy(String prompt) {
        SqlPlan p = new SqlPlan();
        p.sql = legacyBuilder.generateQuery(prompt);
        // Hinweis: Parameter werden hier nicht unterstützt, da der alte Builder sie nicht liefert.
        return p;
    }
}