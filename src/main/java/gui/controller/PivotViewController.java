package gui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.PivotConfig;
import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.interfaces.FileService;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class PivotViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(PivotViewController.class);

    @FXML private TextField sourceFileField;
    @FXML private Button selectSourceFileButton;
    @FXML private ComboBox<String> groupByColumnCombo;
    @FXML private ComboBox<String> pivotColumnCombo;
    @FXML private ListView<String> keepColumnsList;
    @FXML private TableView<ObservableList<String>> previewTableView;
    @FXML private Button exportButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private FileService fileService;
    private List<RowData> loadedData;
    private List<String> columnHeaders;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileService = ServiceFactory.getFileService();
        this.loadedData = new ArrayList<>();
        this.columnHeaders = new ArrayList<>();
        statusLabel.setText("Warte auf Datei-Auswahl...");
        exportButton.setDisable(true);
    }

    @FXML
    private void selectSourceFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Quelldatei für Pivot auswählen");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV-Dateien", "*.csv"),
                new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx")
        );
        File selectedFile = fileChooser.showOpenDialog(getStage());
        if (selectedFile != null) {
            sourceFileField.setText(selectedFile.getAbsolutePath());
            loadFile(selectedFile);
        }
    }

    private void loadFile(File file) {
        setProcessing(true, "Lade Datei...");
        Task<List<RowData>> loadTask = new Task<>() {
            @Override
            protected List<RowData> call() {
                return fileService.readFile(file.getAbsolutePath());
            }

            @Override
            protected void succeeded() {
                loadedData = getValue();
                if (loadedData != null && !loadedData.isEmpty()) {
                    columnHeaders = new ArrayList<>(loadedData.get(0).getValues().keySet());
                    populateControls();
                    displayPreview();
                    exportButton.setDisable(false);
                } else {
                    showErrorDialog("Fehler", "Die Datei ist leer oder konnte nicht gelesen werden.");
                }
                setProcessing(false, "Datei geladen. Bitte Konfiguration wählen.");
            }

            @Override
            protected void failed() {
                showErrorDialog("Fehler beim Laden", getException().getMessage());
                setProcessing(false, "Fehler beim Laden der Datei.");
            }
        };
        new Thread(loadTask).start();
    }

    private void populateControls() {
        ObservableList<String> headers = FXCollections.observableArrayList(columnHeaders);
        groupByColumnCombo.setItems(headers);
        pivotColumnCombo.setItems(headers);
        keepColumnsList.setItems(headers);
    }

    private void displayPreview() {
        previewTableView.getColumns().clear();
        previewTableView.getItems().clear();

        for (String header : columnHeaders) {
            final int colIndex = columnHeaders.indexOf(header);
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(header);
            column.setCellValueFactory(param -> {
                if (param.getValue() != null && param.getValue().size() > colIndex) {
                    return new SimpleStringProperty(param.getValue().get(colIndex));
                }
                return new SimpleStringProperty("");
            });
            previewTableView.getColumns().add(column);
        }

        ObservableList<ObservableList<String>> previewData = FXCollections.observableArrayList();
        for (int i = 0; i <  loadedData.size(); i++) { // Zeigt nur die ersten 100 Zeilen in der Vorschau
            RowData row = loadedData.get(i);
            ObservableList<String> rowValues = FXCollections.observableArrayList();
            for (String header : columnHeaders) {
                rowValues.add(row.getValues().getOrDefault(header, ""));
            }
            previewData.add(rowValues);
        }
        previewTableView.setItems(previewData);
    }

    @FXML
    private void startPivotExport() {
        String groupBy = groupByColumnCombo.getValue();
        String pivot = pivotColumnCombo.getValue();
        List<String> keep = keepColumnsList.getSelectionModel().getSelectedItems();

        if (groupBy == null || pivot == null || keep.isEmpty()) {
            showErrorDialog("Konfigurationsfehler", "Bitte wählen Sie 'Gruppieren nach', 'Pivot-Spalte' und mindestens eine 'Spalte beibehalten' aus.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Pivot-Export speichern unter...");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx"));
        File outputFile = fileChooser.showSaveDialog(getStage());

        if (outputFile == null) {
            return;
        }

        PivotConfig config = new PivotConfig(groupBy, pivot, keep);
        setProcessing(true, "Pivot-Transformation läuft...");

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fileService.writeFileWithPivot(loadedData, config, outputFile.getAbsolutePath(), ExportFormat.XLSX);
                return null;
            }

            @Override
            protected void succeeded() {
                setProcessing(false, "Pivot-Export erfolgreich abgeschlossen!");
                showSuccessDialog("Export erfolgreich", "Die Pivot-Tabelle wurde gespeichert unter:\n" + outputFile.getName());
            }

            @Override
            protected void failed() {
                setProcessing(false, "Pivot-Export fehlgeschlagen!");
                showErrorDialog("Exportfehler", getException().getMessage());
            }
        };
        new Thread(exportTask).start();
    }

    // Hilfsmethoden
    private void setProcessing(boolean isProcessing, String status) {
        progressBar.setVisible(isProcessing);
        statusLabel.setText(status);
        exportButton.setDisable(isProcessing);
        selectSourceFileButton.setDisable(isProcessing);
    }

    private Stage getStage() {
        return (Stage) exportButton.getScene().getWindow();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccessDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
