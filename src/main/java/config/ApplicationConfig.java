package config;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Konfiguration für Anwendungseinstellungen.
 * Diese Klasse enthält Konstanten für verschiedene Einstellungen der Anwendung,
 * wie z.B. Anwendungsname, Version, Exportpfade, Dateieinstellungen,
 * CSV-Parameter und Formatierungseinstellungen für Datum und Zahlen.
 *
 * @author Stephane
 * @since 15/07/2025
 */
public final class ApplicationConfig {

    /**
     * Der Name der Anwendung.
     */
    public static final String APP_NAME = "VIAS Export Tool";
    /**
     * Die Version der Anwendung.
     */
    public static final String APP_VERSION = "1.0.0";

    /**
     * Der Standardausgabeordner für exportierte Dateien.
     */
    public static final String DEFAULT_OUTPUT_DIR = "output/";
    /**
     * Die maximale Anzahl von Zeilen pro Exportdatei.
     */
    public static final int MAX_ROWS_PER_FILE = 100_000;
    /**
     * Die Standardkodierung für exportierte Dateien, z.B. UTF-8.
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Der Ordner für E-Mail-Anhänge.
     */
    public static final String EMAIL_ATTACHMENTS_DIR = "attachments/";
    /**
     * Der temporäre Ordner für Zwischenspeicherungen.
     */
    public static final String TEMP_DIR = "temp/";

    /**
     * Der Byte Order Mark (BOM) für CSV-Dateien, um die korrekte Zeichenkodierung sicherzustellen.
     */
    public static final String CSV_BOM = "\uFEFF";
    /**
     * Das Trennzeichen für CSV-Dateien.
     */
    public static final char CSV_DELIMITER = ';';

    /**
     * Datumsformat für die Eingabe, z.B. zum Parsen von Daten aus einer Quelle.
     * Verwendet das Muster "yyyyMMdd".
     */
    public static final DateTimeFormatter DATE_INPUT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /**
     * Datumsformat für die Ausgabe, z.B. zur Anzeige von Daten.
     * Verwendet das Muster "dd.MM.yyyy".
     */
    public static final DateTimeFormatter DATE_OUTPUT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Zahlenformat für deutsche Geldbeträge mit zwei Nachkommastellen.
     * Verwendet die {@link Locale#GERMANY} für länderspezifische Formatierung.
     */
    public static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.GERMANY);

    static {
        // Konfiguriert das MONEY_FORMAT, um immer genau zwei Nachkommastellen anzuzeigen.
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);
    }

    /**
     * Privater Konstruktor, um die Instanziierung dieser Utility-Klasse zu verhindern.
     * Da alle Felder und Methoden statisch sind, ist keine Objektinstanz erforderlich.
     */
    private ApplicationConfig() {
        // Utility-Klasse darf nicht instanziiert werden
    }
}