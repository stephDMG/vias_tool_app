package model.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum BeteiligteApply {

    SUBUNTERNEHMER("LU_SAST", "Subunternehmer"),
    VERURSACHER   ("LU_VURS", "Verursacher"),
    ANSPRUCHSTELLER("LU_AST", "Anspruchsteller"),
    GESCHAEDIGTER ("LU_GESCH", "Geschädigter");

    private final String prefix;
    private final String alias;

    BeteiligteApply(String prefix, String alias) {
        this.prefix = prefix;
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Gibt den SQL-Ausdruck für diesen spezifischen Beteiligten zurück.
     * @return Ein String, der die formatierte CASE-Anweisung enthält.
     */
    public String sqlFormattedCase() {
        return String.format("""
            CASE
              WHEN %1$s_PNR IS NULL OR %1$s_PNR = ''
                THEN 'Kein'
              ELSE RTRIM(LTRIM(%1$s_PNR)) + ' - ' +
                   COALESCE(RTRIM(LTRIM(%1$s_NAM)) + ' ', '') +
                   COALESCE(RTRIM(LTRIM(%1$s_VOR)) + ' ', '') +
                   COALESCE(RTRIM(LTRIM(%1$s_NA2)), '')
            END AS "%2$s"
            """, this.prefix, this.alias);
    }

    /**
     * Generiert einen kombinierten SQL-String mit CASE-Anweisungen für alle Beteiligten-Typen.
     * @return Ein String, der alle CASE-Anweisungen durch Kommas getrennt enthält.
     */
    public static String generateAllSqlCases() {
        return Arrays.stream(values())
                .map(BeteiligteApply::sqlFormattedCase)
                .collect(Collectors.joining(",\n                "));
    }
}
