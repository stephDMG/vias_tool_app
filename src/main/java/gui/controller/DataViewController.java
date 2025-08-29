package gui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.interfaces.FileService;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static gui.controller.utils.Dialog.showErrorDialog;

/**
 * Controller für die Datenansicht: Laden von CSV/XLSX-Dateien, einfache
 * Volltextsuche über alle Zellen, Paginierung und Tabellenanzeige.
 */
public class DataViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DataViewController.class);
    private static final int ROWS_PER_PAGE = 100;

    @FXML private Button loadFileButton;
    @FXML private TextField searchField;
    @FXML private Label resultsCountLabel;
    @FXML private TableView<ObservableList<String>> dataTableView;
    @FXML private Pagination pagination;

    private FileService fileService;
    private List<RowData> fullDataList = new ArrayList<>();
    private List<RowData> filteredDataList = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileService = ServiceFactory.getFileService();
        pagination.setPageFactory(this::createPage);
        pagination.setVisible(false);

        // Listener für das Suchfeld hinzufügen
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterData(newValue);
        });
    }

    @FXML
    private void loadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datei auswählen");
        // GEÄNDERT: Akzeptiert jetzt CSV und XLSX
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tabellendateien", "*.csv", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV-Dateien", "*.csv"),
                new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx")
        );
        File file = fileChooser.showOpenDialog(loadFileButton.getScene().getWindow());

        if (file != null) {
            try {
                fullDataList = fileService.readFile(file.getAbsolutePath());
                logger.info("{} Zeilen aus {} geladen.", fullDataList.size(), file.getName());
                filterData(""); // Initial alle Daten anzeigen
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Datei", e);
                showErrorDialog("Fehler beim Laden", "Die Datei konnte nicht gelesen werden: " + e.getMessage());
            }
        }
    }

    private void filterData(String filterText) {
        String lowerCaseFilter = filterText.toLowerCase();

        if (lowerCaseFilter.isEmpty()) {
            filteredDataList = new ArrayList<>(fullDataList);
        } else {
            filteredDataList = fullDataList.stream()
                    .filter(row -> row.getValues().values().stream()
                            .anyMatch(cell -> cell.toLowerCase().contains(lowerCaseFilter)))
                    .collect(Collectors.toList());
        }
        setupPagination();
    }

    private void setupPagination() {
        int totalItems = filteredDataList.size();
        resultsCountLabel.setText(totalItems + " Ergebnis(se)");
        int pageCount = (int) Math.ceil((double) totalItems / ROWS_PER_PAGE);
        pagination.setPageCount(pageCount > 0 ? pageCount : 1);
        pagination.setCurrentPageIndex(0);
        pagination.setVisible(totalItems > 0);

        createPage(0); // Erste Seite anzeigen
    }

    private Node createPage(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, filteredDataList.size());

        List<RowData> pageData = filteredDataList.subList(fromIndex, toIndex);
        populateTableView(pageData);

        return new VBox(); // Platzhalter, da die Tabelle bereits im Scene Graph ist
    }

    private void populateTableView(List<RowData> data) {
        // Spalten nur erstellen, wenn sie noch nicht existieren
        if (dataTableView.getColumns().isEmpty() && !fullDataList.isEmpty()) {
            List<String> headers = new ArrayList<>(fullDataList.get(0).getValues().keySet());
            for (int i = 0; i < headers.size(); i++) {
                final int finalI = i;
                TableColumn<ObservableList<String>, String> column = new TableColumn<>(headers.get(i));
                column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(finalI)));
                dataTableView.getColumns().add(column);
            }
        }

        ObservableList<ObservableList<String>> tableData = FXCollections.observableArrayList();
        if (!data.isEmpty()) {
            List<String> headers = new ArrayList<>(data.get(0).getValues().keySet());
            for (RowData row : data) {
                ObservableList<String> rowValues = FXCollections.observableArrayList();
                for (String header : headers) {
                    rowValues.add(row.getValues().getOrDefault(header, ""));
                }
                tableData.add(rowValues);
            }
        }
        dataTableView.setItems(tableData);
    }

}
