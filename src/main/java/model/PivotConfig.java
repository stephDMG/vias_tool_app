package model;

import java.util.List;

/**
 * Konfiguration für Pivot-Transformation.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class PivotConfig {

    private final String groupByColumn;
    private final String pivotColumn;
    private final List<String> keepColumns;

    public PivotConfig(String groupByColumn, String pivotColumn, List<String> keepColumns) {
        this.groupByColumn = groupByColumn;
        this.pivotColumn = pivotColumn;
        this.keepColumns = keepColumns;
    }

    public String getGroupByColumn() { return groupByColumn; }
    public String getPivotColumn() { return pivotColumn; }
    public List<String> getKeepColumns() { return keepColumns; }

    // Factory method für Dokumente
    public static PivotConfig forDocuments() {
        return new PivotConfig("Schaden Nr", "Dokument", List.of("Import ID"));
    }
}