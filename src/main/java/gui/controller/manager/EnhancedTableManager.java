package gui.controller.manager;

import atlantafx.base.theme.NordLight;
import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
import gui.controller.manager.base.AbstractTableManager;
import gui.controller.model.ColumnStateModel;
import gui.controller.model.ResultContextModel;
import gui.controller.model.TableStateModel;
import gui.controller.service.FormatterService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manager für eine klassische {@link TableView} mit Zeilen vom Typ
 * {@code ObservableList<String>}.
 *
 * <p>
 * Diese Klasse implementiert die UI-spezifische Logik auf Basis von
 * {@link AbstractTableManager} (Suche, Pagination, globales Bereinigen,
 * Gruppierungs-Streifen) und behält die bestehende öffentliche API
 * (enableSearch, enablePagination, enableSelection, etc.) für
 * Rückwärtskompatibilität.
 * </p>
 */
public class EnhancedTableManager extends AbstractTableManager {

    private static final Logger log = LoggerFactory.getLogger(EnhancedTableManager.class);

    // UI-Referenzen
    private final TableView<ObservableList<String>> tableView;
    private final Button deleteButton;
    private Button cleanColumnsButton;

    // Datenbasis für Client-Side-Suche
    private List<RowData> originalData = new ArrayList<>();

    // Zustand
    private boolean selectionEnabled = false;

    // Gemeinsame Map für Spaltennamen, wird mit TreeTableManager geteilt
    private javafx.collections.ObservableMap<String, String> sharedColumnDisplayNames;


    // -------------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------------

