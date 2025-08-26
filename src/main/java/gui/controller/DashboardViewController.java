package gui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
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

/**
 * Controller für die Dashboard-Ansicht.
 * Zeigt wichtige Statistiken aus der Datenbank an und ermöglicht deren Aktualisierung.
 */
public class DashboardViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DashboardViewController.class);

    @FXML private Label activeContractsValueLabel;
    @FXML private Label openDamagesValueLabel;
    @FXML private Label brokerCountValueLabel;
    @FXML private Button refreshButton;
    @FXML private Label statusLabel;

    private DatabaseService databaseService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.databaseService = ServiceFactory.getDatabaseService();
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
            activeContractsValueLabel.setText(String.valueOf(stats.getOrDefault("Aktive Verträge", 0)));
            openDamagesValueLabel.setText(String.valueOf(stats.getOrDefault("Offene Schäden", 0)));
            brokerCountValueLabel.setText(String.valueOf(stats.getOrDefault("Anzahl Makler", 0)));
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

    /**
     * Zeigt eine Fehlerdialogbox an.
     */
    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void showExtractionView(ActionEvent actionEvent) {
        // Diese Methode wird in der MainController-Klasse aufgerufen, um die Extraktionsansicht anzuzeigen.
        // Hier könnte
        // eine Logik implementiert werden, um die Extraktionsansicht zu laden und anzuzeigen.
        logger.info("Navigating to Extraction View...");
        // Beispiel: mainBorderPane.setCenter(extractionView);
        // mainBorderPane.setCenter(extractionView);


    }

    public void showDbExportView(ActionEvent actionEvent) {
    }

    public void showDataView(ActionEvent actionEvent) {
    }

    public void showPivotView(ActionEvent actionEvent) {
    }
}
