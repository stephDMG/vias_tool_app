package gui.controller.manager;

import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Universeller Manager für TreeTableView&lt;ObservableList&lt;String&gt;&gt;.
 *
 * <p><b>Funktionen:</b></p>
 * <ul>
 *   <li>Suche (clientseitig)</li>
 *   <li>Gruppierung via Pfad-Provider ({@code Function<RowData, List<String>>})</li>
 *   <li>Pagination (Client/Server) + automatische Zeilenanzahl je nach Höhe</li>
 *   <li>Expand/Collapse</li>
 *   <li>Bereinigung leerer Spalten (sichtbare Seite)</li>
 *   <li>Kontextmenü pro Spalte: Spalte umbenennen / Spalte löschen</li>
 *   <li>Export-Hooks (CSV/XLSX)</li>
 * </ul>
 *
 * <p>Kompatibel zur Logik von {@link EnhancedTableManager} (Suche, Auswahl/Löschen,
 * Clean, Pagination). Bewusst stabil gehalten, um bestehende Controller nicht zu brechen.</p>
 */
public class TreeTableManager {

    private static final Logger log = LoggerFactory.getLogger(TreeTableManager.class);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final int DEFAULT_ROWS_PER_PAGE = 100;

    // UI
    private final TreeTableView<ObservableList<String>> treeTableView;
    private final TextField searchField;
    private final Button deleteColumnsButton;
    private final Pagination pagination;
    private final Label resultsCountLabel;

    private Button cleanColumnsButton;
    private Button expandAllButton;
    private Button collapseAllButton;
    private Button exportCsvButton;
    private Button exportXlsxButton;

    private Runnable onExportCsv;
    private Runnable onExportXlsx;

    // State & Daten
    private boolean showRoot = false;
    private boolean autoExpandRoot = true;

    private boolean searchEnabled = false;
    private boolean selectionEnabled = false;
    private boolean paginationEnabled = false;
    private boolean serverPaginationEnabled = false;

    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;
    private Integer lastRowsPerPage = null;

    private List<RowData> originalData = new ArrayList<>();
    private List<RowData> filteredData = new ArrayList<>();
    private List<String> currentHeaders = new ArrayList<>();

    // Server-Pagination
    private int totalCount = 0;
    private DataLoader dataLoader = null;

    // Gruppierung per Pfad-Provider
    private Function<RowData, List<String>> groupingPathProvider = r -> List.of("Alle");

    private boolean cleanRanForThisPage = false;
    private boolean serverPagerInitialized = false;

    /** Konstruktor. */
    public TreeTableManager(TreeTableView<ObservableList<String>> treeTableView,
                            TextField searchField,
                            Button deleteColumnsButton,
                            Pagination pagination,
                            Label resultsCountLabel) {
        this.treeTableView = Objects.requireNonNull(treeTableView, "treeTableView");
        this.searchField = searchField;
        this.deleteColumnsButton = deleteColumnsButton;
        this.pagination = pagination;
        this.resultsCountLabel = resultsCountLabel;

        // Basis
        this.treeTableView.setShowRoot(showRoot);
        this.treeTableView.setFixedCellSize(24);
        installRowStyling();

        // Auto-Zeilenanzahl an Table-Höhe binden
        bindAutoRowsPerPage(this.treeTableView);

        if (deleteColumnsButton != null) {
            deleteColumnsButton.setDisable(true);
        }
    }

    // ---------------------------------------------------------
    // Öffentliche Konfiguration
    // ---------------------------------------------------------

    /** Aktiviert die clientseitige Suche. */
    public TreeTableManager enableSearch() {
        if (searchField == null) return this;
        searchEnabled = true;
        searchField.textProperty().addListener((obs, ov, nv) -> {
            if (serverPaginationEnabled) {
                log.warn("Serversuche nicht implementiert – im Controller filtern und neu laden.");
            } else {
                filterClientData(nv);
            }
        });
        return this;
    }

