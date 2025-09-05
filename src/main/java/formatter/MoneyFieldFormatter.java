package formatter;

import config.ApplicationConfig;
import gui.controller.utils.format.FormatterService;

import java.math.BigDecimal;
import java.text.NumberFormat;


/**
 * FieldFormatter für Geldbeträge basierend auf ApplicationConfig-MONEY_FORMAT.
 *
 * @author Stephane
 * @since 15/07/2025
 */
public class MoneyFieldFormatter {

    private static final NumberFormat MONEY_FMT = ApplicationConfig.MONEY_FORMAT;

    public static String tryFormat(String column, String value) {
        if (!FormatterService.isMoneyField(column)) return value.trim();

        try {
            String clean = value.replace(",", ".").replaceAll("[^\\d.\\-]", "");
            BigDecimal amount = new BigDecimal(clean);
            if (column.equals("Anteil CS")) {
                return MONEY_FMT.format(amount);
            }
            return MONEY_FMT.format(amount) + " €";
        } catch (Exception e) {
            return value;
        }
    }
}

