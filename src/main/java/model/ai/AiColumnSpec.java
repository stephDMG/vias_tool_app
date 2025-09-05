package model.ai;

import java.util.List;

/**
 * Definiert die Spezifikationen einer Spalte für die KI-Abfragegenerierung.
 * NEU: Generiert die SQL-Syntax jetzt selbst, um die AiKnowledgeBase sauber zu halten.
 *
 * @author Coding-Assistent
 * @version 2.0 (Refactored)
 */
public class AiColumnSpec {
    private final String dbColumn;
    private final String columnAlias;
    private final String tableAlias;
    private final List<String> keywords;
    private final boolean isNumeric;

    // Hauptkonstruktor
    public AiColumnSpec(String dbColumn, String columnAlias, String tableAlias, List<String> keywords, boolean isNumeric) {
        this.dbColumn = dbColumn;
        this.columnAlias = columnAlias;
        this.tableAlias = tableAlias;
        this.keywords = keywords;
        this.isNumeric = isNumeric;
    }

    // Vereinfachter Konstruktor für Text-Spalten (der häufigste Fall)
    public AiColumnSpec(String dbColumn, String columnAlias, String tableAlias, List<String> keywords) {
        this(dbColumn, columnAlias, tableAlias, keywords, false);
    }

    public String getDbColumn() {
        return dbColumn;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public final boolean isNumeric() {
        return isNumeric;
    }

    public final String getTableAlias() {
        return tableAlias;
    }

    public final String getColumnAlias() {
        return columnAlias;
    }

    /**
     * Baut die vollständige SQL-Definition für die SELECT-Klausel dynamisch zusammen.
     *
     * @return Die formatierte SQL-Spaltendefinition (z.B. "COALESCE(...) AS 'Alias'").
     */
    public String getSqlDefinition() {
        String db = dbColumn == null ? "" : dbColumn.trim();
        String expr;

        if (db.startsWith("(")) {
            // Complex expression/subselect passed as-is
            expr = db;
        } else if (db.contains(".")) {
            // Already fully qualified like "LAL.LU_VSN"
            expr = db;
        } else if (tableAlias != null && !tableAlias.isBlank()) {
            // Qualify with table alias if provided
            expr = tableAlias + "." + db;
        } else {
            // Fallback: bare column name
            expr = db;
        }

        if (isNumeric) {
            return expr + " AS \"" + columnAlias + "\"";
        } else {
            // Zen SQL-friendly trimming on text
            return "COALESCE(RTRIM(LTRIM(" + expr + ")), '') AS \"" + columnAlias + "\"";
        }
    }


    public boolean matches(String word) {
        return keywords.contains(word.toLowerCase());
    }


}