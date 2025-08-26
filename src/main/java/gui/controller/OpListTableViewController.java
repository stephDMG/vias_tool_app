package gui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import model.RowData;
import model.enums.ExportFormat;
import service.ServiceFactory;
import service.op.OpListeProcessService;
import service.op.OpListeTask;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static gui.controller.utils.Dialog.showErrorDialog;
import static gui.controller.utils.Dialog.showSuccessDialog;

public class OpListTableViewController implements Initializable {

    // --- UI ---
    @FXML private ComboBox<String> filterComboBox;
    @FXML private RadioButton deRadioButton;
    @FXML private RadioButton enRadioButton;
    @FXML private RadioButton excelRadioButton;
    @FXML private RadioButton pdfRadioButton;

    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private ListView<String> stepsListView;

    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Button showTableButton;

    private final ToggleGroup languageToggleGroup = new ToggleGroup();
    private final ToggleGroup formatToggleGroup   = new ToggleGroup();

    // --- Services & State ---
    private OpListeProcessService opListeProcessService;
    private OpListeTask processTask;
    private List<RowData> fullOpListeData;

    private String currentLanguage;
    private String currentFilter;
    private ExportFormat currentFormat;

    private final ObservableList<String> steps = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.opListeProcessService = new OpListeProcessService(
                ServiceFactory.getDatabaseService(),
                ServiceFactory.getFileService()
        );

        filterComboBox.getItems().addAll("Kunde", "Makler", "Intern");
        filterComboBox.getSelectionModel().select("Kunde");

        deRadioButton.setToggleGroup(languageToggleGroup);
        enRadioButton.setToggleGroup(languageToggleGroup);
        deRadioButton.setSelected(true);

        excelRadioButton.setToggleGroup(formatToggleGroup);
        pdfRadioButton.setToggleGroup(formatToggleGroup);
        excelRadioButton.setSelected(true);

        stepsListView.setItems(steps);

        setIdleState();
    }

    private void setIdleState() {
        progressBar.setVisible(false);
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        statusLabel.setText("Bereit. Wähle Optionen und starte den Prozess…");

        startButton.setDisable(false);
        stopButton.setDisable(true);
        showTableButton.setDisable(true);
    }

    private void setRunningState() {
        startButton.setDisable(true);
        stopButton.setDisable(false);
        showTableButton.setDisable(true);
        progressBar.setVisible(true);
    }

    private void setCompletedState() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        startButton.setDisable(false);
        stopButton.setDisable(true);
        showTableButton.setDisable(false);
        progressBar.setVisible(false);
        showSuccessDialog("Prozess abgeschlossen", "Der Export ist erfolgreich.");
    }

    private void setFailedState() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        startButton.setDisable(false);
        stopButton.setDisable(true);
        showTableButton.setDisable(true);
        progressBar.setVisible(false);
    }

    @FXML
    private void startExportProcess() {
        steps.clear();
        steps.add("▶ Starte Exportprozess…");
        setRunningState();

        Toggle lang = languageToggleGroup.getSelectedToggle();
        Toggle fmt  = formatToggleGroup.getSelectedToggle();
        currentLanguage = (lang != null && ((RadioButton) lang).getText().equalsIgnoreCase("EN")) ? "EN" : "DE";
        currentFilter   = filterComboBox.getValue();
        currentFormat   = (fmt != null && ((RadioButton) fmt).getText().equalsIgnoreCase("PDF")) ? ExportFormat.PDF : ExportFormat.XLSX;

        if (currentFilter == null || currentFilter.isBlank()) {
            setFailedState();
            showErrorDialog("Ungültige Eingaben", "Bitte einen Filter auswählen.");
            return;
        }

        if (opListeProcessService.monthlyExportFolderExists()) {
            steps.add("ℹ️ Hinweis: Monatsordner existiert bereits. Dateien werden überschrieben/ergänzt.");
        }

        // Crée la tâche qui gère tout le processus de A à Z.
        processTask = new OpListeTask(opListeProcessService, currentLanguage, currentFilter, currentFormat);

        progressBar.progressProperty().unbind();
        progressBar.progressProperty().bind(processTask.progressProperty());
        statusLabel.textProperty().unbind();
        statusLabel.textProperty().bind(processTask.messageProperty());

        processTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) steps.add(newMsg);
        });

        processTask.setOnSucceeded(e -> {
            setCompletedState();
        });

        processTask.setOnCancelled(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("⏹ Abgebrochen.");
            steps.add("⏹ Prozess wurde abgebrochen.");
            setFailedState();
        });

        processTask.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("❌ Fehler beim Prozess.");
            Throwable ex = processTask.getException();
            steps.add("❌ Fehler: " + (ex != null ? ex.getMessage() : "unbekannt"));
            setFailedState();
            showErrorDialog("Prozessfehler", (ex != null ? ex.getMessage() : "Unbekannter Fehler"));
        });

        new Thread(processTask, "oplist-task").start();
    }

    @FXML
    private void stopExportProcess() {
        if (processTask != null && processTask.isRunning()) {
            steps.add("⏹ Stop angefordert…");
            processTask.cancel(true);
        } else {
            steps.add("ℹ️ Kein laufender Prozess zum Stoppen.");
        }
    }

    // Les méthodes d'exportation et d'affichage ne sont plus nécessaires, car
    // tout est géré par la méthode startExportProcess().
    @FXML
    private void exportSelectedData() {
        showErrorDialog("Funktion nicht mehr verfügbar", "Der Export startet jetzt automatisch im Hauptprozess.");
    }

    @FXML
    private void showOpListTable() {
        showErrorDialog("Funktion nicht implementiert", "Die Tabellenansicht ist noch nicht implementiert.");
    }
}