    /**
     * Legacy-Konstruktor: erzeugt interne Models, wenn keine externen
     * Zustands-Modelle übergeben werden.
     */
    public EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                TextField searchField,
                                Button deleteButton,
                                Pagination pagination,
                                Label resultsCountLabel) {
        this(tableView, searchField, deleteButton, pagination, resultsCountLabel,
                new TableStateModel(), new ColumnStateModel(), new ResultContextModel());
        log.warn("EnhancedTableManager ohne explizite Models instanziiert – lokale Modelle erzeugt. " +
                "Nur für Tests/Legacy-Kontext empfohlen.");
    }

    /**
     * Vollständiger Konstruktor mit expliziten Zustandsmodellen.
     * Wird typischerweise vom {@code TableViewBuilder} verwendet.
     */
    public EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                TextField searchField,
                                Button deleteButton,
                                Pagination pagination,
                                Label resultsCountLabel,
                                TableStateModel stateModel,
                                ColumnStateModel columnModel,
                                ResultContextModel resultModel) {
        super(searchField, pagination, resultsCountLabel, stateModel, columnModel, resultModel);
        this.tableView = Objects.requireNonNull(tableView, "tableView");
        this.deleteButton = deleteButton;
        if (deleteButton != null) {
            deleteButton.setDisable(true);
        }
        tableView.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/fixed-header.css")).toExternalForm());
        //treeTableView.getStylesheets().add(getClass().getResource("/css/fixed-header.css").toExternalForm());

        installGroupRowFactory();
    }

    /**
     * Alter 7-Argumente-Konstruktor (wurde früher vom Builder genutzt).
     * Bleibt aus Kompatibilitätsgründen erhalten.
     */
    @Deprecated
    public EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                TextField searchField,
                                Button deleteButton,
                                Pagination pagination,
                                Label resultsCountLabel,
                                Object ignoredColumnStateModel,
                                Object ignoredResultContextModel) {
        this(tableView,
                searchField,
                deleteButton,
                pagination,
                resultsCountLabel,
                new TableStateModel(),
                (ignoredColumnStateModel instanceof ColumnStateModel)
                        ? (ColumnStateModel) ignoredColumnStateModel
                        : new ColumnStateModel(),
                (ignoredResultContextModel instanceof ResultContextModel)
                        ? (ResultContextModel) ignoredResultContextModel
                        : new ResultContextModel());
    }

    /**
     * Minimal-Konstruktor nur mit TableView (hauptsächlich für Tests).
     */
    public EnhancedTableManager(TableView<ObservableList<String>> tableView) {
        this(tableView, null, null, null, null);
    }

    // Ajouter cette méthode
    public void resizeColumnsToFitContent() {
        if (tableView.getItems().isEmpty()) return;

        for (TableColumn<ObservableList<String>, ?> column : tableView.getColumns()) {
            double maxWidth = 0.0;

            // Largeur de l'en-tête
            Text headerText = new Text(column.getText());
            maxWidth = headerText.getLayoutBounds().getWidth() + 20;

            // Largeur du contenu (échantillon de 10 premières lignes)
            for (int i = 0; i < Math.min(10, tableView.getItems().size()); i++) {
                Object cellData = column.getCellData(i);
                if (cellData != null) {
                    Text cellText = new Text(cellData.toString());
                    double cellWidth = cellText.getLayoutBounds().getWidth() + 10;
                    maxWidth = Math.max(maxWidth, cellWidth);
                }
            }

            // Limiter à 300px max par colonne
            column.setPrefWidth(Math.min(maxWidth, 300));
            column.setMinWidth(80);  // Largeur minimale pour éviter tronquage
        }
    }

    // -------------------------------------------------------------------------
    // Öffentliche API (kompatibel)
    // -------------------------------------------------------------------------

    public EnhancedTableManager enableSearch() {
        super.enableSearch();
        return this;
    }

    public TableView<ObservableList<String>> getTableView() {
        return tableView;
    }

    public void rebuildView() {
        refreshView(); // méthode protégée existante
    }

    public EnhancedTableManager enableSelection() {
        if (deleteButton == null) {
            log.warn("Selection angefordert, aber kein Delete-Button vorhanden.");
            return this;
        }
        selectionEnabled = true;
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        tableView.getSelectionModel().getSelectedCells().addListener(
                (ListChangeListener<TablePosition>) c ->
                        deleteButton.setDisable(tableView.getSelectionModel().getSelectedCells().isEmpty())
        );
        deleteButton.setOnAction(e -> handleDeleteSelectedColumns());
        return this;
    }

    public EnhancedTableManager enablePagination(int rowsPerPage) {
        super.enablePagination(rowsPerPage);
        return this;
    }

    /**
     * Bindet die automatische Berechnung der Zeilen pro Seite an eine Region
     * (z.B. BorderPane-Zentrum).
     *
     * <p>
     * Diese Methode überschreibt NICHT die Methode in {@link AbstractTableManager},
     * sondern ruft sie nur auf und gibt zur besseren Chainbarkeit {@code this} zurück.
     * </p>
     */
    @Override
    public void bindAutoRowsPerPage(Region observedRegion) {
        // ruft nur die Logik der Basisklasse auf
        super.bindAutoRowsPerPage(observedRegion);
    }

    public EnhancedTableManager withAutoRowsPerPage(Region observedRegion) {
        super.bindAutoRowsPerPage(observedRegion);
        return this;
    }


    public EnhancedTableManager setOnServerSearch(Consumer<String> searchHandler) {
        super.setOnServerSearch(searchHandler);
        return this;
    }

    public EnhancedTableManager enableGroupStripingByHeader(String headerName) {
        super.enableGroupStripingByHeader(headerName);
        return this;
    }

    /**
     * Setzt die Datenbasis für den Client-Side-Modus.
     */
    public void populateTableView(List<RowData> data) {
        this.originalData = (data == null) ? new ArrayList<>() : new ArrayList<>(data);
        super.populateTableView(data);
    }

    public void configureGrouping(String headerName, Color colorA, Color colorB) {
        super.configureGrouping(headerName, colorA, colorB);
    }

    public void disableGrouping() {
        super.disableGrouping();
    }

    // ---------------- Buttons / Verdrahtung ----------------

    public void setCleanButton(Button cleanButton) {
        this.cleanColumnsButton = cleanButton;
        if (cleanButton != null) {
            cleanButton.setOnAction(e -> cleanColumnsAllPages());

            // ➜ Gemeinsame Deaktivierungslogik:
            // - keine Daten -> disabled
            // - bereits global bereinigt -> disabled
            // - globaler Job läuft (loading=true) -> disabled
            cleanButton.disableProperty().bind(
                    columnModel.cleanedProperty()
                            .or(hasDataProperty().not())
                            .or(resultModel.loadingProperty())
            );
        }
    }


    public void setExportCsvButton(Button b) {
        // Export-Buttons werden in CoverDomainController gebunden (keine Logik hier).
    }

    public void setExportXlsxButton(Button b) {
        // Export-Buttons werden in CoverDomainController gebunden (keine Logik hier).
    }

    // Legacy-Alias
    public void cleanTable() {
        cleanColumnsAllPages();
    }

    // -------------------------------------------------------------------------
    // Getter für andere Controller (AiAssistantViewController, DbExportViewController)
    // -------------------------------------------------------------------------

    /**
     * Liefert eine Kopie der aktuell gefilterten Daten (z.B. für KI/Export).
     */
    public List<RowData> getFilteredData() {
        return new ArrayList<>(filteredData);
    }

    /**
     * Liefert eine Kopie der Original-Daten-Basis (vor Client-Side-Filterung).
     */
    public List<RowData> getOriginalData() {
        return new ArrayList<>(originalData);
    }

    // -------------------------------------------------------------------------
    // Interne Hilfsmethoden (Delete / Context-Menü)
    // -------------------------------------------------------------------------

    /**
     * Diese Methode wird sowohl intern (Context-Menü) als auch von einigen
     * Controllern direkt aufgerufen, daher ist sie öffentlich.
     */
    public void handleDeleteSelectedColumns() {
        if (!selectionEnabled || deleteButton == null) return;

        ObservableList<TablePosition> selectedCells = tableView.getSelectionModel().getSelectedCells();
        if (selectedCells == null || selectedCells.isEmpty()) return;

        Set<TableColumn<?, ?>> columnsToDelete = new HashSet<>();
        for (TablePosition<?, ?> pos : selectedCells) {
            TableColumn<?, ?> col = pos.getTableColumn();
            if (col != null) {
                columnsToDelete.add(col);
            }
        }
        if (columnsToDelete.isEmpty()) {
            return;
        }

        List<String> headerNames = columnsToDelete.stream()
                .map(TableColumn::getText)
                .filter(Objects::nonNull)
                .filter(h -> !h.isBlank())
                .toList();

        // ➜ common helper in AbstractTableManager
        if (!confirmDeleteColumns(headerNames, columnsToDelete.size())) {
            return;
        }

        deleteColumns(new ArrayList<>(columnsToDelete));
    }


    private void deleteColumns(List<TableColumn<?, ?>> columnsToDelete) {
        Set<String> originalKeysToDelete = columnsToDelete.stream()
                .map(col -> String.valueOf(col.getUserData()))
                .collect(Collectors.toSet());

        // Klassisches Verhalten: nur ColumnStateModel.hiddenKeys setzen
        Platform.runLater(() -> originalKeysToDelete.forEach(columnModel::addHiddenKey));
    }

    private void deleteColumn(TableColumn<ObservableList<String>, String> column) {
        if (Dialog.showWarningDialog("Spalte löschen",
                "Möchten Sie die Spalte '" + column.getText() + "' wirklich löschen?")) {
            // Einzelelement-Liste erzeugen und an deleteColumns übergeben
            List<TableColumn<?, ?>> cols = new ArrayList<>();
            cols.add(column);
            deleteColumns(cols);
        }
    }

    /**
     * Setzt eine gemeinsame Map für Spalten-Anzeigenamen.
     * <p>
     * Beide Manager (Table und Tree) erhalten dieselbe Map-Instanz.
     * Wenn ein Benutzer eine Spalte umbenennt, wird der Eintrag in dieser Map
     * aktualisiert und alle Listener (Table/Tree) passen ihre Headertexte an.
     * </p>
     *
     * @param map ObservableMap mit Key = technischem Spaltennamen
     *            und Value = angezeigtem Spaltennamen.
     */
    public void setSharedColumnDisplayNames(javafx.collections.ObservableMap<String, String> map) {
        this.sharedColumnDisplayNames = map;
        if (map != null) {
            map.addListener((javafx.collections.MapChangeListener<String, String>) change -> {
                applySharedDisplayNames();
            });
        }
    }

    /**
     * Wendet die in {@link #sharedColumnDisplayNames} hinterlegten Anzeigenamen
     * auf alle aktuellen TableColumns an.
     */
    private void applySharedDisplayNames() {
        if (sharedColumnDisplayNames == null) {
            return;
        }
        for (TableColumn<ObservableList<String>, ?> col : tableView.getColumns()) {
            Object ud = col.getUserData();
            if (ud == null) continue;
            String key = String.valueOf(ud);
            String name = sharedColumnDisplayNames.get(key);
            if (name != null && !name.isBlank()) {
                col.setText(name);
            }
        }
    }


    private void addContextMenuToColumn(TableColumn<ObservableList<String>, String> column) {
        MenuItem renameItem = new MenuItem("Spalte umbenennen");
        MenuItem deleteItem = new MenuItem("Spalte löschen");

        // ➜ NEU: Format-Untermenü
        Menu formatMenu = new Menu("Spalte formatieren");
        MenuItem moneyItem = new MenuItem("Währung (€)");
        MenuItem dateItem = new MenuItem("Datum");
        MenuItem sbItem = new MenuItem("SB (Voll. Name)");
        MenuItem noneItem = new MenuItem("Kein Format");

        moneyItem.setOnAction(e -> applyFormatAndPersist(column, "MONEY"));
        dateItem.setOnAction(e -> applyFormatAndPersist(column, "DATE"));
        sbItem.setOnAction(e -> applyFormatAndPersist(column, "SB"));
        noneItem.setOnAction(e -> applyFormatAndPersist(column, "NONE"));

        renameItem.setOnAction(e -> renameColumn(column));
        deleteItem.setOnAction(e -> deleteColumn(column));

        formatMenu.getItems().addAll(moneyItem, dateItem, sbItem, new SeparatorMenuItem(), noneItem);

        ContextMenu cm = new ContextMenu(renameItem, new SeparatorMenuItem(), deleteItem, new SeparatorMenuItem(), formatMenu);
        column.setContextMenu(cm);

        // Couleurs (déjà demandé)
        renameItem.setStyle("-fx-text-fill: #2563eb;");
        deleteItem.setStyle("-fx-text-fill: #dc2626;");
    }

    private void applyFormatAndPersist(TableColumn<ObservableList<String>, String> column, String type) {
        String originalKey = String.valueOf(column.getUserData());
        String headerText = column.getText();
        try {
            FormatterService.setColumnFormat(originalKey, headerText, type);
            // Recharger la config dans les singletons et rafraîchir l’UI
            FormatterService.reloadRuntimeConfig();
            requestRefresh();                // Table
            // pour TreeTableManager: treeTableView.refresh() ou manager.requestRefresh()
            log.info("Format gesetzt: {} -> {}", originalKey, type);
        } catch (Exception ex) {
            Dialog.showErrorDialog("Format speichern", "Konnte Format nicht speichern: " + ex.getMessage());
        }
    }


    private void renameColumn(TableColumn<ObservableList<String>, String> column) {
        TextInputDialog dialog = new TextInputDialog(column.getText());
        dialog.setTitle("Spalte umbenennen");
        dialog.setHeaderText("Neuer Name für '" + column.getText() + "':");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.isBlank()) {
                column.setText(newName);

                // Gemeinsame Map aktualisieren, damit TreeTable dieselbe Umbenennung sieht
                Object ud = column.getUserData();
                if (sharedColumnDisplayNames != null && ud != null) {
                    sharedColumnDisplayNames.put(String.valueOf(ud), newName);
                }

                tableView.refresh();
                log.info("Spalte umbenannt: {} -> {}", column.getUserData(), newName);
            }

        });
    }

    // -------------------------------------------------------------------------
    // AbstractTableManager: Implementierung der Hooks
    // -------------------------------------------------------------------------

    @Override
    protected List<RowData> getOriginalDataForClientFilter() {
        return originalData;
    }

    @Override
    protected void configureSearchSection(boolean visible) {
        // Suchbereich (TextField, Label) wird in der Regel über FXML/Controller gesteuert.
    }

    @Override
    protected List<String> currentHeaders() {
        return currentHeaders;
    }

    @Override
    protected int getVisibleRowCount() {
        return (tableView.getItems() == null) ? 0 : tableView.getItems().size();
    }

    @Override
    protected String getVisibleCellValue(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || columnIndex < 0) return null;
        var items = tableView.getItems();
        if (items == null || rowIndex >= items.size()) return null;
        var row = items.get(rowIndex);
        return (row != null && columnIndex < row.size()) ? row.get(columnIndex) : null;
    }

    /**
     * RowFactory, die Gruppen-Streifen (abwechselnde Farben) visualisiert,
     * wenn in {@link AbstractTableManager} eine Gruppierung konfiguriert ist.
     */
    @Override
    protected void installGroupRowFactory() {
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ObservableList<String> item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("");

                if (empty || item == null || isSelected()) {
                    return;
                }
                if (!groupStripingEnabled || groupStripingHeader == null) {
                    return;
                }
                if (groupColorA == null && groupColorB == null) {
                    return;
                }

                int rowIndex = getIndex();
                if (rowIndex >= 0 && rowIndex < stripeIsA.size()) {
                    boolean isA = stripeIsA.get(rowIndex);
                    Color c = isA ? groupColorA : groupColorB;
                    if (c != null) {
                        int r = (int) Math.round(c.getRed() * 255);
                        int g = (int) Math.round(c.getGreen() * 255);
                        int b = (int) Math.round(c.getBlue() * 255);
                        String a = String.format(Locale.US, "%.3f", c.getOpacity());
                        setStyle("-fx-background-color: rgba(" + r + "," + g + "," + b + "," + a + ");");
                    }
                }
            }
        });
    }

    /**
     * Baut die sichtbaren Spalten und befüllt die TableView mit den aktuellen
     * gefilterten Daten. Berücksichtigt Client-Side-Pagination.
     */
    @Override
    protected void refreshView() {
        Set<String> hiddenKeys = columnModel.getHiddenKeys();

        buildTableColumns(filteredData, hiddenKeys);
        populateTableData(filteredData);

        // ✅ FORCER LE REDIMENSIONNEMENT APRÈS MISE À JOUR
        Platform.runLater(this::resizeColumnsToFitContent);

        if (!serverPaginationEnabled && pagination != null && paginationEnabled) {
            int rowsPerPage = stateModel.getRowsPerPage();
            int pageCount = (rowsPerPage <= 0)
                    ? 1
                    : (int) Math.ceil(filteredData.size() / (double) rowsPerPage);
            pagination.setPageCount(Math.max(1, pageCount));
            pagination.setCurrentPageIndex(0);
            pagination.setVisible(!filteredData.isEmpty());
            pagination.setPageFactory(this::createClientPage);
        }

        recomputeGroupStripes();
        updateResultsCount();
    }

    private Node createClientPage(int pageIndex) {
        int rowsPerPage = stateModel.getRowsPerPage();
        int fromIndex = pageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredData.size());
        List<RowData> pageData = filteredData.subList(fromIndex, toIndex);

        ObservableList<ObservableList<String>> tableData = FXCollections.observableArrayList();
        for (RowData row : pageData) {
            ObservableList<String> rowValues = FXCollections.observableArrayList();
            for (String header : currentHeaders) {
                String formattedValue = ColumnValueFormatter.format(row, header);
                rowValues.add(formattedValue);
            }
            tableData.add(rowValues);
        }

        tableView.setItems(tableData);
        return tableView;
    }

    private void buildTableColumns(List<RowData> data, Set<String> hiddenKeys) {
        if (data == null || data.isEmpty()) {
            tableView.getColumns().clear();
            currentHeaders = List.of();
            return;
        }

        List<String> allHeaders = new ArrayList<>(data.get(0).getValues().keySet());
        List<String> visibleHeaders = allHeaders.stream()
                .filter(h -> hiddenKeys == null || !hiddenKeys.contains(h))
                .collect(Collectors.toList());

        if (visibleHeaders.equals(currentHeaders) && !tableView.getColumns().isEmpty()) {
            return;
        }

        tableView.getColumns().clear();
        currentHeaders = visibleHeaders;

        for (int i = 0; i < visibleHeaders.size(); i++) {
            final int columnIndex = i;
            final String originalKey = visibleHeaders.get(i);

            String headerText = originalKey;
            if (sharedColumnDisplayNames != null) {
                String mapped = sharedColumnDisplayNames.get(originalKey);
                if (mapped != null && !mapped.isBlank()) {
                    headerText = mapped;
                }
            }

            TableColumn<ObservableList<String>, String> column = new TableColumn<>(headerText);
            column.setUserData(originalKey);

            // ✅ Largeurs pour éviter tronquage
            column.setPrefWidth(150);
            column.setMinWidth(80);

            column.setCellValueFactory(param -> {
                ObservableList<String> row = param.getValue();
                return new SimpleStringProperty(
                        (row != null && columnIndex < row.size()) ? row.get(columnIndex) : ""
                );
            });

            final String headerAlias = originalKey;

            column.setCellFactory(tc -> new javafx.scene.control.cell.TextFieldTableCell<ObservableList<String>, String>() {
                @Override
                public void updateItem(String item, boolean empty) { // <-- public
                    super.updateItem(item, empty);
                    setText(empty ? null : formatter.ColumnValueFormatter.displayOnly(headerAlias, item));
                }
            });

            addContextMenuToColumn(column);
            tableView.getColumns().add(column);
        }
    }

    private void populateTableData(List<RowData> data) {
        if (data == null || data.isEmpty() || currentHeaders.isEmpty()) {
            tableView.setItems(FXCollections.observableArrayList());
            return;
        }

        ObservableList<ObservableList<String>> tableData = FXCollections.observableArrayList();

        for (RowData row : data) {
            ObservableList<String> rowValues = FXCollections.observableArrayList();
            for (String header : currentHeaders) {
                String formattedValue = ColumnValueFormatter.format(row, header);
                rowValues.add(formattedValue);
            }
            tableData.add(rowValues);
        }
        tableView.setItems(tableData);
    }

    @Override
    protected void updateResultsCount() {
        if (resultsCountLabel == null) return;

        int countToDisplay = serverPaginationEnabled
                ? resultModel.getTotalCount()
                : filteredData.size();

        resultsCountLabel.setText(countToDisplay + " Ergebnisse");
    }

    @Override
    protected void clearView() {
        tableView.getColumns().clear();
        tableView.setItems(FXCollections.observableArrayList());
        currentHeaders = List.of();
        filteredData = new ArrayList<>();
        originalData = new ArrayList<>();
        if (pagination != null) pagination.setVisible(false);
    }

    @Override
    public void requestRefresh() {
        tableView.refresh();
    }

    @Override
    protected void disableCleanButtonOnCleaned(javafx.collections.SetChangeListener.Change<? extends String> c) {
        // De-/Aktivierung des Clean-Buttons erfolgt in setCleanButton()
    }

    // -------------------------------------------------------------------------
    // Export-Schnittstelle (für Controller)
    // -------------------------------------------------------------------------

    @Override
    public List<String> getDisplayHeaders() {
        return tableView.getColumns().stream().map(TableColumn::getText).collect(Collectors.toList());
    }

    @Override
    public List<String> getOriginalKeys() {
        return tableView.getColumns().stream()
                .map(col -> String.valueOf(col.getUserData()))
                .collect(Collectors.toList());
    }
}
