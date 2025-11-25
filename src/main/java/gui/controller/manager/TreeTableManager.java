package gui.controller.manager;

import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
import gui.controller.manager.base.AbstractTableManager;
import gui.controller.model.ColumnStateModel;
import gui.controller.model.ResultContextModel;
import gui.controller.model.TableStateModel;
import gui.controller.service.FormatterService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
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
    // Steuerung von expandAll/collapseAll
    private final AtomicLong expandOpSeq = new AtomicLong(0);
    private Button cleanColumnsButton;
    private Button expandAllButton;
    private Button collapseAllButton;
    private String groupingHeaderKey;
    // Export-Hooks (werden vom Controller gesetzt)
    private Runnable onExportCsv;
    private Runnable onExportXlsx;
    // Gruppierungspfad: RowData -> Liste von Segmenten (Makler, Gesellschaft, ...)
    private Function<RowData, List<String>> groupingPathProvider = r -> List.of("Alle");
    private volatile Boolean globalExpand = null;

    // Auswahl
    private boolean selectionEnabled = false;

    // Welche Spalten dienen als Gruppierungs-Schlüssel je Ebene? (null = keine Substitution)
    private List<String> groupingHeaderKeys = List.of();
    // Root-Item
    private TreeItem<ObservableList<String>> rootItem = new TreeItem<>(emptyRow());
    // Gemeinsame Map für Spaltennamen, wird mit EnhancedTableManager geteilt
    private javafx.collections.ObservableMap<String, String> sharedColumnDisplayNames;
    private volatile boolean expandTaskRunning = false;

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


    // -------------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------------

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
        //treeTableView.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/fixed-header.css")).toExternalForm());

        installSelectionSupport();
        installRowStyling();
        installGroupRowFactory();
    }

    /**
     * Vom Controller setzen: pro Ebene der Gruppierung den Header-Alias (z.B. ["SB_Vertr","SB_Schad", null, ...]).
     */
    public void setGroupingHeaderKeys(List<String> keysPerLevel) {
        this.groupingHeaderKeys = (keysPerLevel == null) ? List.of() : new ArrayList<>(keysPerLevel);
    }

    /**
     * (Optionnel) Für Rückwärts-Kompatibilität – 1 Ebene
     */
    @Deprecated
    public void setGroupingHeaderKey(String key) {
        setGroupingHeaderKeys(key == null ? List.of() : List.of(key));
    }

    public TreeTableView<ObservableList<String>> getTreeTableView() {
        return treeTableView;
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

    public void rebuildView() {
        refreshView(); // méthode protégée existante
    }


    // -------------------------------------------------------------------------
    // Öffentliche API (kompatibel)
    // -------------------------------------------------------------------------

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

        treeTableView.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY); // même policy que Table
        Platform.runLater(() -> {
            treeTableView.layout();   // corrige le décalage d’entête
            treeTableView.refresh();
        });
    }

    public void loadDataFromServer(int totalCount, DataLoader dataLoader, Function<RowData, List<String>> provider) {
        this.groupingPathProvider = (provider != null) ? provider : (r -> List.of("Alle"));
        super.loadDataFromServer(totalCount, dataLoader);
    }

    public ReadOnlyBooleanProperty hasDataProperty() {
        return super.hasDataProperty();
    }

    public boolean hasData() {
        return super.hasData();
    }

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

        // ➜ Désactiver par défaut (même comportement que Table)
        deleteColumnsButton.setDisable(true);

        // ➜ Activer/désactiver selon la sélection de CELLULES (pas selectedItem)
        treeTableView.getSelectionModel().getSelectedCells().addListener(
                (javafx.collections.ListChangeListener<TreeTablePosition<ObservableList<String>, ?>>)
                        c -> {
                            boolean empty = treeTableView.getSelectionModel().getSelectedCells().isEmpty();
                            deleteColumnsButton.setDisable(empty);
                        }
        );

        // ➜ (optionnel) si plus de données → désactiver
        hasDataProperty().addListener((obs, oldV, hasData) -> {
            boolean emptySel = treeTableView.getSelectionModel().getSelectedCells().isEmpty();
            deleteColumnsButton.setDisable(!hasData || emptySel);
        });
        deleteColumnsButton.setOnAction(e -> handleDeleteSelectedColumns());
        return this;
    }

    public void setCleanButton(Button cleanButton) {
        this.cleanColumnsButton = cleanButton;
        if (cleanButton != null) {
            cleanButton.setOnAction(e -> cleanColumnsAllPages());

            // Gleiche Deaktivierungslogik wie in EnhancedTableManager
            cleanButton.disableProperty().bind(
                    columnModel.cleanedProperty()
                            .or(hasDataProperty().not())
                            .or(resultModel.loadingProperty())
            );
        }
    }

    public void setOnExportCsv(Runnable r) {
        this.onExportCsv = r;
    }

    public void setOnExportXlsx(Runnable r) {
        this.onExportXlsx = r;
    }

    public void setExportCsvButton(Button b) {
        if (b != null) {
            b.disableProperty().bind(hasDataProperty().not());
            b.setOnAction(e -> {
                if (onExportCsv != null) onExportCsv.run();
            });
        }
    }

    public void setExportXlsxButton(Button b) {
        if (b != null) {
            b.disableProperty().bind(hasDataProperty().not());
            b.setOnAction(e -> {
                if (onExportXlsx != null) onExportXlsx.run();
            });
        }
    }

    public void setExpandAllButton(Button b) {
        this.expandAllButton = b;
        if (b != null) {
            b.setOnAction(e -> expandAll());
        }
    }

    // -------------------------------------------------------------------------
    // Initialisierung Tree
    // -------------------------------------------------------------------------

    public void setCollapseAllButton(Button b) {
        this.collapseAllButton = b;
        if (b != null) {
            b.setOnAction(e -> collapseAll());
        }
    }

    private void initTree() {
        treeTableView.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/fixed-header.css")).toExternalForm());
        treeTableView.setRoot(rootItem);
        treeTableView.setShowRoot(false);
        treeTableView.setEditable(false);

        treeTableView.setFixedCellSize(24);

        treeTableView.setMouseTransparent(false);
        treeTableView.setPickOnBounds(true);
    }

    public TreeTableManager withAutoRowsPerPage(Region observedRegion) {
        super.bindAutoRowsPerPage(observedRegion);
        return this;
    }

    private void installSelectionSupport() {
        // Zusätzliche Selektion-Logik könnte hier ergänzt werden
    }

    /**
     * Styling von Gruppen- und Datenzeilen.
     */
    private void installRowStyling() {
        treeTableView.setRowFactory(tv -> {
            TreeTableRow<ObservableList<String>> row = new TreeTableRow<>() {

                @Override
                protected void updateItem(ObservableList<String> item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("group-row");
                    setStyle("");
                    if (empty || item == null) return;

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
                                String a = String.format(java.util.Locale.US, "%.3f", c.getOpacity());
                                setStyle("-fx-background-color: rgba(" + r + "," + g + "," + b + "," + a + ");");
                            }
                        }
                    }
                }
            };
            row.setPickOnBounds(true);
            row.setMouseTransparent(false);

            // ➜ NEU: Klick auf die Zeile toggelt expand/collapse für Gruppen
            row.setOnMouseClicked(ev -> {
                if (!row.isEmpty()
                        && ev.getButton() == javafx.scene.input.MouseButton.PRIMARY
                        && ev.getClickCount() == 1) {
                    TreeItem<ObservableList<String>> ti = row.getTreeItem();
                    if (isGroupItem(ti)) {
                        ti.setExpanded(!ti.isExpanded());
                        // nach manueller Aktion keinen globalen Expand mehr erzwingen
                        globalExpand = null;
                        ev.consume();
                    }
                }
            });

            return row;
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

    // -------------------------------------------------------------------------
    // Datenaufbau / Gruppierung
    // -------------------------------------------------------------------------

    private ObservableList<String> emptyRow() {
        return FXCollections.observableArrayList();
    }

    @Override
    protected void refreshView() {
        Set<String> hiddenKeys = columnModel.getHiddenKeys();
        TreeItem<ObservableList<String>> newRoot = buildTreeAndColumns(filteredData, hiddenKeys);
        try {
            treeTableView.getSelectionModel().clearSelection();
        } catch (Exception ignore) {
        }

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
            col.setStyle("-fx-alignment: CENTER-LEFT; -fx-padding: 0 6 0 6;");
            col.setUserData(header);

            // ✅ Ajouter ces lignes
            col.setPrefWidth(150);
            col.setMinWidth(80);

            col.setCellValueFactory(param -> {
                TreeItem<ObservableList<String>> item = param.getValue();
                if (item == null) {
                    return new ReadOnlyStringWrapper("");
                }

                ObservableList<String> row = item.getValue();
                if (row == null || colIndex >= row.size()) {
                    return new ReadOnlyStringWrapper("");
                }

                return new ReadOnlyStringWrapper(row.get(colIndex));
            });

            final String headerAlias = header;
            col.setCellFactory(tc -> new TreeTableCell<ObservableList<String>, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : formatter.ColumnValueFormatter.displayOnly(headerAlias, item));
                }
            });


            addContextMenuToColumn(col);
            treeTableView.getColumns().add(col);
        }
        applySharedDisplayNames();
    }

    private TreeItem<ObservableList<String>> createGroupPath(TreeItem<ObservableList<String>> root,
                                                             List<String> path,
                                                             List<String> visibleHeaders) {
        TreeItem<ObservableList<String>> current = root;
        for (int level = 0; level < path.size(); level++) {
            String segment = path.get(level);

            // ⇩⇩ NEU: niveau → clé (SB_Vertr, SB_Schad, …) → displayOnly
            String displaySegment;
            if (level < groupingHeaderKeys.size()) {
                String key = groupingHeaderKeys.get(level);
                if (key != null && !key.isBlank()) {
                    displaySegment = formatter.ColumnValueFormatter.displayOnly(key, segment);
                } else {
                    displaySegment = segment;
                }
            } else {
                displaySegment = segment;
            }

            Optional<TreeItem<ObservableList<String>>> existing = current.getChildren().stream()
                    .filter(child -> isGroupItem(child)
                            && !child.getChildren().isEmpty()
                            && Objects.equals(child.getValue().get(0), displaySegment))
                    .findFirst();

            if (existing.isPresent()) {
                current = existing.get();
            } else {
                ObservableList<String> groupRow = FXCollections.observableArrayList();
                if (!visibleHeaders.isEmpty()) {
                    groupRow.add(displaySegment);
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
        Platform.runLater(() -> {
            treeTableView.getSelectionModel().clearSelection();
            treeTableView.layout();
            treeTableView.refresh();
        });

    }

    private void restoreExpansion(TreeItem<ObservableList<String>> root) {
        if (root == null) return;
        root.setExpanded(true);
        if (Boolean.TRUE.equals(this.globalExpand)) {
            setExpandedRecursively(root, true);
        }
    }


    // -------------------------------------------------------------------------
    // Expand / Collapse
    // -------------------------------------------------------------------------

    /**
     * Nach Gruppierungswechsel aufrufen, um Interaktionen wieder zuzulassen.
     */
    public void onGroupingChanged() {
        try {
            treeTableView.setMouseTransparent(false);
        } catch (Exception ignore) {
        }
        globalExpand = null;
        expandTaskRunning = false;
        setExpandButtonsDisabled(false);
        try {
            resultModel.setLoading(false);
        } catch (Exception ignore) {
        }
        try {
            treeTableView.getSelectionModel().clearSelection();
        } catch (Exception ignore) {
        }
    }

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
                    treeTableView.getSelectionModel().clearSelection();

                    TreeItem<ObservableList<String>> newRoot =
                            buildTreeAndColumns(allRows, columnModel.getHiddenKeys());
                    applyRoot(newRoot);

                    // root visible + tout ouvert
                    newRoot.setExpanded(true);
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
        if (rootItem == null) return;

        globalExpand = null;

        // ⚠️ éviter les exceptions de sélection pendant le collapse
        treeTableView.getSelectionModel().clearSelection();

        // Garder la root ouverte pour que les groupes (niveau 1) restent visibles
        rootItem.setExpanded(true);

        // Replier uniquement les enfants de root (et leur descendance)
        for (TreeItem<ObservableList<String>> child : rootItem.getChildren()) {
            setExpandedRecursively(child, false);
        }

        Platform.runLater(() -> {
            treeTableView.layout();
            treeTableView.refresh();
        });
    }

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


    private void applyFormatAndPersist(TreeTableColumn<ObservableList<String>, String> column, String type) {
        String originalKey = String.valueOf(column.getUserData());   // → clé stable côté backend
        String headerText = column.getText();                        // → label visible (peut changer)
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

        if (cols.isEmpty()) {
            return;
        }

        List<String> headerNames = cols.stream()
                .map(TreeTableColumn::getText)
                .filter(Objects::nonNull)
                .filter(h -> !h.isBlank())
                .toList();

        // ➜ helper de l'abstraite
        if (!confirmDeleteColumns(headerNames, cols.size())) {
            return;
        }

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
    public void requestRefresh() {
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

    public Button getCleanColumnsButton() {
        return cleanColumnsButton;
    }

    public void setCleanColumnsButton(Button cleanColumnsButton) {
        this.cleanColumnsButton = cleanColumnsButton;
    }
}
