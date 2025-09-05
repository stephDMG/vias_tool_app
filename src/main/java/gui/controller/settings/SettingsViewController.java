package gui.controller.settings;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import service.theme.ThemeService;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller der Einstellungsansicht (Theme-Auswahl).
 * Liest das zuletzt gespeicherte Theme und setzt den passenden Radio-Button.
 * Beim Speichern wird das gewählte Theme angewendet und persistiert.
 */
public class SettingsViewController implements Initializable {

    private final ThemeService themeService = new ThemeService();
    @FXML
    private ToggleGroup themeToggleGroup;
    @FXML
    private RadioButton primerLightRadio, primerDarkRadio, nordLightRadio, nordDarkRadio;

    /**
     * Initialisiert die Ansicht, indem das gespeicherte Theme geladen und
     * der entsprechende Radio-Button selektiert wird.
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        ThemeService.Theme currentTheme = themeService.loadTheme();
        switch (currentTheme) {
            case PRIMER_DARK:
                primerDarkRadio.setSelected(true);
                break;
            case NORD_LIGHT:
                nordLightRadio.setSelected(true);
                break;
            case NORD_DARK:
                nordDarkRadio.setSelected(true);
                break;
            default:
                primerLightRadio.setSelected(true);
                break;
        }
    }

    /**
     * Anwenden und Speichern des vom Benutzer gewählten Themes.
     */
    @FXML
    private void handleSave() {
        if (primerLightRadio.isSelected()) {
            themeService.applyTheme(ThemeService.Theme.PRIMER_LIGHT);
        } else if (primerDarkRadio.isSelected()) {
            themeService.applyTheme(ThemeService.Theme.PRIMER_DARK);
        } else if (nordLightRadio.isSelected()) {
            themeService.applyTheme(ThemeService.Theme.NORD_LIGHT);
        } else if (nordDarkRadio.isSelected()) {
            themeService.applyTheme(ThemeService.Theme.NORD_DARK);
        }
    }
}