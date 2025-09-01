package gui.controller.utils;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import formatter.ColumnValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Universelle Tabellenverwaltung für alle Controller.
 * Features: Suche, Spaltenmanagement, Pagination, Kontextmenü, Export-Header
 */
public class EnhancedTableManager {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedTableManager.class);
    private static final int DEFAULT_ROWS_PER_PAGE = 100;

    // Core Components
    private final TableView<ObservableList<String>> tableView;
    private final TextField searchField;
    private final Button deleteButton;
    private final Pagination pagination;
    private final Label resultsCountLabel;

    // Data Management
    private List<RowData> originalData = new ArrayList<>();
    private List<RowData> filteredData = new ArrayList<>();
    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;

    // Feature Flags
    private boolean searchEnabled = false;
    private boolean paginationEnabled = false;
    private boolean selectionEnabled = false;

    /**
     * Konstruktor - Alle optionalen Komponenten können null sein
     */
    public EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                TextField searchField,
                                Button deleteButton,
                                Pagination pagination,
                                Label resultsCountLabel) {
        this.tableView = tableView;
        this.searchField = searchField;
        this.deleteButton = deleteButton;
        this.pagination = pagination;
        this.resultsCountLabel = resultsCountLabel;

        if (deleteButton != null) {
            deleteButton.setDisable(true);
        }
    }

    /**
     * Simplified constructor for basic table only
     */
    public EnhancedTableManager(TableView<ObservableList<String>> tableView) {
        this(tableView, null, null, null, null);
    }

    /**
     * Aktiviert Suchfunktion (nur wenn SearchField verfügbar)
     */
    public EnhancedTableManager enableSearch() {
        if (searchField == null) {
            logger.warn("Search requested but no SearchField provided");
            return this;
        }

        searchEnabled = true;
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterData(newVal);
        });
        logger.info("Search functionality enabled");
        return this;
    }

    /**
     * Aktiviert Spaltenauswahl und Löschen
     */
    public EnhancedTableManager enableSelection() {
        if (deleteButton == null) {
            logger.warn("Selection requested but no DeleteButton provided");
            return this;
        }

        selectionEnabled = true;
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        tableView.getSelectionModel().getSelectedCells().addListener(
                (ListChangeListener<TablePosition>) c -> {
                    deleteButton.setDisable(tableView.getSelectionModel().getSelectedCells().isEmpty());
                });

        logger.info("Selection functionality enabled");
        return this;
    }

    /**
     * Aktiviert Pagination
     */
    public EnhancedTableManager enablePagination(int rowsPerPage) {
        if (pagination == null) {
            logger.warn("Pagination requested but no Pagination component provided");
            return this;
        }

        this.rowsPerPage = rowsPerPage;
        this.paginationEnabled = true;
        pagination.setPageFactory(this::createPage);
        pagination.setVisible(false);
        logger.info("Pagination enabled with {} rows per page", rowsPerPage);
        return this;
    }

    /**
     * Lädt Daten in die Tabelle
     */
    public void populateTableView(List<RowData> data) {
        if (data == null || data.isEmpty()) {
            clearTable();
            return;
        }

        this.originalData = new ArrayList<>(data);

        if (searchEnabled && searchField != null) {
            filterData(searchField.getText());
        } else {
            this.filteredData = new ArrayList<>(data);
            refreshTable();
        }
    }

    /**
     * Filtert Daten basierend auf Suchtext
     */
    private void filterData(String filterText) {
        String lowerCaseFilter = (filterText != null) ? filterText.toLowerCase() : "";

        if (lowerCaseFilter.isEmpty()) {
            filteredData = new ArrayList<>(originalData);
        } else {
            filteredData = originalData.stream()
                    .filter(row -> row.getValues().values().stream()
                            .anyMatch(cell -> cell.toLowerCase().contains(lowerCaseFilter)))
                    .collect(Collectors.toList());
        }

        refreshTable();
    }

    /**
     * Aktualisiert die Tabellenanzeige
     */
    private void refreshTable() {
        updateResultsCount();

        if (paginationEnabled && pagination != null) {
            setupPagination();
        } else {
            buildTableColumns();
            populateTableData(filteredData);
        }
    }

    /**
     * Baut Tabellenspalten auf
     */
    private void buildTableColumns() {
        tableView.getColumns().clear();

        if (filteredData.isEmpty()) return;

        List<String> headers = new ArrayList<>(filteredData.get(0).getValues().keySet());

        for (int i = 0; i < headers.size(); i++) {
            final int columnIndex = i;
            final String originalKey = headers.get(i);

            TableColumn<ObservableList<String>, String> column = new TableColumn<>(originalKey);
            column.setUserData(originalKey);

            column.setCellValueFactory(param -> {
                ObservableList<String> row = param.getValue();
                return (row != null && columnIndex < row.size())
                        ? new SimpleStringProperty(row.get(columnIndex))
                        : new SimpleStringProperty("");
            });

            addContextMenuToColumn(column);
            tableView.getColumns().add(column);
        }
    }

    /**
     * Füllt Tabellendaten
     */
    private void populateTableData(List<RowData> data) {
        if (data.isEmpty()) {
            tableView.setItems(FXCollections.observableArrayList());
            return;
        }

        List<String> headers = new ArrayList<>(data.get(0).getValues().keySet());
        ObservableList<ObservableList<String>> tableData = FXCollections.observableArrayList();

        for (RowData row : data) {
            ObservableList<String> rowValues = FXCollections.observableArrayList();
            for (String header : headers) {
                String formattedValue = ColumnValueFormatter.format(row, header);
                rowValues.add(formattedValue);
            }
            tableData.add(rowValues);
        }

        tableView.setItems(tableData);
    }

    /**
     * Setup für Pagination
     */
    private void setupPagination() {
        int pageCount = (int) Math.ceil((double) filteredData.size() / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setCurrentPageIndex(0);
        pagination.setVisible(filteredData.size() > 0);
        createPage(0);
    }

    /**
     * Erstellt eine Seite für Pagination
     */
    private Node createPage(int pageIndex) {
        int fromIndex = pageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredData.size());
        List<RowData> pageData = filteredData.subList(fromIndex, toIndex);

        buildTableColumns();
        populateTableData(pageData);

        return new VBox();
    }

    /**
     * Fügt Kontextmenü zu Spalte hinzu
     */
    private void addContextMenuToColumn(TableColumn<ObservableList<String>, String> column) {
        MenuItem renameItem = new MenuItem("Spalte umbenennen");
        MenuItem deleteItem = new MenuItem("Spalte löschen");

        renameItem.setStyle("-fx-text-fill: #000;");
        deleteItem.setStyle("-fx-text-fill: #d00;");

        renameItem.setOnAction(e -> renameColumn(column));
        deleteItem.setOnAction(e -> deleteColumn(column));

        ContextMenu contextMenu = new ContextMenu(renameItem, new SeparatorMenuItem(), deleteItem);
        column.setContextMenu(contextMenu);
    }

    /**
     * Spalte umbenennen
     */
    private void renameColumn(TableColumn<ObservableList<String>, String> column) {
        TextInputDialog dialog = new TextInputDialog(column.getText());
        dialog.setTitle("Spalte umbenennen");
        dialog.setHeaderText("Neuer Name für '" + column.getText() + "':");
        dialog.setContentText("Name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.isBlank()) {
                column.setText(newName);
                tableView.refresh();
                logger.info("Column renamed: {} -> {}", column.getUserData(), newName);
            }
        });
    }

    /**
     * Einzelne Spalte löschen
     */
    private void deleteColumn(TableColumn<ObservableList<String>, String> column) {
        if (Dialog.showWarningDialog("Spalte löschen",
                "Möchten Sie die Spalte '" + column.getText() + "' wirklich löschen?")) {
            deleteColumns(List.of(column));
        }
    }

    /**
     * Ausgewählte Spalten löschen (für Delete-Button)
     */
    public void handleDeleteSelectedColumns() {
        if (!selectionEnabled) {
            logger.warn("Selection not enabled but delete requested");
            return;
        }

        ObservableList<TablePosition> selectedCells = tableView.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) {
            Dialog.showErrorDialog("Keine Auswahl", "Bitte wählen Sie Spalten zum Löschen aus.");
            return;
        }

        List<TableColumn<ObservableList<String>, ?>> selectedColumns = selectedCells.stream()
                .map(pos -> (TableColumn<ObservableList<String>, ?>) pos.getTableColumn())
                .distinct()
                .collect(Collectors.toList());

        if (Dialog.showWarningDialog("Spalten löschen",
                selectedColumns.size() + " Spalte(n) löschen?")) {
            deleteColumns(selectedColumns);
        }
    }

    /**
     * Spalten löschen (interne Methode)
     */
    private void deleteColumns(List<TableColumn<ObservableList<String>, ?>> columnsToDelete) {
        Set<String> originalKeysToDelete = columnsToDelete.stream()
                .map(col -> String.valueOf(col.getUserData()))
                .collect(Collectors.toSet());

        // Aus TableView entfernen
        tableView.getColumns().removeAll(columnsToDelete);

        // Aus Datenstrukturen entfernen
        for (RowData row : originalData) {
            row.getValues().keySet().removeAll(originalKeysToDelete);
        }
        for (RowData row : filteredData) {
            row.getValues().keySet().removeAll(originalKeysToDelete);
        }

        updateResultsCount();
        tableView.refresh();
        logger.info("Deleted {} columns", columnsToDelete.size());
    }

    /**
     * Aktualisiert Ergebniszähler
     */
    private void updateResultsCount() {
        if (resultsCountLabel != null) {
            int total = filteredData.size();
            resultsCountLabel.setText("(" + total + " Ergebnis" + (total != 1 ? "se" : "") + ")");
        }
    }

    /**
     * Leert die Tabelle
     */
    private void clearTable() {
        tableView.getColumns().clear();
        tableView.getItems().clear();
        if (pagination != null) {
            pagination.setVisible(false);
        }
        updateResultsCount();
    }

    // Getter für Export-Funktionalität
    public List<String> getDisplayHeaders() {
        return tableView.getColumns().stream()
                .map(TableColumn::getText)
                .collect(Collectors.toList());
    }

    public List<String> getOriginalKeys() {
        return tableView.getColumns().stream()
                .map(col -> String.valueOf(col.getUserData()))
                .collect(Collectors.toList());
    }

    public List<RowData> getFilteredData() {
        return new ArrayList<>(filteredData);
    }

    public List<RowData> getOriginalData() {
        return new ArrayList<>(originalData);
    }
}