    /** Aktiviert Spaltenauswahl/Löschen. */
    public TreeTableManager enableSelection() {
        if (deleteColumnsButton == null) return this;
        selectionEnabled = true;
        treeTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        deleteColumnsButton.setDisable(false);
        deleteColumnsButton.setOnAction(e -> handleDeleteSelectedColumns());
        return this;
    }

    /** Aktiviert Client-Pagination. */
    public TreeTableManager enablePagination(int rowsPerPage) {
        if (pagination == null) return this;
        this.paginationEnabled = true;
        this.rowsPerPage = Math.max(1, rowsPerPage);
        paginationVisible(false);
        return this;
    }

    /** Aktiviert „Bereinigen“-Button (Parität zu Table-Manager). */
    public TreeTableManager enableCleanTable() {
        if (this.cleanColumnsButton != null) {
            this.cleanColumnsButton.setDisable(false);
            this.cleanColumnsButton.setOnAction(e -> cleanColumnsOnCurrentPage());
        }
        return this;
    }

    public void setAutoExpandRoot(boolean autoExpandRoot) { this.autoExpandRoot = autoExpandRoot; }
    public void setShowRoot(boolean showRoot) { this.showRoot = showRoot; this.treeTableView.setShowRoot(showRoot); }

    public void setCleanButton(Button cleanButton) {
        this.cleanColumnsButton = cleanButton;
        if (cleanButton != null) cleanButton.setOnAction(e -> cleanColumnsOnCurrentPage());
    }

    public void setExpandAllButton(Button btn) {
        this.expandAllButton = btn;
        if (btn != null) btn.setOnAction(e -> expandAll());
    }

    public void setCollapseAllButton(Button btn) {
        this.collapseAllButton = btn;
        if (btn != null) btn.setOnAction(e -> collapseAll());
    }

    public void setExportCsvButton(Button btn) {
        this.exportCsvButton = btn;
        if (btn != null) btn.setOnAction(e -> {
            if (onExportCsv != null) onExportCsv.run();
            else Dialog.showInfoDialog("Export CSV", "Kein CSV-Handler gesetzt (später implementieren).");
        });
    }

    public void setExportXlsxButton(Button btn) {
        this.exportXlsxButton = btn;
        if (btn != null) btn.setOnAction(e -> {
            if (onExportXlsx != null) onExportXlsx.run();
            else Dialog.showInfoDialog("Export XLSX", "Kein XLSX-Handler gesetzt (später implementieren).");
        });
    }

    public void setOnExportCsv(Runnable handler) { this.onExportCsv = handler; }
    public void setOnExportXlsx(Runnable handler) { this.onExportXlsx = handler; }

    /** Region für Auto-Row-Berechnung binden. */
    public void bindAutoRowsPerPage(Region observedRegion) {
        final double chrome = 90.0;
        final double defaultRowH = 24.0;
        final int minRows = 10;

        observedRegion.heightProperty().addListener((obs, oldH, newH) -> {
            double h = (newH == null) ? 0 : newH.doubleValue();
            double rowH = (treeTableView.getFixedCellSize() > 0) ? treeTableView.getFixedCellSize() : defaultRowH;
            int rows = (int) Math.max(minRows, Math.floor((h - chrome) / rowH));
            if (lastRowsPerPage == null || Math.abs(rows - lastRowsPerPage) >= 2) {
                lastRowsPerPage = rows;
                setRowsPerPage(rows);
            }
        });
    }

    public void setRowsPerPage(int rowsPerPage) {
        rowsPerPage = Math.max(1, rowsPerPage);
        if (this.rowsPerPage == rowsPerPage) return; // rien à faire → pas de blink
        this.rowsPerPage = rowsPerPage;
        if (serverPaginationEnabled) setupServerPagination();
        else refreshClient();
    }


    // ---------------------------------------------------------
    // Datenversorgung
    // ---------------------------------------------------------

