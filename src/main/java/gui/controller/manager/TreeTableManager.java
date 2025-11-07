package gui.controller.manager;

import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
import gui.controller.manager.DataLoader;
import gui.controller.manager.base.AbstractTableManager;
import gui.controller.model.ColumnStateModel;
import gui.controller.model.ResultContextModel;
import gui.controller.model.TableStateModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.paint.Color;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Manager für eine gruppierte {@link TreeTableView} auf Basis von
 * {@code ObservableList<String>} als Zeilenmodell.
 *
 * <p>
 * Nutzt die gemeinsame Logik aus {@link AbstractTableManager} (Suche,
 * Pagination, globales Bereinigen, Gruppierungs-Streifen) und baut
 * daraus eine hierarchische Darstellung (Gruppen + Detailzeilen).
 * </p>
 */
public class TreeTableManager extends AbstractTableManager {

    private static final Logger log = LoggerFactory.getLogger(TreeTableManager.class);

    // UI
    private final TreeTableView<ObservableList<String>> treeTableView;
    private final Button deleteColumnsButton;
    private Button cleanColumnsButton;
    private Button expandAllButton;
    private Button collapseAllButton;

    // Export-Hooks (werden vom Controller gesetzt)
    private Runnable onExportCsv;
    private Runnable onExportXlsx;

    // Gruppierungspfad: RowData -> Liste von Segmenten (Makler, Gesellschaft, ...)
    private Function<RowData, List<String>> groupingPathProvider = r -> List.of("Alle");

    // Steuerung von expandAll/collapseAll
    private final AtomicLong expandOpSeq = new AtomicLong(0);
    private volatile Boolean globalExpand = null;

    // Auswahl
    private boolean selectionEnabled = false;

    // Root-Item
    private TreeItem<ObservableList<String>> rootItem = new TreeItem<>(emptyRow());

    // Gemeinsame Map für Spaltennamen, wird mit EnhancedTableManager geteilt
    private javafx.collections.ObservableMap<String, String> sharedColumnDisplayNames;


    // -------------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------------

    public TreeTableManager(TreeTableView<ObservableList<String>> treeTableView,
                            TextField searchField,
                            Button deleteColumnsButton,
                            Pagination pagination,
                            Label resultsCountLabel) {
        this(treeTableView, searchField, deleteColumnsButton, pagination, resultsCountLabel,
                new ColumnStateModel(), new ResultContextModel(), new TableStateModel());
        log.warn("TreeTableManager ohne explizite Models instanziiert – lokale Modelle erzeugt. " +
                "Nur für Tests/Legacy-Kontext empfohlen.");
    }

    public TreeTableManager(TreeTableView<ObservableList<String>> treeTableView,
                            TextField searchField,
                            Button deleteColumnsButton,
                            Pagination pagination,
                            Label resultsCountLabel,
                            ColumnStateModel columnStateModel,
                            ResultContextModel resultContextModel,
                            TableStateModel tableStateModel) {
        super(searchField, pagination, resultsCountLabel, tableStateModel, columnStateModel, resultContextModel);
        this.treeTableView = Objects.requireNonNull(treeTableView, "treeTableView");
        this.deleteColumnsButton = deleteColumnsButton;

        initTree();
        installSelectionSupport();
        installRowStyling();
        installGroupRowFactory();
    }

    /**
     * Setzt eine gemeinsame Map für Spalten-Anzeigenamen.
     * <p>
     * Beide Manager (Table und Tree) erhalten dieselbe Map-Instanz.
     * Änderungen an der Map werden per Listener in dieser View reflektiert.
     * </p>
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
     * auf alle TreeTableColumns an.
     */
    private void applySharedDisplayNames() {
        if (sharedColumnDisplayNames == null) {
            return;
        }
        for (TreeTableColumn<ObservableList<String>, ?> col : treeTableView.getColumns()) {
            Object ud = col.getUserData();
            if (ud == null) continue;
            String key = String.valueOf(ud);
            String name = sharedColumnDisplayNames.get(key);
            if (name != null && !name.isBlank()) {
                col.setText(name);
            }
        }
    }



    // -------------------------------------------------------------------------
    // Öffentliche API (kompatibel)
    // -------------------------------------------------------------------------

