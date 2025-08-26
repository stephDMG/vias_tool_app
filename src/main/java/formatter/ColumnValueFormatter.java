package formatter;

import model.RowData;

/**
 * Wie Ihr alter ColumnValueFormatter - einfach und funktional.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class ColumnValueFormatter {

    /**
     * Formatiert den Wert für die angegebene Spalte.
     * @param column Der Name der Spalte, für die der Wert formatiert werden soll.
     * @return Der formatierte Wert.
     */

    public static String format(RowData row, String column) {
        String value = DateFieldFormatter.tryFormat(column, row.getValues().get(column));
        value = MoneyFieldFormatter.tryFormat(column, value);
        return value == null ? "" : value;
    }
}