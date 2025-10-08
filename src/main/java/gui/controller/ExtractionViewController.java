package gui.controller;


import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.pdfextraction.ExtractionService;
import util.FileSearchTool;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

import static gui.controller.dialog.Dialog.showErrorDialog;
import static gui.controller.dialog.Dialog.showSuccessDialog;

public class ExtractionViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionViewController.class);

    @FXML
    private TextField pdfFileField;
    @FXML
    private TextField outputDirectoryField;
    @FXML
    private Button selectPdfButton;
    @FXML
    private Button selectOutputButton;
    @FXML
    private Button extractButton;
    @FXML
    private CheckBox xlsxCheckBox;
    @FXML
    private CheckBox csvCheckBox;
    @FXML
    private TextArea logTextArea;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;

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
        Task<Boolean> extractionTask = createExtractionTask();
        progressBar.progressProperty().bind(extractionTask.progressProperty());
        Thread taskThread = new Thread(extractionTask);
        taskThread.setDaemon(true);
        taskThread.start();
    }

    private Task<Boolean> createExtractionTask() {
        return new Task<Boolean>() {

            @Override
            protected Boolean call() throws Exception {
                boolean anyExportSucceeded = false;
                try {
                    String pdfPath = pdfFileField.getText();
                    String outputDir = outputDirectoryField.getText();

                    String baseName = new File(pdfPath).getName().replace(".pdf", "");

                    if (xlsxCheckBox.isSelected()) {
                        updateMessage("XLSX-Export läuft...");
                        String xlsxPath = outputDir + File.separator + baseName + ".xlsx";
                        if (extractionService.extractAndExport(pdfPath, xlsxPath, ExportFormat.XLSX)) {
                            anyExportSucceeded = true;
                            appendLog("✅ XLSX-Export abgeschlossen: " + baseName + ".xlsx");
                        }
                    }

                    if (csvCheckBox.isSelected()) {
                        updateMessage("CSV-Export läuft...");
                        String csvPath = outputDir + File.separator + baseName + ".csv";
                        if (extractionService.extractAndExport(pdfPath, csvPath, ExportFormat.CSV)) {
                            anyExportSucceeded = true;
                            appendLog("✅ CSV-Export abgeschlossen: " + baseName + ".csv");
                        }
                    }

                    updateProgress(1.0, 1.0);
                    return anyExportSucceeded;

                } catch (Exception e) {
                    logger.error("❌ Fehler bei GUI-Extraktion", e);
                    throw e;
                }
            }

            @Override
            protected void succeeded() {
                Boolean wasAnythingExported = getValue(); // Récupère le 'return' de call()

                setProcessingMode(false);
                if (Boolean.TRUE.equals(wasAnythingExported)) {
                    statusLabel.setText("Extraktion erfolgreich abgeschlossen");
                    appendLog("🎉 Alle Exports erfolgreich abgeschlossen!");
                    showSuccessDialog("Erfolg", "PDF-Extraktion erfolgreich abgeschlossen!");
                } else {
                    statusLabel.setText("Abgeschlossen, keine Daten exportiert");
                    appendLog("ℹ️ Prozess beendet. Keine Daten entsprachen den Kriterien für den Export.");
                }
            }

            @Override
            protected void failed() {
                setProcessingMode(false);
                statusLabel.setText("Extraktion fehlgeschlagen");
                String errorMessage = getException().getMessage();
                appendLog("❌ Fehler: " + errorMessage);
                showErrorDialog("Extraktionsfehler", errorMessage);
            }
        };
    }

    private boolean validateInputs() {
        if (pdfFileField.getText().trim().isEmpty()) {
            showErrorDialog("Eingabefehler", "Bitte wählen Sie eine PDF-Datei aus.");
            return false;
        }

        if (outputDirectoryField.getText().trim().isEmpty()) {
            String downloadsPath = System.getProperty("user.home") + File.separator + "Downloads";
            File downloadsDir = new File(downloadsPath);

            if (outputDirectoryField.getText().trim().isEmpty()) {
                showErrorDialog("Eingabefehler", "Bitte wählen Sie ein Ausgabeverzeichnis aus.");
                return false;
            }

            if (!xlsxCheckBox.isSelected() && !csvCheckBox.isSelected()) {
                showErrorDialog("Eingabefehler", "Bitte wählen Sie mindestens ein Exportformat aus.");
                return false;
            }
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

}
