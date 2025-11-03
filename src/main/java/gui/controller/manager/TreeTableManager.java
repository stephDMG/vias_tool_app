package gui.controller.manager;

import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
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
import javafx.scene.layout.Region;
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
 * Universeller Manager für TreeTableView<ObservableList<String>>.
 *
 * Behält die öffentliche API (Rückwärtskompatibilität) und implementiert
 * Tree-spezifische Darstellung/Gruppierung.
 */
public class TreeTableManager extends AbstractTableManager {

    private static final Logger log = LoggerFactory.getLogger(TreeTableManager.class);

    // UI
    private final TreeTableView<ObservableList<String>> treeTableView;
    private final Button deleteColumnsButton;
    private volatile boolean expandTaskRunning = false;

    private Button cleanColumnsButton;
    private Button expandAllButton;
    private Button collapseAllButton;

    // Export Hooks
    private Runnable onExportCsv;
    private Runnable onExportXlsx;

    // Gruppierung
    private Function<RowData, List<String>> groupingPathProvider = r -> List.of("Alle");
    private final AtomicLong expandOpSeq = new AtomicLong(0);

    // Auswahl
    private boolean selectionEnabled = false;

    // Expansion: null = granular (per Gruppe), TRUE = alles offen, FALSE = alles zu
    private Boolean globalExpand = null;

    // ----- Konstruktoren -----

    public TreeTableManager(TreeTableView<ObservableList<String>> treeTableView,
                            TextField searchField,
                            Button deleteColumnsButton,
                            Pagination pagination,
                            Label resultsCountLabel) {
        this(treeTableView, searchField, deleteColumnsButton, pagination, resultsCountLabel,
                new ColumnStateModel(), new ResultContextModel(), new TableStateModel());
        log.warn("TreeTableManager instanziiert ohne Models. Lokale Modelle erstellt. Nur für Tests/Legacy-Kontext verwenden!");
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

        treeTableView.setShowRoot(false);

        // ⚠️ Fix: éviter le bug d’expansion à la 1ère peinture avec fixedCellSize.
        // On laisse JavaFX faire une première mise en page, puis on fige la hauteur.
        treeTableView.setFixedCellSize(Region.USE_COMPUTED_SIZE);
        Platform.runLater(() -> treeTableView.setFixedCellSize(24));

        treeTableView.getSelectionModel().setCellSelectionEnabled(false);
        treeTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        //installRowStyling(); // facultatif
        installGroupRowFactory();
    }

    // ----- Öffentliche API (kompatibel) -----

    public void loadDataFromServer(int totalCount, DataLoader dataLoader, Function<RowData, List<String>> provider) {
        this.groupingPathProvider = (provider != null) ? provider : (r -> List.of("Alle"));
        super.loadDataFromServer(totalCount, dataLoader);
    }

    public ReadOnlyBooleanProperty hasDataProperty() { return super.hasDataProperty(); }
    public boolean hasData() { return super.hasData(); }

    public TreeTableManager enableSearch() { return (TreeTableManager) super.enableSearch(); }
    public TreeTableManager enablePagination(int rowsPerPage) { return (TreeTableManager) super.enablePagination(rowsPerPage); }
    public TreeTableManager setOnServerSearch(Consumer<String> handler) { return (TreeTableManager) super.setOnServerSearch(handler); }

    public void enableCleanTable() { /* Rétrocompatibilité (géré via setCleanButton) */ }

    public TreeTableManager enableSelection() {
        if (deleteColumnsButton == null) return this;
        selectionEnabled = true;
        deleteColumnsButton.setDisable(false);
        deleteColumnsButton.setOnAction(e -> handleDeleteSelectedColumns());
        return this;
    }

    public void setGroupingPathProvider(Function<RowData, List<String>> provider) {
        this.groupingPathProvider = (provider != null) ? provider : (r -> List.of("Alle"));
    }

