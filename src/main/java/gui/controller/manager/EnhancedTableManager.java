package gui.controller.manager;

import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Universelle Tabellenverwaltung für TableView<ObservableList<String>>.
 * Suche, Spaltenaktionen, Bereinigen, Pagination (Client/Server), Kontextmenü,
 * optisches Gruppierungs-Striping, Export-Header-Helfer.
 */
public class EnhancedTableManager {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedTableManager.class);
    private static final int DEFAULT_ROWS_PER_PAGE = 100;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    // Core Components
    private final TableView<ObservableList<String>> tableView;
    private final TextField searchField;
    private final Button deleteButton;
    private final Pagination pagination;
    private final Label resultsCountLabel;

    // Data
    private List<RowData> originalData = new ArrayList<>();
    private List<RowData> filteredData = new ArrayList<>();
    private List<String> currentHeaders = new ArrayList<>();

    // Paging
    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;
    private int totalCount = 0;
    private DataLoader dataLoader = null;
    private boolean paginationEnabled = false;
    private boolean serverPaginationEnabled = false;
    private Integer lastRowsPerPage = null;

    // Features
    private boolean searchEnabled = false;
    private boolean selectionEnabled = false;

    // Group striping (optional)
    private int groupColIndex = -1;
    private List<Boolean> stripeIsA = new ArrayList<>();
    private Color groupColorA = null;
    private Color groupColorB = null;
    private boolean groupStripingEnabled = false;
    private String groupStripingHeader = null;

    private Button cleanColumnsButton;
    private boolean cleanRanForThisPage = false;

    private Consumer<String> onServerSearch;


    private final BooleanProperty hasData = new SimpleBooleanProperty(false);
    public ReadOnlyBooleanProperty hasDataProperty() { return hasData; }
    public boolean hasData() { return hasData.get(); }

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

        if (deleteButton != null) deleteButton.setDisable(true);
    }

    public EnhancedTableManager(TableView<ObservableList<String>> tableView) {
        this(tableView, null, null, null, null);
    }

    // Features
    public EnhancedTableManager enableSearch() {
        if (searchField == null) {
            logger.warn("Search requested but no SearchField provided");
            return this;
        }
        searchEnabled = true;
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (serverPaginationEnabled) {
                if (onServerSearch != null) {
                    onServerSearch.accept(newVal == null ? "" : newVal.trim());
                } else {
                    filterData(newVal);
                }
            } else {
                filterData(newVal);
            }
        });
        return this;
    }

    public EnhancedTableManager enableSelection() {
        if (deleteButton == null) {
            logger.warn("Selection requested but no DeleteButton provided");
            return this;
        }
        selectionEnabled = true;

        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        tableView.getSelectionModel().getSelectedCells().addListener(
                (ListChangeListener<TablePosition>) c ->
                        deleteButton.setDisable(tableView.getSelectionModel().getSelectedCells().isEmpty())
        );
        return this;
    }

    // Client-Pagination
    public EnhancedTableManager enablePagination(int rowsPerPage) {
        if (pagination == null) {
            logger.warn("Pagination requested but no Pagination component provided");
            return this;
        }
        this.rowsPerPage = Math.max(1, rowsPerPage);
        this.paginationEnabled = true;
        pagination.setVisible(false);
        return this;
    }

    /** Client mode : charge toutes les données et construit la table. */
    public void populateTableView(List<RowData> data) {
        serverPaginationEnabled = false;

        if (data == null || data.isEmpty()) {
            clearTable();
            return;
        }
        this.originalData = new ArrayList<>(data);
        hasData.set(true);

        if (searchEnabled && searchField != null) {
            filterData(searchField.getText());
        } else {
            this.filteredData = new ArrayList<>(data);
            refreshTable();
        }
    }

    private void refreshTable() {
        updateResultsCount();

        if (serverPaginationEnabled) {
            setupServerPagination();
        } else if (paginationEnabled && pagination != null) {
            setupClientPagination();
        } else {
            buildTableColumns(filteredData);
            populateTableData(filteredData);
            recomputeGroupStripes();
        }
    }

    private void setupClientPagination() {
        int pageCount = (int) Math.ceil((double) filteredData.size() / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setCurrentPageIndex(0);
        pagination.setVisible(filteredData.size() > 0);
        pagination.setPageFactory(this::createClientPage);
        updateResultsCount();
    }

    private Node createClientPage(int pageIndex) {
        int fromIndex = pageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredData.size());
        List<RowData> pageData = filteredData.subList(fromIndex, toIndex);

        buildTableColumns(pageData);
        populateTableData(pageData);
        recomputeGroupStripes();

        return new VBox();
    }

    // Server-Pagination
    private void setupServerPagination() {
        if (pagination == null) return;

        int pageCount = (int) Math.ceil((double) totalCount / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setCurrentPageIndex(0);
        pagination.setVisible(totalCount > 0);
        pagination.setPageFactory(this::createServerPage);

        updateResultsCount();
    }

    private Node createServerPage(int pageIndex) {
        loadServerPageData(pageIndex);
        return new Label();
    }

    private void loadServerPageData(int pageIndex) {
        EXECUTOR.submit(() -> {
            try {
                List<RowData> pageData = dataLoader.loadPage(pageIndex, rowsPerPage);

                Platform.runLater(() -> {
                    if (pageData == null || pageData.isEmpty()) {
                        tableView.setItems(FXCollections.observableArrayList());
                        tableView.setPlaceholder(new Label("Keine Daten gefunden."));
                        hasData.set(false); // <-- important pour désactiver export quand page vide
                    } else {
                        buildTableColumns(pageData);
                        populateTableData(pageData);
                        recomputeGroupStripes();
                        hasData.set(true); // <-- données présentes -> export ON
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                        Dialog.showErrorDialog("Ladefehler", "Daten konnten nicht geladen werden:\n" + e.getMessage())
                );
            }
        });
    }

    /** Initialise la pagination serveur et signale la présence de données. */
    public void loadDataFromServer(int totalCount, DataLoader dataLoader) {
        serverPaginationEnabled = true;
        this.dataLoader = Objects.requireNonNull(dataLoader, "DataLoader must not be null for server-side pagination.");
        this.totalCount = Math.max(0, totalCount);

        if (this.totalCount <= 0) {
            clearTable();
            return;
        }

        hasData.set(true);

        // 🆕 reset pagination page index
        if (pagination != null) {
            pagination.setCurrentPageIndex(0);
        }

        setupServerPagination(); // reconstruit la pagination

        // 🆕 force le recalcul du label
        updateResultsCount();
    }


    // Grouping (optical striping)
    public void applyGroupingConfig(boolean enabled, String headerName, Color colorA, Color colorB) {
        this.groupStripingEnabled = enabled && headerName != null && !headerName.isBlank();
        this.groupStripingHeader = this.groupStripingEnabled ? headerName : null;
        this.groupColorA = this.groupStripingEnabled ? colorA : null;
        this.groupColorB = this.groupStripingEnabled ? colorB : null;
        if (!groupStripingEnabled) stripeIsA.clear();
        installGroupRowFactory();
        recomputeGroupStripes();
    }

    public EnhancedTableManager enableGroupStripingByHeader(String headerName) {
        applyGroupingConfig(true, headerName, null, null);
        return this;
    }

    public void disableGrouping() {
        applyGroupingConfig(false, null, null, null);
    }

    public void configureGrouping(String headerName, Color colorA, Color colorB) {
        this.groupStripingEnabled = (headerName != null && !headerName.isBlank());
        this.groupStripingHeader = headerName;
        if (colorA != null) this.groupColorA = colorA;
        if (colorB != null) this.groupColorB = colorB;
        installGroupRowFactory();
        recomputeGroupStripes();
    }

    private static String toRgbaCss(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        String a = String.format(java.util.Locale.US, "%.3f", c.getOpacity());
        return "rgba(" + r + "," + g + "," + b + "," + a + ")";
    }

    private void installGroupRowFactory() {
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ObservableList<String> item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("");

                if (empty || item == null) return;
                if (isSelected()) return;

                if (!groupStripingEnabled || groupStripingHeader == null || (groupColorA == null && groupColorB == null)) {
                    return;
                }

                int rowIndex = getIndex();
                if (rowIndex >= 0 && rowIndex < stripeIsA.size()) {
                    boolean isA = stripeIsA.get(rowIndex);
                    Color c = isA ? groupColorA : groupColorB;
                    String cssColor = toRgbaCss(c);
                    if (cssColor != null) {
                        setStyle("-fx-background-color: " + cssColor + ";");
                    }
                }
            }
        });
    }

    private int findHeaderIndex(String header) {
        if (filteredData == null || filteredData.isEmpty()) return -1;
        List<String> headers = new ArrayList<>(filteredData.get(0).getValues().keySet());
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h.equalsIgnoreCase(header) || h.replace(" ", "").equalsIgnoreCase(header.replace(" ", ""))) {
                return i;
            }
        }
        return -1;
    }

    private void recomputeGroupStripes() {
        if (!groupStripingEnabled || groupStripingHeader == null) return;
        var items = tableView.getItems();
        stripeIsA = new ArrayList<>(items.size());
        groupColIndex = findHeaderIndex(groupStripingHeader);
        String lastKey = null;
        boolean useA = true;
        for (ObservableList<String> row : items) {
            String key = "";
            if (groupColIndex >= 0 && groupColIndex < row.size()) {
                key = Objects.requireNonNullElse(row.get(groupColIndex), "");
            }
            if (!Objects.equals(key, lastKey)) {
                useA = !useA;
                lastKey = key;
            }
            stripeIsA.add(useA);
        }
        tableView.refresh();
    }

    // Search (client)
    private void filterData(String filterText) {
        String lowerCaseFilter = (filterText != null) ? filterText.toLowerCase() : "";
        if (lowerCaseFilter.isEmpty()) {
            filteredData = new ArrayList<>(originalData);
        } else {
            filteredData = originalData.stream()
                    .filter(row -> row.getValues().values().stream()
                            .filter(Objects::nonNull)
                            .anyMatch(cell -> cell.toLowerCase().contains(lowerCaseFilter)))
                    .collect(Collectors.toList());
        }
        refreshTable();
    }

    // Auto-rows per page
    public void setRowsPerPage(int rowsPerPage) {
        if (rowsPerPage <= 0) return;
        this.rowsPerPage = rowsPerPage;
        if (serverPaginationEnabled) {
            setupServerPagination();
        } else {
            refreshTable();
        }
    }

    public void bindAutoRowsPerPage(Region observedRegion) {
        final double chrome = 90.0;
        final double defaultRowH = 24.0;
        final int minRows = 10;
        final int changeThreshold = 2;

        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        observedRegion.heightProperty().addListener((obs, oldH, newH) -> {
            debounce.stop();
            debounce.setOnFinished(evt -> {
                double h = (newH == null) ? 0 : newH.doubleValue();
                double rowH = (tableView.getFixedCellSize() > 0) ? tableView.getFixedCellSize() : defaultRowH;
                int rows = (int) Math.max(minRows, Math.floor((h - chrome) / rowH));
                if (lastRowsPerPage == null || Math.abs(rows - lastRowsPerPage) >= changeThreshold) {
                    lastRowsPerPage = rows;
                    setRowsPerPage(rows);
                }
            });
            debounce.playFromStart();
        });
    }

    // Table build & data
    private void buildTableColumns(List<RowData> data) {
        if (data == null || data.isEmpty()) {
            tableView.getColumns().clear();
            currentHeaders = List.of();
            markDataChanged();
            return;
        }

        List<String> headers = new ArrayList<>(data.get(0).getValues().keySet());
        if (headers.equals(currentHeaders) && !tableView.getColumns().isEmpty()) {
            return;
        }

        tableView.getColumns().clear();
        currentHeaders = headers;

        for (int i = 0; i < headers.size(); i++) {
            final int columnIndex = i;
            final String originalKey = headers.get(i);
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(originalKey);
            column.setUserData(originalKey);
            column.setCellValueFactory(param -> {
                ObservableList<String> row = param.getValue();
                return new SimpleStringProperty(
                        (row != null && columnIndex < row.size()) ? row.get(columnIndex) : ""
                );
            });
            addContextMenuToColumn(column);
            tableView.getColumns().add(column);
        }
        markDataChanged();
    }

    private void populateTableData(List<RowData> data) {
        if (data == null || data.isEmpty()) {
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
        markDataChanged();
    }

    // Clean / Kontextmenü / Delete
    public EnhancedTableManager enableCleanTable() {
        if (this.cleanColumnsButton == null) return this;
        this.cleanColumnsButton.setDisable(false);
        this.cleanColumnsButton.setOnAction(e -> cleanTable());
        return this;
    }

    public void setCleanButton(Button cleanButton) {
        this.cleanColumnsButton = cleanButton;
        this.cleanColumnsButton.setOnAction(e -> cleanTable());
    }

    private void cleanTable() {
        if (this.cleanColumnsButton != null && cleanRanForThisPage) {
            return;
        }

        var cols = new ArrayList<TableColumn<ObservableList<String>, ?>>(tableView.getColumns());
        var items = tableView.getItems();

        if (cols.isEmpty() || items == null || items.isEmpty()) return;

        if (serverPaginationEnabled) {
            boolean proceed = Dialog.showWarningDialog(
                    "Bereinigen (nur aktuelle Seite)",
                    "Es werden nur Spalten entfernt, die auf der AKTUELLEN Seite komplett leer sind.\n" +
                            "Fortfahren?");
            if (!proceed) return;
        }

        List<TableColumn<ObservableList<String>, ?>> toRemove = new ArrayList<>();
        for (int c = 0; c < cols.size(); c++) {
            boolean allEmpty = true;
            for (ObservableList<String> row : items) {
                String v = (c < row.size()) ? row.get(c) : null;
                if (v != null && !v.trim().isEmpty()) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) toRemove.add(cols.get(c));
        }

        if (toRemove.isEmpty()) {
            Dialog.showInfoDialog("Bereinigen", "Es gibt keine vollständig leeren Spalten auf dieser Seite.");
            cleanRanForThisPage = true;
            if (this.cleanColumnsButton != null) this.cleanColumnsButton.setDisable(true);
            return;
        }

        int minKeep = 2;
        if (cols.size() - toRemove.size() < minKeep) {
            int canDelete = Math.max(0, cols.size() - minKeep);
            if (canDelete == 0) {
                Dialog.showWarningDialog("Bereinigen",
                        "Mindestens " + minKeep + " Spalten müssen sichtbar bleiben.");
                cleanRanForThisPage = true;
                if (this.cleanColumnsButton != null) this.cleanColumnsButton.setDisable(true);
                return;
            }
            toRemove = toRemove.subList(0, canDelete);
        }

        deleteColumns(toRemove);

        cleanRanForThisPage = true;
        if (this.cleanColumnsButton != null) this.cleanColumnsButton.setDisable(true);
    }

    private void markDataChanged() {
        cleanRanForThisPage = false;
        if (this.cleanColumnsButton != null) {
            this.cleanColumnsButton.setDisable(false);
        }
    }

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

    private void deleteColumn(TableColumn<ObservableList<String>, String> column) {
        if (Dialog.showWarningDialog("Spalte löschen",
                "Möchten Sie die Spalte '" + column.getText() + "' wirklich löschen?")) {
            deleteColumns(List.of(column));
        }
    }

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

    private void deleteColumns(List<TableColumn<ObservableList<String>, ?>> columnsToDelete) {
        Set<String> originalKeysToDelete = columnsToDelete.stream()
                .map(col -> String.valueOf(col.getUserData()))
                .collect(Collectors.toSet());
        tableView.getColumns().removeAll(columnsToDelete);
        for (RowData row : originalData) row.getValues().keySet().removeAll(originalKeysToDelete);
        for (RowData row : filteredData) row.getValues().keySet().removeAll(originalKeysToDelete);
        updateResultsCount();
        tableView.refresh();
        logger.info("Deleted {} columns", columnsToDelete.size());
    }

    // Anzeigezähler & Clear
    private void updateResultsCount() {
        if (resultsCountLabel != null) {
            int countToDisplay = serverPaginationEnabled ? totalCount : filteredData.size();
            resultsCountLabel.setText("(" + countToDisplay + " Ergebnis" + (countToDisplay != 1 ? "se" : "") + ")");
        }
    }

    private void clearTable() {
        tableView.getColumns().clear();
        tableView.getItems().clear();
        if (pagination != null) {
            pagination.setVisible(false);
        }
        hasData.set(false); // <-- important : export OFF quand plus de données
        updateResultsCount();
    }

    // Export helpers
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

    /**
     * Gibt die aktuell gefilterten Daten (Client-seitig) zurück.
     * Wird z.B. vom AiAssistantViewController verwendet.
     */
    public List<RowData> getFilteredData() {
        return new ArrayList<>(filteredData);
    }


    public EnhancedTableManager setOnServerSearch(Consumer<String> handler) {
        this.onServerSearch = handler;
        return this;
    }


    /**
     * Gibt die Originaldaten (ungefiltert) zurück.
     */
    public List<RowData> getOriginalData() {
        return new ArrayList<>(originalData);
    }

    public TextField getSearchField() {
        return searchField;
    }

}