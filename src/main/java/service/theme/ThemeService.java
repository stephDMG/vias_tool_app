package service.theme;

import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.NordDark;
import javafx.application.Application;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Service zur Verwaltung und Persistierung des UI-Themes.
 *
 * - Wendet ein ausgewähltes AtlantisFX-Theme auf die JavaFX-Anwendung an
 * - Speichert die Auswahl in einer einfachen Properties-Datei (config.properties)
 * - Lädt beim Start das zuletzt verwendete Theme
 */
public class ThemeService {

    /**
     * Unterstützte UI-Themes für die Anwendung.
     */
    public enum Theme { PRIMER_LIGHT,
        PRIMER_DARK,
        NORD_LIGHT,
        NORD_DARK }

    private static final String CONFIG_FILE = "config.properties";

    /**
     * Wendet das angegebene Theme an und speichert es als aktuelle Auswahl.
     *
     * @param theme das anzuwendende Theme
     */
    public void applyTheme(Theme theme) {
        String stylesheet;
        switch (theme) {
            case PRIMER_LIGHT:
                stylesheet = new PrimerLight().getUserAgentStylesheet();
                break;
            case PRIMER_DARK:
                stylesheet = new PrimerDark().getUserAgentStylesheet();
                break;
            case NORD_LIGHT:
                stylesheet = new NordLight().getUserAgentStylesheet();
                break;
            case NORD_DARK:
                stylesheet = new NordDark().getUserAgentStylesheet();
                break;
            default:
                stylesheet = new PrimerLight().getUserAgentStylesheet();
        }
        Application.setUserAgentStylesheet(stylesheet);
        saveTheme(theme);
    }

    /**
     * Lädt das zuletzt gespeicherte Theme aus der Konfiguration.
     * Fällt auf PRIMER_LIGHT zurück, wenn Datei fehlt/ungültig ist.
     *
     * @return das geladene Theme (nie null)
     */
    public Theme loadTheme() {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            props.load(reader);
            String themeName = props.getProperty("app.theme", "PRIMER_LIGHT");
            return Theme.valueOf(themeName);
        } catch (IOException | IllegalArgumentException e) {
            return Theme.PRIMER_LIGHT; // Standard-Theme
        }
    }

    /**
     * Persistiert die Theme-Auswahl in der Konfigurationsdatei.
     *
     * @param theme das zu speichernde Theme
     */
    private void saveTheme(Theme theme) {
        Properties props = new Properties();
        props.setProperty("app.theme", theme.name());
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            props.store(writer, "Application Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}