    public void loadDataFromServer(int totalCount, DataLoader dataLoader, Function<RowData, List<String>> provider) {
        this.groupingPathProvider = (provider != null) ? provider : (r -> List.of("Alle"));
        super.loadDataFromServer(totalCount, dataLoader);
    }

    public ReadOnlyBooleanProperty hasDataProperty() { return super.hasDataProperty(); }
    public boolean hasData() { return super.hasData(); }

    public TreeTableManager enableSearch() {
        super.enableSearch();
        return this;
    }

    public TreeTableManager enablePagination(int rowsPerPage) {
        super.enablePagination(rowsPerPage);
        return this;
    }

    public TreeTableManager setOnServerSearch(Consumer<String> handler) {
        super.setOnServerSearch(handler);
        return this;
    }

    public TreeTableManager enableSelection() {
        if (deleteColumnsButton == null) {
            log.warn("Selection angefordert, aber kein Delete-Button vorhanden.");
            return this;
        }
        selectionEnabled = true;
        treeTableView.getSelectionModel().setCellSelectionEnabled(true);
        treeTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        treeTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) ->
                deleteColumnsButton.setDisable(newSel == null)
        );
        deleteColumnsButton.setOnAction(e -> handleDeleteSelectedColumns());
        return this;
    }

    public void setCleanButton(Button cleanButton) {
        this.cleanColumnsButton = cleanButton;
        if (cleanButton != null) {
            cleanButton.setOnAction(e -> cleanColumnsAllPages());
            columnModel.cleanedProperty().addListener((obs, oldV, newV) ->
                    cleanColumnsButton.setDisable(newV || !hasData())
            );
            hasDataProperty().addListener((obs, oldV, newV) ->
                    cleanColumnsButton.setDisable(columnModel.isCleaned() || !newV)
            );
            cleanColumnsButton.setDisable(!hasData() || columnModel.isCleaned());
        }
    }

    public void setOnExportCsv(Runnable r) { this.onExportCsv = r; }
    public void setOnExportXlsx(Runnable r) { this.onExportXlsx = r; }

    public void setExportCsvButton(Button b) {
        if (b != null) {
            b.disableProperty().bind(hasDataProperty().not());
            b.setOnAction(e -> { if (onExportCsv != null) onExportCsv.run(); });
        }
    }

    public void setExportXlsxButton(Button b) {
        if (b != null) {
            b.disableProperty().bind(hasDataProperty().not());
            b.setOnAction(e -> { if (onExportXlsx != null) onExportXlsx.run(); });
        }
    }

    public void setExpandAllButton(Button b) {
        this.expandAllButton = b;
        if (b != null) {
            b.setOnAction(e -> expandAll());
        }
    }

    public void setCollapseAllButton(Button b) {
        this.collapseAllButton = b;
        if (b != null) {
            b.setOnAction(e -> collapseAll());
        }
    }

    // -------------------------------------------------------------------------
    // Initialisierung Tree
    // -------------------------------------------------------------------------

    private void initTree() {
        treeTableView.setRoot(rootItem);
        treeTableView.setShowRoot(false);
        treeTableView.setFixedCellSize(24);
    }

    private void installSelectionSupport() {
        // Zusätzliche Selektion-Logik könnte hier ergänzt werden
    }

    /**
     * Styling von Gruppen- und Datenzeilen.
     */
    private void installRowStyling() {
        treeTableView.setRowFactory(tv -> new TreeTableRow<>() {
            @Override
            protected void updateItem(ObservableList<String> item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("group-row");
                setStyle("");

                if (empty || item == null) {
                    return;
                }

                TreeItem<ObservableList<String>> treeItem = getTreeItem();
                if (treeItem == null) return;

                if (isGroupItem(treeItem)) {
                    getStyleClass().add("group-row");
                    setStyle("-fx-font-weight: bold;");
                } else if (groupStripingEnabled && groupStripingHeader != null) {
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
            }
        });
    }

    /**
     * Ermittelt, ob ein TreeItem eine Gruppenzeile darstellt.
     * Kennzeichnung über {@code graphic.userData == Boolean.TRUE}.
     */
    private boolean isGroupItem(TreeItem<ObservableList<String>> item) {
        if (item == null) return false;
        if (item.getGraphic() == null) return false;
        Object ud = item.getGraphic().getUserData();
        return Boolean.TRUE.equals(ud);
    }

    private ObservableList<String> emptyRow() {
        return FXCollections.observableArrayList();
    }

    // -------------------------------------------------------------------------
    // Datenaufbau / Gruppierung
    // -------------------------------------------------------------------------

    @Override
    protected void refreshView() {
        Set<String> hiddenKeys = columnModel.getHiddenKeys();
        TreeItem<ObservableList<String>> newRoot = buildTreeAndColumns(filteredData, hiddenKeys);
        applyRoot(newRoot);
        restoreExpansion(newRoot);
        recomputeGroupStripes();
        updateResultsCount();
    }

    private TreeItem<ObservableList<String>> buildTreeAndColumns(List<RowData> rows, Set<String> hiddenKeys) {
        if (rows == null || rows.isEmpty()) {
            treeTableView.getColumns().clear();
            currentHeaders = List.of();
            return new TreeItem<>(emptyRow());
        }

        // 1) sichtbare Header
        List<String> allHeaders = new ArrayList<>(rows.get(0).getValues().keySet());
        List<String> visibleHeaders = allHeaders.stream()
                .filter(h -> hiddenKeys == null || !hiddenKeys.contains(h))
                .collect(Collectors.toList());

        if (!visibleHeaders.equals(currentHeaders)) {
            currentHeaders = visibleHeaders;
            rebuildColumns(visibleHeaders);
        }

        // 2) Baumstruktur anhand groupingPathProvider
        Map<List<String>, List<RowData>> groups = rows.stream()
                .collect(Collectors.groupingBy(r -> {
                    try {
                        return groupingPathProvider.apply(r);
                    } catch (Exception ex) {
                        log.error("Fehler beim Ermitteln des Gruppierungspfads", ex);
                        return List.of("Alle");
                    }
                }));

        TreeItem<ObservableList<String>> root = new TreeItem<>(emptyRow());
        root.setExpanded(true);

        List<List<String>> sortedKeys = new ArrayList<>(groups.keySet());
        sortedKeys.sort(Comparator.comparing(a -> String.join(" / ", a)));

        for (List<String> path : sortedKeys) {
            TreeItem<ObservableList<String>> groupNode = createGroupPath(root, path, visibleHeaders);
            List<RowData> groupRows = groups.getOrDefault(path, List.of());
            for (RowData row : groupRows) {
                ObservableList<String> rowValues = FXCollections.observableArrayList();
                for (String header : visibleHeaders) {
                    String formattedValue = ColumnValueFormatter.format(row, header);
                    rowValues.add(formattedValue);
                }
                TreeItem<ObservableList<String>> rowItem = new TreeItem<>(rowValues);
                groupNode.getChildren().add(rowItem);
            }
        }

        return root;
    }

    private void rebuildColumns(List<String> visibleHeaders) {
        treeTableView.getColumns().clear();

        for (int i = 0; i < visibleHeaders.size(); i++) {
            final int colIndex = i;
            final String header = visibleHeaders.get(i);
            String headerText = header;
            if (sharedColumnDisplayNames != null) {
                String mapped = sharedColumnDisplayNames.get(header);
                if (mapped != null && !mapped.isBlank()) {
                    headerText = mapped;
                }
            }

            TreeTableColumn<ObservableList<String>, String> col = new TreeTableColumn<>(headerText);
            col.setUserData(header);

            col.setCellValueFactory(param -> {
                ObservableList<String> row = param.getValue().getValue();
                if (row == null || colIndex >= row.size()) {
                    return new ReadOnlyStringWrapper("");
                }
                return new ReadOnlyStringWrapper(row.get(colIndex));
            });

            col.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
            addContextMenuToColumn(col);
            treeTableView.getColumns().add(col);
        }
        applySharedDisplayNames();
    }

    private TreeItem<ObservableList<String>> createGroupPath(TreeItem<ObservableList<String>> root,
                                                             List<String> path,
                                                             List<String> visibleHeaders) {
        TreeItem<ObservableList<String>> current = root;
        for (String segment : path) {
            Optional<TreeItem<ObservableList<String>>> existing = current.getChildren().stream()
                    .filter(child -> isGroupItem(child)
                            && !child.getChildren().isEmpty()
                            && Objects.equals(child.getValue().get(0), segment))
                    .findFirst();

            if (existing.isPresent()) {
                current = existing.get();
            } else {
                ObservableList<String> groupRow = FXCollections.observableArrayList();
                if (!visibleHeaders.isEmpty()) {
                    groupRow.add(segment);
                    IntStream.range(1, visibleHeaders.size()).forEach(i -> groupRow.add(""));
                }

                TreeItem<ObservableList<String>> groupItem = new TreeItem<>(groupRow);
                Label marker = new Label();
                marker.setUserData(Boolean.TRUE);
                groupItem.setGraphic(marker);
                groupItem.setExpanded(false);
                current.getChildren().add(groupItem);
                current = groupItem;
            }
        }
        return current;
    }

    private void applyRoot(TreeItem<ObservableList<String>> newRoot) {
        treeTableView.setRoot(newRoot);
        rootItem = newRoot;
        treeTableView.setShowRoot(false);
    }

    private void restoreExpansion(TreeItem<ObservableList<String>> root) {
        if (root == null) return;

        final Boolean ge = this.globalExpand;
        if (Boolean.TRUE.equals(ge)) {
            setExpandedRecursively(root, true);
        } else if (Boolean.FALSE.equals(ge)) {
            setExpandedRecursively(root, false);
        }
    }

    // -------------------------------------------------------------------------
    // Expand / Collapse
    // -------------------------------------------------------------------------

    private void setExpandButtonsDisabled(boolean disabled) {
        if (expandAllButton != null) expandAllButton.setDisable(disabled);
        if (collapseAllButton != null) collapseAllButton.setDisable(disabled);
    }

    public void expandAll() {
        if (filteredData == null || filteredData.isEmpty()) return;

        long mySeq = expandOpSeq.incrementAndGet();
        if (globalExpand == Boolean.TRUE && expandTaskRunning) return;
        expandTaskRunning = true;
        globalExpand = Boolean.TRUE;

        setExpandButtonsDisabled(true);
        resultModel.setLoading(true);

        EXECUTOR.submit(() -> {
            try {
                List<RowData> allRows = new ArrayList<>();
                int total = resultModel.getTotalCount();
                int pageSize = stateModel.getRowsPerPage();
                int pages = (pageSize <= 0)
                        ? 1
                        : (int) Math.ceil(total / (double) pageSize);

                for (int p = 0; p < pages; p++) {
                    if (expandOpSeq.get() != mySeq) {
                        log.info("expandAll(): abgebrochen wegen neuer Operation.");
                        return;
                    }
                    try {
                        List<RowData> pageRows = resultModel.getPageLoader().loadPage(p, pageSize);
                        allRows.addAll(pageRows);
                    } catch (Exception ex) {
                        log.error("expandAll(): Fehler beim Nachladen Seite {}", p, ex);
                    }
                }

                Platform.runLater(() -> {
                    TreeItem<ObservableList<String>> newRoot = buildTreeAndColumns(allRows, columnModel.getHiddenKeys());
                    applyRoot(newRoot);
                    setExpandedRecursively(newRoot, true);
                });
            } finally {
                Platform.runLater(() -> {
                    resultModel.setLoading(false);
                    setExpandButtonsDisabled(false);
                    expandTaskRunning = false;
                });
            }
        });
    }

    public void collapseAll() {
        globalExpand = Boolean.FALSE;
        if (rootItem == null) return;
        setExpandedRecursively(rootItem, false);
    }

    private volatile boolean expandTaskRunning = false;

    private void setExpandedRecursively(TreeItem<ObservableList<String>> item, boolean expanded) {
        if (item == null) return;
        item.setExpanded(expanded);
        for (TreeItem<ObservableList<String>> child : item.getChildren()) {
            setExpandedRecursively(child, expanded);
        }
    }

    // -------------------------------------------------------------------------
    // Kontext-Menü (Rename / Delete)
    // -------------------------------------------------------------------------

    private void addContextMenuToColumn(TreeTableColumn<ObservableList<String>, String> column) {
        MenuItem renameItem = new MenuItem("Spalte umbenennen");
        MenuItem deleteItem = new MenuItem("Spalte löschen");

        renameItem.setStyle("-fx-text-fill: #2563eb;");
        deleteItem.setStyle("-fx-text-fill: #dc2626;");

        renameItem.setOnAction(e -> renameColumn(column));
        deleteItem.setOnAction(e -> deleteColumn(column));

        ContextMenu contextMenu = new ContextMenu(renameItem, new SeparatorMenuItem(), deleteItem);
        column.setContextMenu(contextMenu);
    }

    private void renameColumn(TreeTableColumn<ObservableList<String>, String> column) {
        TextInputDialog dialog = new TextInputDialog(column.getText());
        dialog.setTitle("Spalte umbenennen");
        dialog.setHeaderText("Neuer Name für '" + column.getText() + "':");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.isBlank()) {
                column.setText(newName);

                // Gemeinsame Map aktualisieren, damit TableView dieselbe Umbenennung sieht
                Object ud = column.getUserData();
                if (sharedColumnDisplayNames != null && ud != null) {
                    sharedColumnDisplayNames.put(String.valueOf(ud), newName);
                }

                treeTableView.refresh();
                log.info("Tree-Spalte umbenannt: {} -> {}", column.getUserData(), newName);
            }

        });
    }

    private void deleteColumn(TreeTableColumn<ObservableList<String>, String> column) {
        if (Dialog.showWarningDialog("Spalte löschen",
                "Möchten Sie die Spalte '" + column.getText() + "' wirklich löschen?")) {
            deleteColumns(List.of(column));
        }
    }

    private void handleDeleteSelectedColumns() {
        if (!selectionEnabled || deleteColumnsButton == null) return;
        var selectedCells = treeTableView.getSelectionModel().getSelectedCells();
        if (selectedCells == null || selectedCells.isEmpty()) return;

        Set<TreeTableColumn<ObservableList<String>, ?>> cols = selectedCells.stream()
                .map(TreeTablePosition::getTableColumn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        deleteColumns(new ArrayList<>(cols));
    }

    private void deleteColumns(List<TreeTableColumn<ObservableList<String>, ?>> columns) {
        Set<String> keys = columns.stream()
                .map(c -> String.valueOf(c.getUserData()))
                .collect(Collectors.toSet());

        Platform.runLater(() -> keys.forEach(columnModel::addHiddenKey));
    }

    // -------------------------------------------------------------------------
    // AbstractTableManager Hooks
    // -------------------------------------------------------------------------

    @Override
    protected List<RowData> getOriginalDataForClientFilter() {
        // Tree nutzt aktuell nur Server-Seite; für Client-Filter könnte hier
        // eine separate Datenbasis hinterlegt werden.
        return filteredData;
    }

    @Override
    protected void configureSearchSection(boolean visible) {
        // Search-Section wird über FXML/Builder gesteuert.
    }

    @Override
    protected List<String> currentHeaders() {
        return currentHeaders;
    }

    @Override
    protected int getVisibleRowCount() {
        return (treeTableView.getRoot() == null) ? 0 : treeTableView.getExpandedItemCount();
    }

    @Override
    protected String getVisibleCellValue(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || columnIndex < 0) return null;
        if (treeTableView.getRoot() == null) return null;
        if (rowIndex >= treeTableView.getExpandedItemCount()) return null;

        TreeItem<ObservableList<String>> item = treeTableView.getTreeItem(rowIndex);
        if (item == null || item.getValue() == null) return null;
        ObservableList<String> row = item.getValue();
        if (columnIndex >= row.size()) return null;
        return row.get(columnIndex);
    }

    @Override
    protected void installGroupRowFactory() {
        // Visuelles Group-Striping übernimmt installRowStyling().
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
        treeTableView.getColumns().clear();
        treeTableView.setRoot(new TreeItem<>(emptyRow()));
        currentHeaders = List.of();
        filteredData = new ArrayList<>();
        if (pagination != null) pagination.setVisible(false);
    }

    @Override
    protected void requestRefresh() {
        treeTableView.refresh();
    }

    @Override
    public List<String> getDisplayHeaders() {
        return treeTableView.getColumns().stream()
                .map(TreeTableColumn::getText)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getOriginalKeys() {
        return treeTableView.getColumns().stream()
                .map(c -> String.valueOf(c.getUserData()))
                .collect(Collectors.toList());
    }

    @Override
    protected void disableCleanButtonOnCleaned(javafx.collections.SetChangeListener.Change<? extends String> c) {
        // Gesteuert über setCleanButton()
    }
}
