package gui.controller;

// Alle Imports bleiben gleich wie im alten MainController
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import model.enums.ExportFormat;
import service.ExtractionService;
import service.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.FileSearchTool;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

// Diese Klasse enthält die gesamte Logik für die Extraktionsansicht
public class ExtractionViewController implements Initializable {

    // Alle FXML-Felder und die Logik aus dem alten MainController werden hierher verschoben.
    // Der Code ist identisch.

    private static final Logger logger = LoggerFactory.getLogger(ExtractionViewController.class);

    @FXML private TextField pdfFileField;
    @FXML private TextField outputDirectoryField;
    @FXML private Button selectPdfButton;
    @FXML private Button selectOutputButton;
    @FXML private Button extractButton;
    @FXML private CheckBox xlsxCheckBox;
    @FXML private CheckBox csvCheckBox;
    @FXML private TextArea logTextArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextField searchField;
    @FXML private Button searchButton;

    private ExtractionService extractionService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("🎯 ExtractionViewController wird initialisiert...");
        extractionService = new ExtractionService(ServiceFactory.getFileService());
        xlsxCheckBox.setSelected(true);
        csvCheckBox.setSelected(true);
        statusLabel.setText("Warte auf Eingabe...");
        progressBar.setVisible(false);
        setupEventHandlers();
        logger.info("✅ ExtractionViewController erfolgreich initialisiert");
    }

    private void setupEventHandlers() {
        selectPdfButton.setOnAction(e -> selectPdfFile());
        selectOutputButton.setOnAction(e -> selectOutputDirectory());
        extractButton.setOnAction(e -> startExtraction());
        searchButton.setOnAction(e -> searchForPdfByName());
    }

    // Alle anderen Methoden (searchForPdfByName, selectPdfFile, startExtraction, etc.)
    // bleiben hier exakt so, wie sie im alten MainController waren.
    @FXML
    private void searchForPdfByName() {
        String fileNameToSearch = searchField.getText();
        if (fileNameToSearch == null || fileNameToSearch.trim().isEmpty()) {
            showErrorDialog("Suchefehler", "Bitte geben Sie einen Dateinamen in das Suchfeld ein.");
            return;
        }

        appendLog("🔍 Suche nach Datei: " + fileNameToSearch + "...");
        setProcessingMode(true);

        Task<String> searchTask = new Task<>() {
            @Override
            protected String call() {
                return FileSearchTool.findFileByName(fileNameToSearch);
            }

            @Override
            protected void succeeded() {
                String foundPath = getValue();
                setProcessingMode(false);
                if (foundPath != null) {
                    pdfFileField.setText(foundPath);
                    appendLog("✅ Datei gefunden: " + foundPath);
                } else {
                    appendLog("❌ Datei nicht gefunden: " + fileNameToSearch);
                    showErrorDialog("Suche erfolglos", "Die Datei '" + fileNameToSearch + "' konnte nicht gefunden werden.");
                }
            }

            @Override
            protected void failed() {
                setProcessingMode(false);
                appendLog("❌ Fehler bei der Dateisuche.");
                showErrorDialog("Schwerwiegender Fehler", "Ein Fehler ist während der Suche aufgetreten.");
            }
        };

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    @FXML
    private void selectPdfFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("PDF-Datei auswählen");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
        String userHome = System.getProperty("user.home");
        File initialDir = new File(userHome + "\\Downloads");
        if (initialDir.exists()) {
            fileChooser.setInitialDirectory(initialDir);
        }
        File selectedFile = fileChooser.showOpenDialog(selectPdfButton.getScene().getWindow());
        if (selectedFile != null) {
            pdfFileField.setText(selectedFile.getAbsolutePath());
            appendLog("📄 PDF-Datei ausgewählt: " + selectedFile.getName());
        }
    }

    @FXML
    private void selectOutputDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Ausgabeverzeichnis auswählen");
        File selectedDirectory = directoryChooser.showDialog(selectOutputButton.getScene().getWindow());
        if (selectedDirectory != null) {
            outputDirectoryField.setText(selectedDirectory.getAbsolutePath());
            appendLog("📁 Ausgabeverzeichnis ausgewählt: " + selectedDirectory.getName());
        }
    }

    @FXML
    private void startExtraction() {
        if (!validateInputs()) {
            return;
        }
        setProcessingMode(true);
        clearLog();
        appendLog("🚀 Extraktion wird gestartet...");
        Task<Void> extractionTask = createExtractionTask();
        progressBar.progressProperty().bind(extractionTask.progressProperty());
        Thread taskThread = new Thread(extractionTask);
        taskThread.setDaemon(true);
        taskThread.start();
    }

    private Task<Void> createExtractionTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    String pdfPath = pdfFileField.getText();
                    String outputDir = outputDirectoryField.getText();
                    updateProgress(0.1, 1.0);
                    updateMessage("PDF wird analysiert...");
                    if (xlsxCheckBox.isSelected()) {
                        updateProgress(0.3, 1.0);
                        updateMessage("XLSX-Export läuft...");
                        String xlsxPath = outputDir + "\\versicherung_extract.xlsx";
                        extractionService.extractAndExport(pdfPath, xlsxPath, ExportFormat.XLSX);
                        javafx.application.Platform.runLater(() -> appendLog("✅ XLSX-Export abgeschlossen: versicherung_extract.xlsx"));
                    }
                    if (csvCheckBox.isSelected()) {
                        updateProgress(0.7, 1.0);
                        updateMessage("CSV-Export läuft...");
                        String csvPath = outputDir + "\\versicherung_extract.csv";
                        extractionService.extractAndExport(pdfPath, csvPath, ExportFormat.CSV);
                        javafx.application.Platform.runLater(() -> appendLog("✅ CSV-Export abgeschlossen: versicherung_extract.csv"));
                    }
                    updateProgress(1.0, 1.0);
                    updateMessage("Extraktion erfolgreich abgeschlossen");
                } catch (Exception e) {
                    logger.error("❌ Fehler bei GUI-Extraktion", e);
                    javafx.application.Platform.runLater(() -> {
                        appendLog("❌ Fehler: " + e.getMessage());
                        showErrorDialog("Extraktionsfehler", e.getMessage());
                    });
                    throw e;
                }
                return null;
            }

            @Override
            protected void succeeded() {
                javafx.application.Platform.runLater(() -> {
                    setProcessingMode(false);
                    statusLabel.setText("Extraktion erfolgreich abgeschlossen");
                    appendLog("🎉 Alle Exports erfolgreich abgeschlossen!");
                    showSuccessDialog();
                });
            }

            @Override
            protected void failed() {
                javafx.application.Platform.runLater(() -> {
                    setProcessingMode(false);
                    statusLabel.setText("Extraktion fehlgeschlagen");
                });
            }
        };
    }

    // In der Datei: ExtractionViewController.java

    private boolean validateInputs() {
        if (pdfFileField.getText().trim().isEmpty()) {
            showErrorDialog("Eingabefehler", "Bitte wählen Sie eine PDF-Datei aus.");
            return false;
        }

        // --- GEÄNDERTER TEIL ---
        if (outputDirectoryField.getText().trim().isEmpty()) {
            // Wir verwenden jetzt den "Downloads"-Ordner des Benutzers als Standard.
            String downloadsPath = System.getProperty("user.home") + File.separator + "Downloads";
            File downloadsDir = new File(downloadsPath);

            // Prüfen, ob der Downloads-Ordner existiert.
            if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                outputDirectoryField.setText(downloadsPath);
                appendLog("📁 Standard-Ausgabeverzeichnis verwendet: " + downloadsPath);
            } else {
                // Fallback, falls es keinen Downloads-Ordner gibt (sehr unwahrscheinlich)
                String userHomePath = System.getProperty("user.home");
                outputDirectoryField.setText(userHomePath);
                appendLog("📁 Standard-Ausgabeverzeichnis verwendet: " + userHomePath);
            }
        }
        // --- ENDE DES GEÄNDERTEN TEILS ---

        if (!xlsxCheckBox.isSelected() && !csvCheckBox.isSelected()) {
            showErrorDialog("Eingabefehler", "Bitte wählen Sie mindestens ein Exportformat aus.");
            return false;
        }
        return true;
    }

    private void setProcessingMode(boolean processing) {
        extractButton.setDisable(processing);
        selectPdfButton.setDisable(processing);
        selectOutputButton.setDisable(processing);
        searchButton.setDisable(processing);
        progressBar.setVisible(processing);
        statusLabel.setText(processing ? "Verarbeitung läuft..." : "Warte auf Eingabe...");
    }

    private void appendLog(String message) {
        javafx.application.Platform.runLater(() -> {
            logTextArea.appendText(message + "\n");
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void clearLog() {
        logTextArea.clear();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccessDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Erfolg");
        alert.setHeaderText(null);
        alert.setContentText("PDF-Extraktion erfolgreich abgeschlossen!");
        alert.showAndWait();
    }
}
