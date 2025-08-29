package gui.controller;

import gui.controller.utils.TableManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.RowData;
import model.enums.ExportFormat;
import model.enums.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.interfaces.DatabaseService;


import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static gui.controller.utils.Dialog.showErrorDialog;
import static gui.controller.utils.Dialog.showSuccessDialog;
import static gui.controller.utils.format.FormatterService.exportWithFormat;

/**
 * Controller für den Datenbank-Exportbereich. Unterstützt vordefinierte Abfragen
 * mit Parametererfassung (Grid oder Liste), Vorschau mit Spaltenverwaltung
 * und Export in CSV/XLSX.
 */
public class DbExportViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DbExportViewController.class);
    private static final int ROWS_PER_PAGE = 100;

    // --- Bestehende FXML-Felder ---
    @FXML private ComboBox<String> reportComboBox;
    @FXML private Button helpButton;
    @FXML private GridPane parameterGrid;
    @FXML private Button executeQueryButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TableView<ObservableList<String>> previewTableView;
    @FXML private Button exportCsvButton;
    @FXML private Button exportXlsxButton;
    @FXML private Pagination pagination;
    @FXML private Label resultsCountLabel;
    @FXML private Button deleteColumnsButton;

    @FXML private VBox parameterListBox;
    @FXML private TextField parameterInputTextField;
    @FXML private ListView<String> parameterListView;

    private DatabaseService databaseService;
    private QueryRepository selectedQuery;
    private final List<TextField> parameterTextFields = new ArrayList<>();
    private List<RowData> fullResults = new ArrayList<>();
    private final ObservableList<String> listParameters = FXCollections.observableArrayList();
    private TableManager tableManager;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.databaseService = ServiceFactory.getDatabaseService();

        // ComboBox mit allen Queries füllen, die keine AI-Queries sind
        List<String> displayNames = QueryRepository.getDisplayNames().stream()
                .filter(name -> !name.startsWith("AI"))
                .collect(Collectors.toList());
        reportComboBox.setItems(FXCollections.observableArrayList(displayNames));

        // Listener, der die Ansicht basierend auf der Auswahl umschaltet
        reportComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedQuery = QueryRepository.fromDisplayName(newVal);
                updateParameterView(); // NEUE Methode, die die Ansicht steuert
            }
        });

        // Die ListView mit der ObservableList verbinden
        parameterListView.setItems(listParameters);

        pagination.setPageFactory(this::createPage);


        this.tableManager =  new TableManager(previewTableView);
        this.tableManager.allowSelection(deleteColumnsButton);

        // Initialzustand
        setInitialState();

    }


    @FXML
    private void handleDeleteSelectedColumns() { tableManager.handleDeleteSelectedColumns(fullResults);}


    /**
     * NEU: Diese Methode ist das Herzstück der Logik.
     * Sie schaltet zwischen der normalen Grid-Ansicht und der speziellen Listen-Ansicht um.
     */
    private void updateParameterView() {
        if (selectedQuery == QueryRepository.SCHADEN_DETAILS_BY_MAKLER_SNR) {
            // Zeige die spezielle Listen-Ansicht
            parameterGrid.setVisible(false);
            parameterGrid.setManaged(false);
            parameterListBox.setVisible(true);
            parameterListBox.setManaged(true);
        } else {
            // Zeige die normale Grid-Ansicht
            parameterListBox.setVisible(false);
            parameterListBox.setManaged(false);
            parameterGrid.setVisible(true);
            parameterGrid.setManaged(true);
            // Und fülle sie wie gewohnt
            populateParameterGrid();
        }
        helpButton.setDisable(false);
    }

    /**
     * Umbenannt von updateParameterFields zu populateParameterGrid für mehr Klarheit.
     * Füllt das normale Grid mit Textfeldern.
     */
    private void populateParameterGrid() {
        parameterGrid.getChildren().clear();
        parameterTextFields.clear();

        List<String> paramNames = selectedQuery.getParameterNames();
        for (int i = 0; i < paramNames.size(); i++) {
            Label label = new Label(paramNames.get(i) + ":");
            TextField textField = new TextField();
            textField.setPromptText("Wert für " + paramNames.get(i) + " eingeben...");
            parameterGrid.add(label, 0, i);
            parameterGrid.add(textField, 1, i);
            parameterTextFields.add(textField);
        }
    }

    @FXML
    private void handleAddParameter() {
        String newParam = parameterInputTextField.getText().trim();
        if (!newParam.isEmpty() && !listParameters.contains(newParam)) {
            listParameters.add(newParam);
            parameterInputTextField.clear();
        }
    }

    @FXML
    private void handleRemoveParameter() {
        String selectedItem = parameterListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            listParameters.remove(selectedItem);
        }
    }

    /**
     * MODIFIZIERT: Liest die Parameter aus der jeweils aktiven Ansicht.
     */
    private List<String> getParametersFromUi() {
        if (selectedQuery == null) {
            showErrorDialog("Eingabe fehlt", "Bitte wählen Sie zuerst einen Bericht aus.");
            return null;
        }

        List<String> parameters;

        if (selectedQuery == QueryRepository.SCHADEN_DETAILS_BY_MAKLER_SNR) {
            // Lese Parameter aus der ListView
            parameters = new ArrayList<>(listParameters);
            if (parameters.isEmpty()) {
                showErrorDialog("Eingabe fehlt", "Bitte fügen Sie mindestens eine Schadennummer zur Liste hinzu.");
                return null;
            }
        } else {
            // Lese Parameter aus dem normalen Grid
            parameters = parameterTextFields.stream()
                    .map(tf -> tf.getText().trim())
                    .collect(Collectors.toList());
            if (parameters.stream().anyMatch(String::isEmpty)) {
                showErrorDialog("Eingabe fehlt", "Bitte füllen Sie alle Parameterfelder aus.");
                return null;
            }
        }
        return parameters;
    }

    @FXML
    private void executeQuery() {
        List<String> parameters = getParametersFromUi();
        if (parameters == null) return;

        setProcessingState(true, "Führe Abfrage aus...");
        previewTableView.getColumns().clear();
        previewTableView.getItems().clear();
        pagination.setVisible(false);
        resultsCountLabel.setText("");

        databaseService.invalidateCache();

        Task<List<RowData>> queryTask = new Task<>() {
            @Override
            protected List<RowData> call() throws Exception {
                return databaseService.executeQuery(selectedQuery, parameters);
            }

            @Override
            protected void succeeded() {
                fullResults = getValue();
                if (fullResults != null && !fullResults.isEmpty()) {
                    setupPagination();
                    exportCsvButton.setDisable(false);
                    exportXlsxButton.setDisable(false);
                    resultsCountLabel.setText("(" + fullResults.size() + " Zeilen insgesamt)");
                } else {
                    resultsCountLabel.setText("(0 Zeilen gefunden)");
                }
                setProcessingState(false, fullResults.size() + " Zeilen gefunden.");
            }

            @Override
            protected void failed() {
                setProcessingState(false, "Fehler bei der Abfrage.");
                showErrorDialog("Datenbankfehler", getException().getMessage());
            }
        };
        new Thread(queryTask).start();
    }

    private void setInitialState() {
        exportCsvButton.setDisable(true);
        exportXlsxButton.setDisable(true);
        helpButton.setDisable(true);
        pagination.setVisible(false);
        parameterGrid.setVisible(false);
        parameterGrid.setManaged(false);
        parameterListBox.setVisible(false);
        parameterListBox.setManaged(false);
    }

    private void setupPagination() {
        int pageCount = (int) Math.ceil((double) fullResults.size() / ROWS_PER_PAGE);
        pagination.setPageCount(pageCount > 0 ? pageCount : 1);
        pagination.setCurrentPageIndex(0);
        pagination.setVisible(true);
        createPage(0);
    }

    private Node createPage(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, fullResults.size());
        List<RowData> pageData = fullResults.subList(fromIndex, toIndex);
        populateTableView(pageData);
        return new VBox();
    }


    @FXML
    private void exportFullReport(ActionEvent event) {
        if (fullResults == null || fullResults.isEmpty() || fullResults.get(0).getValues().isEmpty()) {
            logger.warn("Warnung: Datenliste ist leer. Header können nicht bestimmt werden.");
            return ;
        }

        // Anzeige-Header (sichtbar) und Original-Keys (für Formatlogik) ermitteln
        List<TableColumn<ObservableList<String>, ?>> cols = previewTableView.getColumns();
        List<String> displayHeaders = cols.stream().map(TableColumn::getText).toList();
        List<String> backingKeys = cols.stream()
                .map(c -> String.valueOf(c.getUserData()))
                .toList();

        List<String> parameters = getParametersFromUi();
        if (parameters == null) return;

        Button sourceButton = (Button) event.getSource();
        ExportFormat format = sourceButton.getId().contains("Csv") ? ExportFormat.CSV : ExportFormat.XLSX;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Vollständigen Bericht exportieren");
        fileChooser.setInitialFileName(selectedQuery.name().toLowerCase() + "_export." + format.getExtension());
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension())
        );
        File file = fileChooser.showSaveDialog(executeQueryButton.getScene().getWindow());

        if (file == null) return;

        setProcessingState(true, "Exportiere " + format.name() + "...");

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                exportWithFormat(fullResults, displayHeaders, backingKeys, file, format);
                return null;
            }

            @Override
            protected void succeeded() {
                setProcessingState(false, "Export erfolgreich abgeschlossen.");
                showSuccessDialog("Export erfolgreich", "Der Bericht wurde gespeichert als:\n" + file.getName());
            }

            @Override
            protected void failed() {
                setProcessingState(false, "Export fehlgeschlagen.");
                showErrorDialog("Exportfehler", getException().getMessage());
            }
        };
        new Thread(exportTask).start();
    }

    private void populateTableView(List<RowData> data) {
        this.tableManager.populateTableView(data);
    }



    @FXML
    private void showHelp() {
        if (selectedQuery != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Hilfe für: " + selectedQuery.getDisplayName());
            alert.setHeaderText("Beschreibung des Berichts");
            alert.setContentText(selectedQuery.getDescription());
            alert.showAndWait();
        }
    }

    private void setProcessingState(boolean isProcessing, String status) {
        progressBar.setVisible(isProcessing);
        statusLabel.setText(status);
        executeQueryButton.setDisable(isProcessing);
        exportCsvButton.setDisable(isProcessing || fullResults.isEmpty());
        exportXlsxButton.setDisable(isProcessing || fullResults.isEmpty());
    }

}