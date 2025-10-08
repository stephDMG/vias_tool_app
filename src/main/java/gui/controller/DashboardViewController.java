package gui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.interfaces.DatabaseService;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

import static gui.controller.dialog.Dialog.showErrorDialog;

/**
 * Controller für die Dashboard-Ansicht.
 * Zeigt wichtige Statistiken aus der Datenbank an und ermöglicht deren Aktualisierung.
 * Hinweis: Navigation zwischen Ansichten wird zentral im MainController umgesetzt (MainWindow.fxml).
 */
public class DashboardViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DashboardViewController.class);

    // Legacy label refs (fallback if old FXML is used)
    @FXML
    private Label activeContractsValueLabel;
    @FXML
    private Label openDamagesValueLabel;
    @FXML
    private Label brokerCountValueLabel;

    // New reusable stat-card controls (present in current FXML)
    @FXML
    private gui.components.StatCardControl activeContractsCard;
    @FXML
    private gui.components.StatCardControl openDamagesCard;
    @FXML
    private gui.components.StatCardControl brokerCountCard;
    @FXML
    private Button refreshButton;
    @FXML
    private Label statusLabel;

    private DatabaseService databaseService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.databaseService = ServiceFactory.getDatabaseService();

        // Titles for new stat cards (if present)
        if (activeContractsCard != null) activeContractsCard.setTitle("Aktive Verträge");
        if (openDamagesCard != null) openDamagesCard.setTitle("Offene Schäden");
        if (brokerCountCard != null) brokerCountCard.setTitle("Anzahl Makler");

        // Lädt die Daten beim ersten Öffnen des Dashboards.
        refreshDashboard();
    }

    /**
     * Wird aufgerufen, wenn der "Aktualisieren"-Button geklickt wird.
     * Startet einen Hintergrund-Task, um die Dashboard-Statistiken neu zu laden.
     */
    @FXML
    private void refreshDashboard() {
        setProcessing(true, "Daten werden geladen...");

        // Ein Task wird verwendet, um die Datenbankabfrage im Hintergrund auszuführen,
        // damit die GUI nicht blockiert wird.
        Task<Map<String, Integer>> statisticsTask = new Task<>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                // Diese Methode ruft die Daten aus dem DatabaseService ab.
                return databaseService.getDashboardStatistics();
            }

            @Override
            protected void succeeded() {
                // Diese Methode wird im JavaFX Application Thread ausgeführt, wenn der Task erfolgreich war.
                Map<String, Integer> stats = getValue();
                updateDashboardUI(stats);
                setProcessing(false, "Daten zuletzt aktualisiert am " + getCurrentTimestamp());
            }

            @Override
            protected void failed() {
                // Diese Methode wird im JavaFX Application Thread ausgeführt, wenn der Task fehlgeschlagen ist.
                Throwable error = getException();
                logger.error("Fehler beim Laden der Dashboard-Statistiken", error);
                setProcessing(false, "Fehler beim Laden der Daten.");
                showErrorDialog("Datenbankfehler", "Die Statistiken konnten nicht geladen werden:\n" + error.getMessage());
            }
        };

        // Startet den Task in einem neuen Thread.
        new Thread(statisticsTask).start();
    }

    /**
     * Aktualisiert die Labels in der GUI mit den abgerufenen statistischen Werten.
     * Muss im JavaFX Application Thread aufgerufen werden.
     */
    private void updateDashboardUI(Map<String, Integer> stats) {
        // Platform.runLater stellt sicher, dass UI-Updates immer im richtigen Thread erfolgen.
        Platform.runLater(() -> {
            int active = stats.getOrDefault("Aktive Verträge", 0);
            int open = stats.getOrDefault("Offene Schäden", 0);
            int brokers = stats.getOrDefault("Anzahl Makler", 0);

            if (activeContractsCard != null) activeContractsCard.setValue(String.valueOf(active));
            if (openDamagesCard != null) openDamagesCard.setValue(String.valueOf(open));
            if (brokerCountCard != null) brokerCountCard.setValue(String.valueOf(brokers));

            // Fallback for legacy labels if present
            if (activeContractsValueLabel != null) activeContractsValueLabel.setText(String.valueOf(active));
            if (openDamagesValueLabel != null) openDamagesValueLabel.setText(String.valueOf(open));
            if (brokerCountValueLabel != null) brokerCountValueLabel.setText(String.valueOf(brokers));
        });
    }

    /**
     * Setzt den Verarbeitungsstatus der UI (z.B. Deaktivieren von Buttons).
     */
    private void setProcessing(boolean isProcessing, String status) {
        refreshButton.setDisable(isProcessing);
        statusLabel.setText(status);
    }

    /**
     * Gibt den aktuellen Zeitstempel als formatierten String zurück.
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }

}