    /** Gruppierung per Pfad-Provider setzen. */
    public void setGroupingPathProvider(Function<RowData, List<String>> provider) {
        this.groupingPathProvider = (provider != null) ? provider : (r -> List.of("Alle"));
    }

    /** 2-Arg-Variante (nutzt aktuellen Provider). */
    public void loadDataFromServer(int totalCount, DataLoader dataLoader) {
        loadDataFromServer(totalCount, dataLoader, this.groupingPathProvider);
    }

    /** 3-Arg-Variante (Provider direkt angeben). */
    public void loadDataFromServer(int totalCount,
                                   DataLoader dataLoader,
                                   Function<RowData, List<String>> provider) {
        this.groupingPathProvider = (provider != null) ? provider : this.groupingPathProvider;
        this.serverPaginationEnabled = true;
        this.dataLoader = dataLoader;
        this.totalCount = totalCount;
        setupServerPagination();
    }

    // ---------------------------------------------------------
    // Client-Ansicht
    // ---------------------------------------------------------
    private void refreshClient() {
        updateResultsCountClient();
        List<RowData> toDisplay = filteredData;

        if (paginationEnabled && pagination != null) {
            int pageCount = (int) Math.ceil((double) toDisplay.size() / rowsPerPage);
            pagination.setPageCount(Math.max(pageCount, 1));
            pagination.setCurrentPageIndex(0);
            paginationVisible(toDisplay.size() > 0);
            pagination.setPageFactory(this::createClientPage);
        } else {
            buildColumnsFromRows(toDisplay);
            TreeItem<ObservableList<String>> root = buildTreeUsingPathProvider(toDisplay);
            applyRoot(root);
        }
    }

    private Node createClientPage(int pageIndex) {
        int from = pageIndex * rowsPerPage;
        int to = Math.min(from + rowsPerPage, filteredData.size());
        List<RowData> slice = (from < to) ? filteredData.subList(from, to) : List.of();

        buildColumnsFromRows(slice);
        TreeItem<ObservableList<String>> root = buildTreeUsingPathProvider(slice);
        applyRoot(root);
        return new Label();
    }

