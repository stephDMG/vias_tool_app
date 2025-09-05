package model.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum DateField {

    SCHADENTAG("LU_SDA", "Schadentag"),
    MELDEDATUM("LU_MELDEDATUM", "Meldedatum"),
    IMPORTDATUM("LU_IMPORT_AM", "Importdatum"),
    TRANSPORTBEGINN("LU_STRABEG_DATUM", "Transportbeginn"),
    TRANSPORTENDE("LU_STRAEND_DATUM", "Transportende"),
    BEGINN("LU_BEG", "Beginn"),
    ENDE("LU_ABL", "Ablauf"),
    ;
    public static final Set<String> ALL_MATCHED_NAMES = Arrays.stream(values())
            .flatMap(d -> Arrays.stream(new String[]{d.dbColumn, d.alias}))
            .collect(Collectors.toSet());
    private final String dbColumn;
    private final String alias;

    DateField(String dbColumn, String alias) {
        this.dbColumn = dbColumn;
        this.alias = alias;
    }

    public static boolean isDateField(String columnName) {
        return ALL_MATCHED_NAMES.contains(columnName);
    }

    public String getDbColumn() {
        return dbColumn;
    }

    public String getAlias() {
        return alias;
    }
}