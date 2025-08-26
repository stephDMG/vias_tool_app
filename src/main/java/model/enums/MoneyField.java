package model.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum MoneyField {
    RESTRESERVE("LU_RESTRESERVE", "Restreserve"),
    ZAHLUNGEN_100("LU_ZAHG_100_SA", "Zahlungen 100"),
    ZAHLUNGEN_ANTEIL_CS("LU_ZAHG_ZE_SA", "Zahlungen Anteil CS"),
    MAXJAHRESLEISTUNG("LU_VHV_SUM_216", "Max Jahresleistung"),
    MAXSCHADENLEISTUNG("LU_VHV_SUM_205", "Max Schadenleistung"),
    ANTEIL_CS("LU_ANTEIL_CS", "Anteil CS"),
    RESTRESERVE_SVA("LU_RESTRESERVE", "Reserve (Anteil CS)"),
    BEZAHLTE_SCHAEDEN("LU_ZAHG_ZE_SA", "Bezahlte Schäden (Anteil CS)"),
    GESAMT("LU_RESTRESERVE + LU_ZAHG_ZE_SA", "Gesamt"),

    PRAEMIE_ERWM_1("LU_PRAEMIE_ERWM_1", "Prämie ERWM 1"),
    PRAEMIE_ERWM_2("LU_PRAEMIE_ERWM_2", "Prämie ERWM 2"),
    MIN_PRAEMIE_NETTO("LU_MIN_PRNET", "Min. Prämie Netto"),
    VSP_PRAEMIE_NETTO("LU_VSP_PRNET", "VSP Prämie Netto"),
    JAHRESPRAEMIE_NETTO("LU_JPR_PRNET", "Jahresprämie Netto"),
    ZAHLUNGSBETRAG("LU_ZBETRAG", "Zahlungen"),

    ZAHLUNGEN("Zahlungen", "Zahlungen"),
    ;

    private final String dbColumn;
    private final String alias;

    MoneyField(String dbColumn, String alias) {
        this.dbColumn = dbColumn;
        this.alias = alias;
    }

    public String getDbColumn() {
        return dbColumn;
    }

    public String getAlias() {
        return alias;
    }

    public static final Set<String> ALL_MATCHED_NAMES = Arrays.stream(values())
            .flatMap(d -> Arrays.stream(new String[]{d.dbColumn, d.alias}))
            .collect(Collectors.toSet());

    public static boolean isMoneyField(String columnName) {
        return ALL_MATCHED_NAMES.contains(columnName);
    }
}
