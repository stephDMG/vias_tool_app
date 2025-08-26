// DateFieldFormatter.java
package formatter;

import config.ApplicationConfig;
import gui.controller.utils.format.FormatterService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * FieldFormatter für Datumsfelder basierend auf ApplicationConfig-Formaten.
 * Thread-sicher und immutable.
 *
 * @author Stephane
 * @since 15/07/2025
 */
public class DateFieldFormatter {

    private static final DateTimeFormatter INPUT_FMT  = ApplicationConfig.DATE_INPUT;
    private static final DateTimeFormatter OUTPUT_FMT = ApplicationConfig.DATE_OUTPUT;

    public static String tryFormat(String column, String value) {
        if (value == null ) {
            return value;
        }

        if(FormatterService.isDateField(column) && isLikelyDate(value)) {
            return reformat(value);
        }

        return value;
    }

    private static boolean isLikelyDate(String value) {
        try {
            LocalDate.parse(value, INPUT_FMT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static String reformat(String value) {
        try {
            LocalDate date = LocalDate.parse(value, INPUT_FMT);
            return date.format(OUTPUT_FMT);
        } catch (DateTimeParseException e) {
            return value;
        }
    }
}