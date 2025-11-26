package formatter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import model.RowData;

import java.util.Map;
import java.util.Set;

/**
 * Zentraler Formatter für Tabellen-/TreeTable-Zellwerte.
 * <p>
 * - Behält bestehende Formatierung für Datum/Geldfelder bei.<br>
 * - Optionaler Modus "Voll. Name": zeigt für SB-Codes (z.B. NKI, CKÜ) den
 * vollständigen Namen aus dem SACHBEA-Dictionary an (Vorname + Nachname).
 * </p>
 *
 * <p><b>Nutzung:</b></p>
 * <ul>
 *   <li>Einmalig das SB-Dictionary setzen:
 *       {@link #setSbDictionary(Map)}</li>
 *   <li>Den UI-Toggle binden:
 *       {@link #bindFullNameMode(BooleanProperty)}</li>
 *   <li>In Table/Tree-Renderer immer {@link #format(RowData, String)} verwenden.</li>
 * </ul>
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class ColumnValueFormatter {

    /**
     * Globaler Toggle für "Voll. Name"-Anzeige (per Controller gebunden).
     */
    private static final BooleanProperty FULL_NAME_MODE = new SimpleBooleanProperty(false);
    /**
     * Spalten, die SB-Codes enthalten und bei "Voll. Name" in Klarnamen
     * aufgelöst werden sollen. <br>
     * <i>Diese Bezeichnungen müssen den Alias-Namen in deinem SELECT entsprechen.</i>
     */
    private static final Set<String> SB_HEADERS = Set.of(
            "SB_Vertr",       // COVER.LU_SACHBEA_VT  AS SB_Vertr
            "SB_Schad",       // COVER.LU_SACHBEA_SC  AS SB_Schad
            "SB_Rechnung",    // COVER.LU_SACHBEA_RG  AS SB_Rechnung
            "GL_Prokurist",   // COVER.LU_SACHBEA_GL  AS GL_Prokurist
            "SB_Doku",        // COVER.LU_SACHBEA_DOK AS SB_Doku
            "SB_BuHa",         // COVER.LU_SACHBEA_BUH AS SB_BuHa
            "Wiedervorlage_durch_1",
            "Storno_durch_1",
            "Wiedervorlage_durch_2",
            "Grund_durch",
            "Veranlasst_durch"
    );
    /**
     * Dictionary: SB-Code (LU_SB_KURZ) -> "Vorname Nachname".
     */
    private static Map<String, String> SB_DICT = Map.of();
    private static Set<String> ADDITIONAL_SB_HEADERS = Set.of();

    // Utility-Konstruktor verhindern
    private ColumnValueFormatter() {
    }

    /**
     * Bindet den globalen "Voll. Name"-Modus an eine UI-Property
     * (z. B. ToggleButton.selectedProperty()).
     *
     * @param prop BooleanProperty aus dem Controller/Model.
     */
    public static void bindFullNameMode(BooleanProperty prop) {
        if (prop != null) {
            FULL_NAME_MODE.unbind();
            FULL_NAME_MODE.bind(prop);
        }
    }

    public static void setAdditionalSbHeaders(Set<String> more) {
        ADDITIONAL_SB_HEADERS = (more == null) ? Set.of() : Set.copyOf(more);
    }

    private static boolean isSbHeader(String column) {
        return SB_HEADERS.contains(column) || ADDITIONAL_SB_HEADERS.contains(column);
    }

    /**
     * Setzt das SB-Dictionary (Code → "Vorname Nachname").
     * /**
     * Setzt das SB-Dictionary (Code → "Vorname Nachname").
     *
     * @param dict Map mit Schlüsseln = LU_SB_KURZ, Werten = "Vorname Nachname".
     */
    public static void setSbDictionary(Map<String, String> dict) {
        SB_DICT = (dict == null) ? Map.of() : dict;
    }

    /**
     * Formatiert den Zellwert für die angegebene Spalte.
     * <ol>
     *   <li>Wenn "Voll. Name" aktiv ist und die Spalte ein SB-Code-Feld ist,
     *       wird der Code über {@link #SB_DICT} in "Vorname Nachname" aufgelöst.</li>
     *   <li>Andernfalls werden wie bisher Datum- und Geldformat angewendet.</li>
     * </ol>
     *
     * @param row    Die aktuelle Zeile.
     * @param column Der Spaltenname (Alias im SELECT).
     * @return Formatierter Text (nie {@code null}).
     */
    public static String format(RowData row, String column) {
        if (row == null || column == null) return "";
        String raw = row.getValues().get(column);
        if (raw == null) raw = "";

        if (FULL_NAME_MODE.get() && isSbHeader(column)) {
            String code = raw.trim();
            if (!code.isEmpty()) {
                String full = SB_DICT.get(code);
                if (full != null && !full.isBlank()) return full;
            }
            return code;
        }
        String value = DateFieldFormatter.tryFormat(column, raw);
        value = MoneyFieldFormatter.tryFormat(column, value);
        return value == null ? "" : value;
    }

    public static String displayOnly(String header, String raw) {
        if (raw == null) raw = "";
        if (FULL_NAME_MODE.get() && isSbHeader(header)) {
            String code = raw.trim();
            if (!code.isEmpty()) {
                String full = SB_DICT.get(code);
                if (full != null && !full.isBlank()) return full;
            }
            return code;
        }
        return raw;
    }
}
