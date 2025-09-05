package model.enums;

/**
 * VIAS Tabellen-Typen.
 */
public enum TableType {
    SCHADEN("LU_SVA"),
    COVER("LU_ALLE"),
    MASK("LU_MASKP"),
    DOKUMENT_LINK("DCPL0300"),
    DOKUMENT("DCPL0200");


    private final String tableName;

    TableType(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }
}