    private void filterClientData(String text) {
        String q = (text == null) ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filteredData = new ArrayList<>(originalData);
        } else {
            filteredData = originalData.stream()
                    .filter(r -> r.getValues().values().stream()
                            .filter(Objects::nonNull)
                            .anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(q)))
                    .collect(Collectors.toList());
        }
        refreshClient();
    }

    // ---------------------------------------------------------
    // Server-Ansicht
    // ---------------------------------------------------------
    private void setupServerPagination() {
        if (pagination == null) {
            loadServerPage(0);
            return;
        }

        // Garder la page actuelle, ne pas remettre à 0
        int current = Math.max(0, pagination.getCurrentPageIndex());
        int pageCount = Math.max(1, (int) Math.ceil((double) totalCount / rowsPerPage));

        // Mettre à jour le pageCount sans recréer la factory
        pagination.setPageCount(pageCount);

        // N'initialiser la pageFactory qu'une seule fois → pas de "blink"
        if (!serverPagerInitialized) {
            pagination.setPageFactory(pageIndex -> {
                loadServerPage(pageIndex);
                return new Label();
            });
            serverPagerInitialized = true;
        }

        // Ne pas enlever le noeud du layout: visible oui, managed reste true (voir §3)
        pagination.setVisible(totalCount > 0);

        // Rester sur la page courante, en la bornant
        if (current >= pageCount) current = pageCount - 1;
        if (current < 0) current = 0;

        // Si l'index n'a pas changé, on recharge simplement la page courante
        if (pagination.getCurrentPageIndex() != current) {
            pagination.setCurrentPageIndex(current);
        } else {
            loadServerPage(current);
        }

        updateResultsCount();
    }


    private void loadServerPage(int pageIndex) {
        EXECUTOR.submit(() -> {
            try {
                List<RowData> pageRows = dataLoader.loadPage(pageIndex, rowsPerPage);
                Platform.runLater(() -> {
                    buildColumnsFromRows(pageRows);
                    TreeItem<ObservableList<String>> root = buildTreeUsingPathProvider(pageRows);
                    applyRoot(root);
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        Dialog.showErrorDialog("Ladefehler", "Seite konnte nicht geladen werden:\n" + ex.getMessage()));
            }
        });
    }

    // ---------------------------------------------------------
    // Spaltenaufbau
    // ---------------------------------------------------------
    private void buildColumnsFromRows(List<RowData> data) {
        treeTableView.getColumns().clear();
        currentHeaders = (data == null || data.isEmpty())
                ? new ArrayList<>()
                : new ArrayList<>(data.get(0).getValues().keySet());

        for (int i = 0; i < currentHeaders.size(); i++) {
            final int idx = i;
            final String originalKey = currentHeaders.get(i);
            TreeTableColumn<ObservableList<String>, String> col = new TreeTableColumn<>(originalKey);
            col.setUserData(originalKey);
            col.setCellValueFactory(param -> {
                ObservableList<String> row = (param.getValue() == null) ? null : param.getValue().getValue();
                String value = (row != null && idx < row.size()) ? row.get(idx) : "";
                return new ReadOnlyStringWrapper(value);
            });

            // Kontextmenü
            addContextMenuToColumn(col);

            treeTableView.getColumns().add(col);
        }
        markDataChanged();
    }

    private void addContextMenuToColumn(TreeTableColumn<ObservableList<String>, String> column) {
        MenuItem renameItem = new MenuItem("Spalte umbenennen");
        MenuItem deleteItem = new MenuItem("Spalte löschen");

        renameItem.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog(column.getText());
            dialog.setTitle("Spalte umbenennen");
            dialog.setHeaderText("Neuer Name für '" + column.getText() + "':");
            dialog.setContentText("Name:");
            dialog.showAndWait().ifPresent(newName -> {
                if (newName != null && !newName.isBlank()) {
                    column.setText(newName);
                    treeTableView.refresh();
                }
            });
        });

        deleteItem.setOnAction(e -> deleteColumn(column));

        ContextMenu cm = new ContextMenu(renameItem, new SeparatorMenuItem(), deleteItem);
        column.setContextMenu(cm);
    }

    private void deleteColumn(TreeTableColumn<ObservableList<String>, String> column) {
        if (column == null) return;
        if (!Dialog.showWarningDialog("Spalte löschen",
                "Möchten Sie die Spalte '" + column.getText() + "' wirklich löschen?")) return;

        deleteColumns(List.of(column));
    }

    // ---------------------------------------------------------
    // Baumaufbau (Pfad-Provider)
    // ---------------------------------------------------------
    private TreeItem<ObservableList<String>> buildTreeUsingPathProvider(List<RowData> rows) {
        TreeItem<ObservableList<String>> root = new TreeItem<>(emptyRow());
        root.setExpanded(autoExpandRoot);

        if (rows == null || rows.isEmpty()) return root;

        Map<String, TreeItem<ObservableList<String>>> groupIndex = new LinkedHashMap<>();

        for (RowData r : rows) {
            List<String> path = safePath(groupingPathProvider.apply(r));
            TreeItem<ObservableList<String>> parent = root;
            StringBuilder keyBuilder = new StringBuilder();

            for (String segment : path) {
                keyBuilder.append('\u001F').append(segment);
                String key = keyBuilder.toString();

                TreeItem<ObservableList<String>> grp = groupIndex.get(key);
                if (grp == null) {
                    grp = makeGroupItem(segment);
                    parent.getChildren().add(grp);
                    groupIndex.put(key, grp);
                }
                parent = grp;
            }
            parent.getChildren().add(toLeafItem(r));
        }
        return root;
    }

    private List<String> safePath(List<String> p) {
        if (p == null || p.isEmpty()) return List.of("Alle");
        List<String> out = new ArrayList<>(p.size());
        for (String s : p) out.add(s == null ? "" : s);
        return out;
    }

    private TreeItem<ObservableList<String>> toLeafItem(RowData row) {
        ObservableList<String> values = FXCollections.observableArrayList();
        for (String h : currentHeaders) values.add(ColumnValueFormatter.format(row, h));
        return new TreeItem<>(values);
    }

    private TreeItem<ObservableList<String>> makeGroupItem(String label) {
        ObservableList<String> row = emptyRow();
        if (!row.isEmpty()) row.set(0, safe(label));
        return new TreeItem<>(row);
    }

    private ObservableList<String> emptyRow() {
        ObservableList<String> list = FXCollections.observableArrayList();
        for (int i = 0; i < currentHeaders.size(); i++) list.add("");
        return list;
    }

    private void applyRoot(TreeItem<ObservableList<String>> root) {
        treeTableView.setRoot(root);
        treeTableView.setShowRoot(showRoot);
        if (autoExpandRoot) expandLevel(root, 1);
        updateResultsCount();
        cleanRanForThisPage = false;
        if (cleanColumnsButton != null) cleanColumnsButton.setDisable(false);
    }

    // ---------------------------------------------------------
    // Styling
    // ---------------------------------------------------------
    private void installRowStyling() {
        treeTableView.setRowFactory(tv -> new TreeTableRow<>() {
            @Override
            protected void updateItem(ObservableList<String> item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("");
                if (empty) return;
                TreeItem<ObservableList<String>> ti = getTreeItem();
                if (ti != null && !ti.isLeaf()) {
                    setStyle("-fx-font-weight: bold; -fx-background-color: rgba(0,0,0,0.04);");
                }
            }
        });
    }

    // ---------------------------------------------------------
    // Spalten-Operationen
    // ---------------------------------------------------------
    private void handleDeleteSelectedColumns() {
        if (!selectionEnabled) return;
        var sel = treeTableView.getSelectionModel().getSelectedCells();
        if (sel.isEmpty()) {
            Dialog.showErrorDialog("Keine Auswahl", "Bitte Spaltenköpfe/Zellen auswählen.");
            return;
        }
        Set<TreeTableColumn<ObservableList<String>, ?>> cols = sel.stream()
                .map(TreeTablePosition::getTableColumn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (cols.isEmpty()) return;

        if (Dialog.showWarningDialog("Spalten löschen", cols.size() + " Spalte(n) löschen?")) {
            deleteColumns(new ArrayList<>(cols));
        }
    }

    private void deleteColumns(List<TreeTableColumn<ObservableList<String>, ?>> toRemove) {
        Set<String> keys = toRemove.stream().map(c -> String.valueOf(c.getUserData())).collect(Collectors.toSet());
        treeTableView.getColumns().removeAll(toRemove);

        for (RowData r : originalData) r.getValues().keySet().removeAll(keys);
        for (RowData r : filteredData) r.getValues().keySet().removeAll(keys);

        currentHeaders.removeIf(keys::contains);

        if (serverPaginationEnabled) {
            if (pagination != null) pagination.setCurrentPageIndex(pagination.getCurrentPageIndex());
            else loadServerPage(0);
        } else {
            refreshClient();
        }
    }

    /** Entfernt Spalten, die auf der sichtbaren Seite vollständig leer sind. */
    private void cleanColumnsOnCurrentPage() {
        if (cleanRanForThisPage || treeTableView.getRoot() == null) {
            if (cleanColumnsButton != null) cleanColumnsButton.setDisable(true);
            return;
        }

        List<TreeTableColumn<ObservableList<String>, ?>> columns = new ArrayList<>(treeTableView.getColumns());
        List<TreeItem<ObservableList<String>>> leaves = collectLeaves(treeTableView.getRoot());

        List<TreeTableColumn<ObservableList<String>, ?>> toDelete = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            boolean allEmpty = true;
            for (TreeItem<ObservableList<String>> leaf : leaves) {
                ObservableList<String> row = leaf.getValue();
                String v = (row != null && c < row.size()) ? row.get(c) : null;
                if (v != null && !v.trim().isEmpty()) { allEmpty = false; break; }
            }
            if (allEmpty) toDelete.add(columns.get(c));
        }

        if (toDelete.isEmpty()) {
            Dialog.showInfoDialog("Bereinigen", "Keine vollständig leeren Spalten auf dieser Seite.");
        } else {
            deleteColumns(toDelete);
        }
        cleanRanForThisPage = true;
        if (cleanColumnsButton != null) cleanColumnsButton.setDisable(true);
    }

    private List<TreeItem<ObservableList<String>>> collectLeaves(TreeItem<ObservableList<String>> root) {
        List<TreeItem<ObservableList<String>>> out = new ArrayList<>();
        if (root == null) return out;
        for (TreeItem<ObservableList<String>> ch : root.getChildren()) {
            if (ch.isLeaf()) out.add(ch);
            else out.addAll(collectLeaves(ch));
        }
        return out;
    }

    // ---------------------------------------------------------
    // Expand/Collapse
    // ---------------------------------------------------------
    public void expandAll() {
        TreeItem<ObservableList<String>> root = treeTableView.getRoot();
        if (root != null) setExpandedRecursively(root, true);
    }

    public void collapseAll() {
        TreeItem<ObservableList<String>> root = treeTableView.getRoot();
        if (root != null) {
            setExpandedRecursively(root, false);
            if (showRoot) root.setExpanded(false);
        }
    }

    private void expandLevel(TreeItem<?> item, int levels) {
        if (item == null || levels <= 0) return;
        item.setExpanded(true);
        for (TreeItem<?> c : item.getChildren()) expandLevel(c, levels - 1);
    }

    private void setExpandedRecursively(TreeItem<?> item, boolean expanded) {
        item.setExpanded(expanded);
        for (TreeItem<?> c : item.getChildren()) setExpandedRecursively(c, expanded);
    }

    // ---------------------------------------------------------
    // Ergebniszähler
    // ---------------------------------------------------------
    private void updateResultsCount() {
        if (resultsCountLabel == null) return;
        if (serverPaginationEnabled) updateResultsCountServer();
        else updateResultsCountClient();
    }

    private void updateResultsCountClient() {
        if (resultsCountLabel == null) return;
        int leafCount = (treeTableView.getRoot() == null) ? 0 : collectLeaves(treeTableView.getRoot()).size();
        resultsCountLabel.setText("(" + leafCount + " Ergebnis" + (leafCount == 1 ? "" : "se") + ")");
    }

    private void updateResultsCountServer() {
        if (resultsCountLabel == null) return;
        resultsCountLabel.setText("(" + totalCount + " Ergebnis" + (totalCount == 1 ? "" : "se") + ")");
    }

    private void paginationVisible(boolean visible) {
        if (pagination != null) {
            pagination.setVisible(visible);
            //pagination.setManaged(visible);
        }
    }

    private void clearTree() {
        treeTableView.getColumns().clear();
        treeTableView.setRoot(null);
        paginationVisible(false);
        updateResultsCount();
    }

    private void markDataChanged() {
        cleanRanForThisPage = false;
        if (cleanColumnsButton != null) cleanColumnsButton.setDisable(false);
    }

    private static String safe(String s) { return (s == null) ? "" : s; }

    // ---------------------------------------------------------
    // Getter (Export)
    // ---------------------------------------------------------
    public List<String> getDisplayHeaders() {
        return treeTableView.getColumns().stream().map(TreeTableColumn::getText).collect(Collectors.toList());
    }

    public List<String> getOriginalKeys() {
        return treeTableView.getColumns().stream().map(c -> String.valueOf(c.getUserData())).collect(Collectors.toList());
    }

    public TreeTableView<ObservableList<String>> getTreeTableView() { return treeTableView; }
}