    // Buttons
    public void setCleanButton(Button cleanButton) {
        this.cleanColumnsButton = cleanButton;
        if (cleanButton != null) {
            cleanButton.setOnAction(e -> cleanColumnsAllPages());
            columnModel.cleanedProperty().addListener((obs, ov, nv) ->
                    cleanColumnsButton.setDisable(nv || !hasData()));
            hasDataProperty().addListener((obs, ov, nv) ->
                    cleanColumnsButton.setDisable(columnModel.isCleaned() || !nv));
            cleanColumnsButton.setDisable(!hasData() || columnModel.isCleaned());
        }
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
        if (btn != null) {
            btn.disableProperty().bind(hasDataProperty().not());
            btn.setOnAction(e -> { if (onExportCsv != null) onExportCsv.run(); });
        }
    }
    public void setExportXlsxButton(Button btn) {
        if (btn != null) {
            btn.disableProperty().bind(hasDataProperty().not());
            btn.setOnAction(e -> { if (onExportXlsx != null) onExportXlsx.run(); });
        }
    }
    public void setOnExportCsv(Runnable handler) { this.onExportCsv = handler; }
    public void setOnExportXlsx(Runnable handler) { this.onExportXlsx = handler; }

    // ----- Tree-spezifische Logik -----

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

    public void expandAll() {
        globalExpand = Boolean.TRUE;

        TreeItem<ObservableList<String>> root = treeTableView.getRoot();
        if (root == null) return;

        // Ouvrir immédiatement ce qui est déjà visible
        setExpandedRecursively(root, true);

        var loader = resultModel.getPageLoader();
        int total = resultModel.getTotalCount();
        int pageSize = stateModel.getRowsPerPage();

        if (!(serverPaginationEnabled && loader != null && total > pageSize)) {
            // pas de pagination serveur → on est bon
            Platform.runLater(() -> {
                TreeItem<ObservableList<String>> rt = treeTableView.getRoot();
                if (rt != null) {
                    setExpandedRecursively(rt, true);
                    treeTableView.requestLayout();
                    treeTableView.refresh();
                }
            });
            return;
        }

        // éviter double-clics
        if (expandTaskRunning) return;
        expandTaskRunning = true;

        setExpandButtonsDisabled(true);
        resultModel.setLoading(true);

        EXECUTOR.submit(() -> {
            try {
                List<RowData> allRows = new ArrayList<>();
                int pages = (int) Math.ceil(total / (double) pageSize);

                // commence par la page courante
                int current = stateModel.getCurrentPageIndex();
                IntStream.range(0, pages)
                        .boxed()
                        .sorted(Comparator.comparingInt(p -> p == current ? -1 : 1))
                        .forEach(p -> {
                            try {
                                List<RowData> part = loader.loadPage(p, pageSize);
                                if (part != null && !part.isEmpty()) {
                                    synchronized (allRows) { allRows.addAll(part); }
                                }
                            } catch (Exception ex) {
                                log.error("expandAll(): Fehler beim Nachladen Seite {}", p, ex);
                            }
                        });

                Platform.runLater(() -> {
                    TreeItem<ObservableList<String>> newRoot = buildTreeUsingPathProvider(allRows);
                    applyRoot(newRoot);
                    // Restaurer après attache de la root (skin-ready)
                    Platform.runLater(() -> {
                        TreeItem<ObservableList<String>> rt = treeTableView.getRoot();
                        if (rt != null) {
                            setExpandedRecursively(rt, true);
                            treeTableView.requestLayout();
                            treeTableView.refresh();
                        }
                    });
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

    private void setExpandButtonsDisabled(boolean disabled) {
        if (expandAllButton != null) expandAllButton.setDisable(disabled);
        if (collapseAllButton != null) collapseAllButton.setDisable(disabled);
    }

    public void collapseAll() {
        globalExpand = Boolean.FALSE;
        if (expandAllButton != null) expandAllButton.setDisable(true);
        if (collapseAllButton != null) collapseAllButton.setDisable(true);

        expandOpSeq.incrementAndGet(); // annule un éventuel expand en cours

        TreeItem<ObservableList<String>> root = treeTableView.getRoot();
        if (root != null) {
            setExpandedRecursively(root, false);
            root.setExpanded(true);
        }
        if (expandAllButton != null) expandAllButton.setDisable(false);
        if (collapseAllButton != null) collapseAllButton.setDisable(false);
    }

    private void setExpandedRecursively(TreeItem<?> item, boolean expanded) {
        if (item == null) return;
        item.setExpanded(expanded);
        for (TreeItem<?> c : item.getChildren()) setExpandedRecursively(c, expanded);
    }

    private void restoreExpansion(TreeItem<ObservableList<String>> root) {
        if (root == null) return;

        final Boolean ge = this.globalExpand; // capture pour éviter races

        if (Boolean.TRUE.equals(ge)) {
            setExpandedRecursively(root, true);
            return;
        }
        if (Boolean.FALSE.equals(ge)) {
            setExpandedRecursively(root, false);
            root.setExpanded(true);
            return;
        }

        for (TreeItem<ObservableList<String>> item : root.getChildren()) {
            if (!item.isLeaf()) {
                String key = expansionKeyFor(item); // 🔁 même clé que rememberExpansion()
                boolean expanded = stateModel.getExpansionState()
                        .getOrDefault(key, false);
                setExpandedRecursively(item, expanded);
            }
        }


        // sécurité layout
        Platform.runLater(() -> {
            treeTableView.requestLayout();
            treeTableView.refresh();
        });
    }

    private TreeItem<ObservableList<String>> buildTreeAndColumns(List<RowData> rows, Set<String> hiddenKeys) {
        if (rows == null || rows.isEmpty()) {
            treeTableView.getColumns().clear();
            currentHeaders = List.of();
            return new TreeItem<>(emptyRow());
        }

        // 1) Visible headers
        List<String> allHeaders = new ArrayList<>(rows.get(0).getValues().keySet());
        List<String> visibleHeaders = allHeaders.stream()
                .filter(h -> !hiddenKeys.contains(h))
                .toList();

        // 2) Build columns if needed
        if (!visibleHeaders.equals(currentHeaders) || treeTableView.getColumns().isEmpty()) {
            treeTableView.getColumns().clear();
            currentHeaders = visibleHeaders;

            for (int i = 0; i < visibleHeaders.size(); i++) {
                final int idx = i;
                final String originalKey = visibleHeaders.get(i);

                TreeTableColumn<ObservableList<String>, String> col = new TreeTableColumn<>(originalKey);
                col.setUserData(originalKey);
                col.setCellValueFactory(param -> {
                    ObservableList<String> row = (param.getValue() == null) ? null : param.getValue().getValue();
                    String value = (row != null && idx < row.size()) ? row.get(idx) : "";
                    return new ReadOnlyStringWrapper(value);
                });
                addContextMenuToColumn(col);
                treeTableView.getColumns().add(col);
            }

            // activer le disclosure node sur la 1ère colonne
            if (!treeTableView.getColumns().isEmpty()) {
                @SuppressWarnings("unchecked")
                TreeTableColumn<ObservableList<String>, ?> treeCol =
                        (TreeTableColumn<ObservableList<String>, ?>) treeTableView.getColumns().get(0);
                treeTableView.setTreeColumn(treeCol);
            }
        }

        // 3) Build tree
        return buildTreeUsingPathProvider(rows);
    }

    private TreeItem<ObservableList<String>> buildTreeUsingPathProvider(List<RowData> rows) {
        TreeItem<ObservableList<String>> root = new TreeItem<>(emptyRow());
        root.setExpanded(true);

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
        if (label == null || label.isBlank()) label = "(leer)";
        ObservableList<String> row = emptyRow();
        if (!row.isEmpty()) row.set(0, label);
        TreeItem<ObservableList<String>> item = new TreeItem<>(row);

        item.expandedProperty().addListener((obs, ov, nv) -> {
            stateModel.putExpansion(expansionKeyFor(item), nv);
            globalExpand = null;
            // on laisse la passe de layout aux helpers différés
        });
        return item;
    }

    private ObservableList<String> emptyRow() {
        ObservableList<String> list = FXCollections.observableArrayList();
        for (int i = 0; i < currentHeaders.size(); i++) list.add("");
        return list;
    }

    private void applyRoot(TreeItem<ObservableList<String>> root) {
        treeTableView.setRoot(root);
        treeTableView.setShowRoot(false);
        updateResultsCount();
        // pas de restore ici — on le fait via deferRestoreExpansion()
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

        Platform.runLater(() -> columnModel.addHiddenKey(String.valueOf(column.getUserData())));
        // refreshView() se déclenchera via le listener du ColumnStateModel
    }

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
            cols.stream()
                    .map(c -> String.valueOf(c.getUserData()))
                    .forEach(columnModel::addHiddenKey);
        }
    }

    // ----- Abstrakte Hooks -----

    @Override
    protected List<RowData> getOriginalDataForClientFilter() {
        return filteredData; // Client-Modus: alles gespiegelt
    }

    @Override
    protected void configureSearchSection(boolean visible) {
        // Sichtbarkeit vom FXML/Builder gesteuert
    }

    @Override
    protected List<String> currentHeaders() { return currentHeaders; }

    @Override
    protected int getVisibleRowCount() {
        TreeItem<ObservableList<String>> root = treeTableView.getRoot();
        return (root == null) ? 0 : collectLeaves(root).size();
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

    @Override
    protected String getVisibleCellValue(int rowIndex, int columnIndex) {
        TreeItem<ObservableList<String>> root = treeTableView.getRoot();
        if (root == null || rowIndex < 0 || columnIndex < 0) return null;
        List<TreeItem<ObservableList<String>>> leaves = collectLeaves(root);
        if (rowIndex >= leaves.size()) return null;
        ObservableList<String> row = leaves.get(rowIndex).getValue();
        return (row != null && columnIndex < row.size()) ? row.get(columnIndex) : null;
    }

    @Override
    protected void installGroupRowFactory() {
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

        // 1) Sélectionner la row sous la souris AVANT le clic
        treeTableView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            Node tgt = (e.getPickResult() == null) ? null : e.getPickResult().getIntersectedNode();
            for (Node n = tgt; n != null; n = n.getParent()) {
                if (n instanceof TreeTableRow<?> row) {
                    int idx = ((TreeTableRow<?>) row).getIndex();
                    if (idx >= 0) {
                        treeTableView.requestFocus();
                        treeTableView.getSelectionModel().clearAndSelect(idx);
                    }
                    break;
                }
            }
        });

        // 2) Toggle basé sur la row cliquée (pas l’index sélectionné)
        treeTableView.setOnMouseClicked(e -> {
            if (e.getClickCount() != 1) return;

            Node tgt = (e.getPickResult() == null) ? null : e.getPickResult().getIntersectedNode();

            // remonte jusqu’à la TreeTableRow cliquée
            TreeTableRow<ObservableList<String>> row = null;
            for (Node n = tgt; n != null; n = n.getParent()) {
                if (n instanceof TreeTableRow<?> r) {
                    @SuppressWarnings("unchecked")
                    TreeTableRow<ObservableList<String>> rr = (TreeTableRow<ObservableList<String>>) r;
                    row = rr; break;
                }
            }
            if (row == null || row.isEmpty()) return;

            // si clic sur le disclosure → laisser JavaFX gérer icône + enfants
            if (isDisclosureHit(tgt, row)) return;

            TreeItem<ObservableList<String>> ti = row.getTreeItem();
            if (ti == null || ti.isLeaf()) return;

            ti.setExpanded(!ti.isExpanded());
            rememberExpansion(ti);
            globalExpand = null;
            // le relayout sera fait par le pipeline différé si nécessaire
            e.consume();
        });

        // 3) Raccourcis clavier
        treeTableView.setOnKeyPressed(e -> {
            TreeItem<ObservableList<String>> ti = treeTableView.getSelectionModel().getSelectedItem();
            if (ti == null || ti.isLeaf()) return;
            switch (e.getCode()) {
                case ENTER:
                case SPACE:
                    ti.setExpanded(!ti.isExpanded());
                    rememberExpansion(ti);
                    globalExpand = null;
                    e.consume();
                    break;
                case RIGHT:
                case ADD:
                    if (!ti.isExpanded()) {
                        ti.setExpanded(true);
                        rememberExpansion(ti);
                        globalExpand = null;
                        e.consume();
                    }
                    break;
                case LEFT:
                case SUBTRACT:
                    if (ti.isExpanded()) {
                        ti.setExpanded(false);
                        rememberExpansion(ti);
                        globalExpand = null;
                        e.consume();
                    }
                    break;
                default:
                    break;
            }
        });
    }

    private String expansionKeyFor(TreeItem<ObservableList<String>> item) {
        // remonte jusqu’à root et reconstruit le chemin des segments de groupe (col 0)
        Deque<String> stack = new ArrayDeque<>();
        for (TreeItem<ObservableList<String>> it = item; it != null && it.getParent() != null; it = it.getParent()) {
            ObservableList<String> v = it.getValue();
            String seg = (v != null && !v.isEmpty()) ? v.get(0) : "";
            if (seg == null || seg.isBlank()) seg = "(leer)";
            stack.push(seg);
        }
        return String.join("⟂", stack); // séparateur improbable
    }

    private TreeTableRow<ObservableList<String>> getRowFromEvent(javafx.scene.input.MouseEvent e) {
        Node n = e.getPickResult() == null ? null : e.getPickResult().getIntersectedNode();
        while (n != null && !(n instanceof TreeTableRow)) n = n.getParent();
        @SuppressWarnings("unchecked")
        TreeTableRow<ObservableList<String>> row = (TreeTableRow<ObservableList<String>>) n;
        return row;
    }

    private boolean isDisclosureHit(Node target, TreeTableRow<?> row) {
        for (Node n = target; n != null && n != row; n = n.getParent()) {
            var sc = n.getStyleClass();
            if (sc == null) continue;
            if (sc.contains("tree-disclosure-node") || sc.contains("arrow") || sc.contains("arrow-button")) return true;
        }
        return false;
    }

    private void rememberExpansion(TreeItem<ObservableList<String>> ti) {
        stateModel.putExpansion(expansionKeyFor(ti), ti.isExpanded());
    }

    @Override
    public void refreshView() {
        // 1) Construire colonnes + arbre sur données filtrées visibles
        Set<String> hiddenKeys = columnModel.getHiddenKeys();
        TreeItem<ObservableList<String>> root = buildTreeAndColumns(filteredData, hiddenKeys);
        applyRoot(root);

        // 2) Restaurer l’expansion quand la skin/VirtualFlow est prête
        deferRestoreExpansion();

        // 3) Pagination client (si activée ici)
        if (!serverPaginationEnabled && paginationEnabled && pagination != null) {
            int rowsPerPage = stateModel.getRowsPerPage();
            int pageCount = (int) Math.ceil((double) filteredData.size() / rowsPerPage);
            pagination.setPageCount(Math.max(pageCount, 1));
            pagination.setVisible(filteredData.size() > 0);
            pagination.setPageFactory(this::createClientPage);
        }

        recomputeGroupStripes();
        updateResultsCount();
    }

    /**
     * Diffère la restauration d’expansion d’un ou deux pulses pour garantir que la Skin/VirtualFlow est prête.
     */
    private void deferRestoreExpansion() {
        Runnable r = () -> {
            TreeItem<ObservableList<String>> rt = treeTableView.getRoot();
            if (rt != null) {
                restoreExpansion(rt);
                treeTableView.requestLayout();
                treeTableView.refresh();
            }
        };
        if (treeTableView.getSkin() == null) {
            Platform.runLater(() -> Platform.runLater(r)); // 2 pulses → skin + relayout garantis
        } else {
            Platform.runLater(r);
        }
    }

    private Node createClientPage(int pageIndex) {
        // 🧹 reset expansion pour éviter la “contamination” inter-pages
        stateModel.clearExpansion();      // ← assure “tout fermé” par défaut
        globalExpand = null;              // ← rester en mode granulaire

        int rowsPerPage = stateModel.getRowsPerPage();
        int from = pageIndex * rowsPerPage;
        int to = Math.min(from + rowsPerPage, filteredData.size());
        List<RowData> slice = (from < to) ? filteredData.subList(from, to) : List.of();

        Set<String> hiddenKeys = columnModel.getHiddenKeys();
        TreeItem<ObservableList<String>> root = buildTreeAndColumns(slice, hiddenKeys);
        applyRoot(root);

        restoreExpansion(root); // toutes les clés sont vides → tout fermé
        recomputeGroupStripes();
        return new Label();
    }


    @Override
    protected void updateResultsCount() {
        if (resultsCountLabel == null) return;

        final int countToDisplay = serverPaginationEnabled
                ? stateModel.getTotalCount()
                : getVisibleRowCount();

        resultsCountLabel.setText("(" + countToDisplay + " Ergebnis" + (countToDisplay == 1 ? "" : "se") + ")");
    }

    @Override
    protected void clearView() {
        treeTableView.getColumns().clear();
        treeTableView.setRoot(null);
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
                .toList();
    }

    @Override
    public List<String> getOriginalKeys() {
        return treeTableView.getColumns().stream()
                .map(c -> String.valueOf(c.getUserData()))
                .toList();
    }

    @Override
    protected void disableCleanButtonOnCleaned(javafx.collections.SetChangeListener.Change<? extends String> c) {
        // Géré dans setCleanButton()
    }
}
