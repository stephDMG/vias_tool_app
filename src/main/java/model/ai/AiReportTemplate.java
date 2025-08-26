package model.ai;

import java.util.List;
import java.util.Map;

/**
 * Repräsentiert eine Vorlage für einen KI-gesteuerten Bericht.
 */
public class AiReportTemplate {
    private final String reportName;
    private final List<String> mainKeywords;
    private final Map<String, AiColumnSpec> availableColumns;
    private final String sqlTemplate;

    public AiReportTemplate(String reportName, List<String> mainKeywords, Map<String, AiColumnSpec> availableColumns, String sqlTemplate) {
        this.reportName = reportName;
        this.mainKeywords = mainKeywords;
        this.availableColumns = availableColumns;
        this.sqlTemplate = sqlTemplate;
    }

    public String getReportName() { return reportName; }
    public List<String> getMainKeywords() { return mainKeywords; }
    public Map<String, AiColumnSpec> getAvailableColumns() { return availableColumns; }
    public String getSqlTemplate() { return sqlTemplate; }
}