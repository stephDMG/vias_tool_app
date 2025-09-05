package gui.controller;


import gui.controller.utils.TableManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.DatenanreicherungService;
import service.ServiceFactory;
import service.interfaces.FileService;
import util.FileSearchTool;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static gui.controller.dialog.Dialog.showErrorDialog;
import static gui.controller.dialog.Dialog.showSuccessDialog;
import static gui.controller.utils.format.FormatterService.exportWithFormat;

/**
 * Controller für die Datenanreicherung: Lädt Quelldateien, sucht Dateien,
 * reichert per Datenbankinformationen an, zeigt Vorschau und exportiert CSV/XLSX.
 */
public class EnrichmentViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentViewController.class);
    private static final int ROWS_PER_PAGE = 100;
    private final List<RowData> fullResults = new ArrayList<>();
    @FXML
    private TextField sourceFileField;
    @FXML
    private Button selectFileButton;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private Button enrichButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private Label resultsCountLabel;
    @FXML
    private TableView<ObservableList<String>> previewTableView;
    @FXML
    private Pagination pagination;
    @FXML
    private Button exportCsvButton;
    @FXML
    private Button exportXlsxButton;
    @FXML
    private Button deleteColumnsButton;
    private DatenanreicherungService enrichmentService;
    private FileService fileService;
    private List<RowData> enrichedData = new ArrayList<>();
    private File selectedSourceFile;
    private TableManager tableManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileService = ServiceFactory.getFileService();
        this.enrichmentService = new DatenanreicherungService(fileService, ServiceFactory.getDatabaseService());

        this.tableManager = new TableManager(previewTableView);
        this.tableManager.allowSelection(deleteColumnsButton);

        pagination.setPageFactory(this::createPage);
        pagination.setVisible(false);
        logger.info("✅ EnrichmentViewController initialized successfully.");
    }

    /*
    @FXML
    private void handleDeleteSelectedColumns() {
        tableManager.handleDeleteSelectedColumns(fullResults);
    }
    */


    @FXML
    private void selectSourceFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Quelldatei für Anreicherung auswählen");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx", "*.xls")
        );
        File file = fileChooser.showOpenDialog(selectFileButton.getScene().getWindow());
        if (file != null) {
            updateSourceFile(file);
        }
    }

    @FXML
    private void searchForFileByName() {
        String fileNameToSearch = searchField.getText();
        if (fileNameToSearch == null || fileNameToSearch.trim().isEmpty()) {
            showErrorDialog("Suchefehler", "Bitte geben Sie einen Dateinamen in das Suchfeld ein.");
            return;
        }

        Task<String> searchTask = new Task<>() {
            @Override
            protected String call() {
                return FileSearchTool.findFileByName(fileNameToSearch);
            }

            @Override
            protected void succeeded() {
                String foundPath = getValue();
                if (foundPath != null) {
                    updateSourceFile(new File(foundPath));
                } else {
                    showErrorDialog("Suche erfolglos", "Die Datei '" + fileNameToSearch + "' konnte nicht gefunden werden.");
                }
            }

            @Override
            protected void failed() {
                showErrorDialog("Schwerwiegender Fehler", "Ein Fehler ist während der Suche aufgetreten.");
            }
        };
        new Thread(searchTask).start();
    }

    private void updateSourceFile(File file) {
        selectedSourceFile = file;
        sourceFileField.setText(file.getAbsolutePath());
        statusLabel.setText("Datei ausgewählt. Bereit zur Anreicherung.");
        enrichButton.setDisable(false);
        exportCsvButton.setDisable(true);
        exportXlsxButton.setDisable(true);
    }


    @FXML
    private void startEnrichment() {
        if (selectedSourceFile == null) {
            showErrorDialog("Keine Datei ausgewählt", "Bitte wählen Sie zuerst eine Quelldatei aus.");
            return;
        }

        setProcessing(true, "Lese Datei und reichere Daten an...");
        previewTableView.getColumns().clear();
        previewTableView.getItems().clear();
        pagination.setVisible(false);
        resultsCountLabel.setText("");

        Task<List<RowData>> enrichmentTask = new Task<>() {
            @Override
            protected List<RowData> call() throws Exception {
                return enrichmentService.enrichDataFromFile(selectedSourceFile.getAbsolutePath());
            }

            @Override
            protected void succeeded() {
                enrichedData = getValue();
                if (enrichedData != null && !enrichedData.isEmpty()) {
                    setupPagination();
                    exportCsvButton.setDisable(false);
                    exportXlsxButton.setDisable(false);
                }
                setProcessing(false, enrichedData.size() + " Zeilen erfolgreich angereichert.");
            }

            @Override
            protected void failed() {
                setProcessing(false, "Fehler bei der Datenanreicherung.");
                showErrorDialog("Fehler im Prozess", getException().getMessage());
            }
        };
        new Thread(enrichmentTask).start();
    }

    @FXML
    private void exportEnrichedData(ActionEvent event) {
        if (fullResults == null || fullResults.isEmpty() || fullResults.get(0).getValues().isEmpty()) {
            logger.warn("Warnung: Datenliste ist leer. Header können nicht bestimmt werden.");
            return;
        }

        // Anzeige-Header (sichtbar) und Original-Keys (für Formatlogik) ermitteln
        List<TableColumn<ObservableList<String>, ?>> cols = previewTableView.getColumns();
        List<String> displayHeaders = cols.stream().map(TableColumn::getText).toList();
        List<String> backingKeys = cols.stream()
                .map(c -> String.valueOf(c.getUserData()))
                .toList();

        Button sourceButton = (Button) event.getSource();
        ExportFormat format = sourceButton.getId().contains("Csv") ? ExportFormat.CSV : ExportFormat.XLSX;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Angereicherten Bericht speichern");
        String initialName = selectedSourceFile.getName().replaceFirst("[.][^.]+$", ""); // remove extension
        fileChooser.setInitialFileName(initialName + "_angereichert." + format.getExtension());
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension()));

        File outputFile = fileChooser.showSaveDialog(exportCsvButton.getScene().getWindow());
        if (outputFile == null) return;

        setProcessing(true, "Exportiere " + format.name() + "...");

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                exportWithFormat(fullResults, displayHeaders, backingKeys, outputFile, format);
                return null;
            }

            @Override
            protected void succeeded() {
                setProcessing(false, "Export erfolgreich abgeschlossen.");
                showSuccessDialog("Export erfolgreich", "Die Datei wurde gespeichert als:\n" + outputFile.getName());
            }

            @Override
            protected void failed() {
                setProcessing(false, "Export fehlgeschlagen.");
                showErrorDialog("Exportfehler", getException().getMessage());
            }
        };
        new Thread(exportTask).start();
    }


    // --- Pagination und Hilfsmethoden ---

    private void setupPagination() {
        int totalItems = enrichedData.size();
        resultsCountLabel.setText(totalItems + " Ergebnis(se)");
        int pageCount = (int) Math.ceil((double) totalItems / ROWS_PER_PAGE);
        pagination.setPageCount(pageCount > 0 ? pageCount : 1);
        pagination.setCurrentPageIndex(0);
        pagination.setVisible(totalItems > 0);
        createPage(0);
    }

    private Node createPage(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, enrichedData.size());
        List<RowData> pageData = enrichedData.subList(fromIndex, toIndex);
        populateTableView(pageData);
        return new VBox();
    }

    private void populateTableView(List<RowData> data) {
        if (previewTableView.getColumns().isEmpty() && !enrichedData.isEmpty()) {
            List<String> headers = new ArrayList<>(enrichedData.get(0).getValues().keySet());
            for (int i = 0; i < headers.size(); i++) {
                final int finalI = i;
                TableColumn<ObservableList<String>, String> column = new TableColumn<>(headers.get(i));
                column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(finalI)));
                previewTableView.getColumns().add(column);
            }
        }
        ObservableList<ObservableList<String>> tableData = FXCollections.observableArrayList();
        if (!data.isEmpty()) {
            List<String> headers = new ArrayList<>(enrichedData.get(0).getValues().keySet());
            for (RowData row : data) {
                ObservableList<String> rowValues = FXCollections.observableArrayList();
                for (String header : headers) {
                    rowValues.add(row.getValues().getOrDefault(header, ""));
                }
                tableData.add(rowValues);
            }
        }
        previewTableView.setItems(tableData);
    }

    private void setProcessing(boolean isProcessing, String status) {
        progressBar.setVisible(isProcessing);
        statusLabel.setText(status);
        selectFileButton.setDisable(isProcessing);
        enrichButton.setDisable(isProcessing);
        exportCsvButton.setDisable(isProcessing || enrichedData.isEmpty());
        exportXlsxButton.setDisable(isProcessing || enrichedData.isEmpty());
    }

}
