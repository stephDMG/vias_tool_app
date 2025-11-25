package gui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import model.RowData;
import model.enums.ExportFormat;
import model.op.kunde.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.op.OPListenService;
import service.op.repository.OpRepository;
import service.op.task.GenerateOpListeTask;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static gui.controller.dialog.Dialog.showErrorDialog;
import static gui.controller.dialog.Dialog.showSuccessDialog;

/**
 * Controller für den OP-Listen-Export.
 * Kümmert sich um Start/Stop des Exportprozesses, Statuswechsel,
 * Ergebnisanzeige und Export der selektierten Daten.
 */
public class OpListTableViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(OpListTableViewController.class);
    private final ToggleGroup languageToggleGroup = new ToggleGroup();
    private final ToggleGroup formatToggleGroup = new ToggleGroup();
    private final ObservableList<String> steps = FXCollections.observableArrayList();

    // --- UI ---
    @FXML
    private ComboBox<String> filterComboBox;
    @FXML
    private ComboBox<String> kundeComboBox;   // <- Nouveau ComboBox pour clients
    @FXML
    private RadioButton deRadioButton;
    @FXML
    private RadioButton enRadioButton;
    @FXML
    private RadioButton excelRadioButton;
    @FXML
    private RadioButton pdfRadioButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private ListView<String> stepsListView;
    @FXML
    private Button startButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button showTableButton;
    @FXML
    private Button loadOnlyButton;

    // --- Services & State ---
    private OPListenService opListenService;
    private GenerateOpListeTask processTask;
    private List<RowData> fullOpListeData;
    private String currentLanguage;
    private String currentFilter;
    private ExportFormat currentFormat;
    private OpRepository opRepository;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        this.opListenService = ServiceFactory.getOpListeService();

        this.opRepository = ServiceFactory.getOpRepository();

        // Filtres principaux
        filterComboBox.getItems().addAll("Kunde", "Makler", "Intern");
        filterComboBox.getSelectionModel().select("Kunde");

        // Sous-filtres clients
        kundeComboBox.getItems().addAll("Hartrodt", "Gateway", "Saco", "Fivestar");
        kundeComboBox.setVisible(true);
        kundeComboBox.setManaged(true);
        kundeComboBox.getSelectionModel().selectFirst();

        // Affichage dynamique du combo "Kunde"
        filterComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isKunde = "Kunde".equalsIgnoreCase(newVal);
            kundeComboBox.setVisible(isKunde);
            kundeComboBox.setManaged(isKunde);

            if (isKunde && kundeComboBox.getValue() == null) {
                kundeComboBox.getSelectionModel().selectFirst();
            }
        });

        // RadioButtons
        deRadioButton.setToggleGroup(languageToggleGroup);
        enRadioButton.setToggleGroup(languageToggleGroup);
        deRadioButton.setSelected(true);

        excelRadioButton.setToggleGroup(formatToggleGroup);
        pdfRadioButton.setToggleGroup(formatToggleGroup);
        excelRadioButton.setSelected(true);

        stepsListView.setItems(steps);

        setIdleState();
    }

    @FXML
    private void loadOnly() {
        steps.clear();
        steps.add("⏬ Lade OP-Hauptliste (ohne Export)...");
        setRunningState();
        statusLabel.setText("Lade Hauptliste…");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        loadOnlyButton.setDisable(true);

        Task<List<RowData>> loadTask = new Task<>() {
            @Override
            protected List<RowData> call() throws Exception {
                if (opRepository.isCacheEmpty()) {
                    return opRepository.loadAndCacheMainList();
                } else {
                    return opRepository.getMainCache();
                }
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<RowData> list = loadTask.getValue();
            int count = (list == null) ? 0 : list.size();
            steps.add("✅ Hauptliste geladen: " + count + " Zeilen.");
            statusLabel.setText("Hauptliste geladen: " + count + " Zeilen.");
            progressBar.setVisible(false);

            startButton.setDisable(false);
            stopButton.setDisable(true);
            showTableButton.setDisable(count == 0);
        });

        loadTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = loadTask.getException();
            steps.add("❌ Fehler beim Laden: " + (ex != null ? ex.getMessage() : "unbekannt"));
            setFailedState();
            showErrorDialog("Ladefehler", ex != null ? ex.getMessage() : "Unbekannter Fehler");
        });

        new Thread(loadTask, "oplist-loadonly-thread").start();
    }

    private void setIdleState() {
        progressBar.setVisible(false);
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        statusLabel.setText("Bereit. Wähle Optionen und starte den Prozess…");

        startButton.setDisable(false);
        stopButton.setDisable(true);

        boolean cacheReady = false;
        try {
            if (opRepository != null && !opRepository.isCacheEmpty()) {
                cacheReady = true;
            }
        } catch (Exception ignored) {
        }

        showTableButton.setDisable(!cacheReady);
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
        Toggle fmt = formatToggleGroup.getSelectedToggle();
        currentLanguage = (lang != null && ((RadioButton) lang).getText().equalsIgnoreCase("EN")) ? "EN" : "DE";
        currentFilter = filterComboBox.getValue();
        currentFormat = (fmt != null && ((RadioButton) fmt).getText().equalsIgnoreCase("PDF")) ? ExportFormat.PDF : ExportFormat.XLSX;

        // Kunde → sous-filtres obligatoires
        if ("Kunde".equalsIgnoreCase(currentFilter)) {
            String selectedKunde = kundeComboBox.getValue();
            if (selectedKunde == null || selectedKunde.isBlank()) {
                setFailedState();
                showErrorDialog("Ungültige Eingaben", "Bitte einen Kunden auswählen.");
                return;
            }
            currentFilter = selectedKunde; // ex: "Hartrodt"
        }

        if (currentFilter == null || currentFilter.isBlank()) {
            setFailedState();
            showErrorDialog("Ungültige Eingaben", "Bitte einen Filter auswählen.");
            return;
        }


        processTask = new GenerateOpListeTask(opListenService, currentFilter, currentLanguage, currentFormat);

        progressBar.progressProperty().unbind();
        progressBar.progressProperty().bind(processTask.progressProperty());
        statusLabel.textProperty().unbind();
        statusLabel.textProperty().bind(processTask.messageProperty());

        processTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) steps.add(newMsg);
        });

        processTask.setOnSucceeded(e -> {
            ExecutionResult result = processTask.getValue();
            if (result.isSuccess()) {
                setCompletedState();
            } else {
                setFailedState();
                showErrorDialog("Fehler", result.getMessage());
            }
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


    @FXML
    private void showOpListTable() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OpListViewer.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("OP-Liste (Hauptliste)");

            Scene scene = new Scene(root, 1200, 700);

            var css = getClass().getResource("/css/styles-atlantafx.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }

            stage.setScene(scene);
            stage.show();

        } catch (Exception ex) {
            log.error("Fehler beim Öffnen der OP-Liste Ansicht", ex);
            showErrorDialog("Ansichtsfehler", ex.getMessage());
        }
    }
}
