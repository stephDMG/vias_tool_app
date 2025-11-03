package gui.controller.manager.base;

import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
import gui.controller.manager.DataLoader;
import gui.controller.model.ColumnStateModel;
import gui.controller.model.ResultContextModel;
import gui.controller.model.TableStateModel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Abstrakte Basisklasse für Table/Tree-Manager.
 *
 * <p>Implementiert die gesamte gemeinsame Logik (Suche, Pagination, Bereinigen-Global,
 * Gruppierung-Striping) und synchronisiert den Zustand über die Modelle.</p>
 *
 * <p>Setzt das Template Method Pattern um: UI-spezifische Aktionen (z.B. refresh,
 * Column-Erstellung) werden an die Subklassen delegiert.</p>
 */
public abstract class AbstractTableManager {

    private static final Logger log = LoggerFactory.getLogger(AbstractTableManager.class);
    protected static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    // Zustand (Models)
    protected final TableStateModel stateModel;
    protected final ColumnStateModel columnModel;
    protected final ResultContextModel resultModel;

    // UI-Basis (über Builder gefüllt)
    protected final TextField searchField;
    protected final Pagination pagination;
    protected final Label resultsCountLabel;

    // Datenhaltung
    protected List<RowData> filteredData = new ArrayList<>();
    protected List<String> currentHeaders = new ArrayList<>();

    // Suche/Pagination-Status
    protected boolean searchEnabled = false;
    protected boolean paginationEnabled = false;
    protected boolean serverPaginationEnabled = false;
    protected Consumer<String> onServerSearch = null;
    protected int totalCount = 0;



    // Status
    protected final BooleanProperty hasData = new SimpleBooleanProperty(false);

    protected int groupColIndex = -1;
    protected List<Boolean> stripeIsA = new ArrayList<>();
    protected Color groupColorA = null;
    protected Color groupColorB = null;
    protected boolean groupStripingEnabled = false;
    protected String groupStripingHeader = null;

    protected AbstractTableManager(TextField searchField,
                                   Pagination pagination,
                                   Label resultsCountLabel,
                                   TableStateModel stateModel,
                                   ColumnStateModel columnModel,
                                   ResultContextModel resultModel) {
        this.searchField = searchField;
        this.pagination = pagination;
        this.resultsCountLabel = resultsCountLabel;
        this.stateModel = Objects.requireNonNull(stateModel);
        this.columnModel = Objects.requireNonNull(columnModel);
        this.resultModel = Objects.requireNonNull(resultModel);

        // Bindings vom Zustand
        stateModel.totalCountProperty().addListener((obs, oldV, newV) -> updateResultsCount());
        stateModel.rowsPerPageProperty().addListener((obs, oldV, newV) -> setRowsPerPage(newV.intValue()));
        columnModel.cleanedProperty().addListener((obs, oldV, newV) -> refreshView()); // Neubau der Spalten

        if (searchField != null) {
            // Synchronisiert SearchText zwischen den Views (1-e)
            searchField.textProperty().bindBidirectional(stateModel.searchTextProperty());
            stateModel.searchTextProperty().addListener((obs, ov, nv) -> {
                if (serverPaginationEnabled) {
                    // Startet die Serversuche (via Controller-Hook)
                    if (onServerSearch != null) onServerSearch.accept(nv == null ? "" : nv.trim());
                } else {
                    // Startet die Clientsuche
                    filterData(nv);
                }
            });
        }
    }

    // ---------- Öffentliche gemeinsame API ----------

    public ReadOnlyBooleanProperty hasDataProperty() { return hasData; }
    public boolean hasData() { return hasData.get(); }

    public AbstractTableManager enableSearch() {
        if (searchField == null) return this;
        this.searchEnabled = true;
        configureSearchSection(true);
        return this;
    }

    public AbstractTableManager enablePagination(int initialRowsPerPage) {
        if (pagination == null) return this;
        this.paginationEnabled = true;
        stateModel.setRowsPerPage(initialRowsPerPage);
        pagination.setVisible(false);
        return this;
    }

    /**
     * Setzt die Daten für den Client-Side-Modus.
     * Achtung: Der Client-Modus sollte nur für kleine Datenmengen verwendet werden.
     */
    public void populateTableView(List<RowData> data) {
        serverPaginationEnabled = false;

        if (data == null || data.isEmpty()) {
            stateModel.setTotalCount(0);
            clearView();
            hasData.set(false);
            return;
        }

        this.filteredData = new ArrayList<>(data);
        stateModel.setTotalCount(data.size());
        hasData.set(true);

        // Client-Filter anwenden, falls Search aktiv
        if (searchEnabled && searchField != null) {
            filterData(searchField.getText());
        } else {
            refreshView();
        }
    }

    /**
     * Setzt die Daten für den Server-Side-Modus.
     * (Wird von runKernfrage() und setOnServerSearch() im Controller aufgerufen)
     */
    public void loadDataFromServer(int totalCount, DataLoader dataLoader) {
        this.serverPaginationEnabled = true;

        if (totalCount <= 0) {
            stateModel.setTotalCount(0);
            clearView();
            hasData.set(false);
            return;
        }

        stateModel.setTotalCount(totalCount);
        resultModel.setPageLoader(dataLoader); // Wichtig für den Export (2-b)
        hasData.set(true);

        if (pagination != null) {
            int rowsPerPage = stateModel.getRowsPerPage();
            pagination.setPageCount(Math.max(1, (int) Math.ceil((double) totalCount / rowsPerPage)));
            // setCurrentPageIndex(0) löst event aus, was loadServerPageData(0) aufruft
            pagination.setCurrentPageIndex(0);
            pagination.setVisible(true);
            pagination.setPageFactory(this::createServerPage);
        }

        updateResultsCount();
        loadServerPageData(0);
    }

    public AbstractTableManager setOnServerSearch(Consumer<String> handler) {
        this.onServerSearch = handler;
        return this;
    }

    public void setRowsPerPage(int rowsPerPage) {
        if (rowsPerPage <= 0 || !paginationEnabled) return;
        stateModel.setRowsPerPage(rowsPerPage);
        if (serverPaginationEnabled && pagination != null) {
            pagination.setPageCount(Math.max(1, (int) Math.ceil((double) stateModel.getTotalCount() / rowsPerPage)));
            // Bleibt auf der aktuellen Seite, wenn möglich, sonst wechselt zu letzter Seite
            int newIndex = Math.min(pagination.getCurrentPageIndex(), pagination.getPageCount() - 1);
            pagination.setCurrentPageIndex(newIndex);
            loadServerPageData(newIndex);
        } else if (!serverPaginationEnabled) {
            refreshView(); // Nur Client-Pagination muss neu gerendert werden
        }
    }

    public void bindAutoRowsPerPage(Region observedRegion) {
        final double chrome = 90.0;
        final double defaultRowH = 24.0;
        final int minRows = 10;
        final int changeThreshold = 2;
        Integer lastRowsPerPage = null;

        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        observedRegion.heightProperty().addListener((obs, oldH, newH) -> {
            debounce.stop();
            debounce.setOnFinished(evt -> {
                double h = (newH == null) ? 0 : newH.doubleValue();
                double rowH = defaultRowH;
                int rows = (int) Math.max(minRows, Math.floor((h - chrome) / rowH));
                if (lastRowsPerPage == null || Math.abs(rows - lastRowsPerPage) >= changeThreshold) {
                    setRowsPerPage(rows);
                }
            });
            debounce.playFromStart();
        });
    }

    /** Bereinigt alle Spalten, die auf ALLEN Seiten leer sind (1-a). */
    public void cleanColumnsAllPages() {
        if (!hasData()) {
            Dialog.showInfoDialog("Bereinigen", "Keine Daten vorhanden.");
            return;
        }
        if (stateModel.isCleaningApplied() && columnModel.isCleaned()) {
            Dialog.showInfoDialog("Bereinigen", "Die Spalten wurden bereits global bereinigt.");
            return;
        }

        if (!Dialog.showWarningDialog("Global Bereinigen",
                "Es werden Spalten entfernt, die im GESAMTEN Ergebnis leer sind. Dies kann bei großen Datenmengen einen Moment dauern. Fortfahren?")) {
            return;
        }

        // Deaktiviert den Button während des Jobs (löst Problem 2-a)
        Platform.runLater(() -> columnModel.getHiddenKeys().addListener(this::disableCleanButtonOnCleaned));

        EXECUTOR.submit(() -> {
            try {
                // 1. Alle Daten vom Server/lokal laden
                List<RowData> allRows = loadAllData();
                if (allRows.isEmpty()) {
                    Platform.runLater(() -> Dialog.showInfoDialog("Bereinigen", "Keine Daten zum Bereinigen verfügbar."));
                    return;
                }

                // 2. Original-Keys bestimmen (könnte leer sein, wenn nur 1 Spalte übrig ist)
                Set<String> allKeys = allRows.get(0).getValues().keySet();
                if (allKeys.isEmpty()) return;

                // 3. Spalten identifizieren, die komplett leer sind
                Set<String> keysToRemove = new HashSet<>();
                for (String key : allKeys) {
                    boolean allEmpty = true;
                    for (RowData row : allRows) {
                        String value = ColumnValueFormatter.format(row, key);
                        if (value != null && !value.trim().isEmpty()) {
                            allEmpty = false;
                            break;
                        }
                    }
                    if (allEmpty) keysToRemove.add(key);
                }

                // 4. Update des globalen Zustands
                Platform.runLater(() -> {
                    if (keysToRemove.isEmpty()) {
                        Dialog.showInfoDialog("Bereinigen", "Es gibt keine vollständig leeren Spalten im gesamten Ergebnis.");
                    } else {
                        // Wichtig: ColumnStateModel aktualisieren
                        columnModel.replaceHiddenKeys(keysToRemove);
                        stateModel.setCleaningApplied(true); // Status setzen (4-a)
                        Dialog.showInfoDialog("Bereinigen", keysToRemove.size() + " Spalte(n) wurden global ausgeblendet.");
                    }
                });

            } catch (Exception ex) {
                log.error("Global Bereinigen fehlgeschlagen", ex);
                Platform.runLater(() -> Dialog.showErrorDialog("Bereinigen Fehler", "Fehler beim Laden aller Daten: " + ex.getMessage()));
            } finally {
                // Entfernt den Listener nach dem Job (oder sollte im Sub-Manager bleiben)
                Platform.runLater(() -> columnModel.getHiddenKeys().removeListener(this::disableCleanButtonOnCleaned));
            }
        });
    }

    // ---------- Gemeinsame interne Logik ----------

    protected List<RowData> loadAllData() throws Exception {
        DataLoader loader = resultModel.getPageLoader();
        int total = resultModel.getTotalCount();
        if (loader == null || total <= 0) return Collections.emptyList();

        final int pageSize = 1000;
        int pages = (int) Math.ceil(total / (double) pageSize);
        List<RowData> all = new ArrayList<>(Math.min(total, 20000)); // Capping für zu große Mengen

        for (int p = 0; p < pages; p++) {
            List<RowData> chunk = loader.loadPage(p, pageSize);
            if (chunk != null && !chunk.isEmpty()) {
                all.addAll(chunk);
            }
        }
        return all;
    }

    // Wird vom Manager (Sub-Klasse) an den Clean Button gehängt
    protected abstract void disableCleanButtonOnCleaned(javafx.collections.SetChangeListener.Change<? extends String> c);


    protected void filterData(String filterText) {
        final String q = (filterText == null) ? "" : filterText.toLowerCase(Locale.ROOT).trim();

        // Achtung: Client-Modus speichert filteredData in seiner Sub-Klasse.
        // Die Sub-Klasse muss originalData halten. Da das hier ein Client-only-Filter ist,
        // nehmen wir an, dass die Sub-Klasse die Basisdaten hat.
        List<RowData> originalData = getOriginalDataForClientFilter();

        if (q.isEmpty()) {
            filteredData = new ArrayList<>(originalData);
        } else {
            filteredData = new ArrayList<>();
            for (RowData r : originalData) {
                boolean match = false;
                for (String v : r.getValues().values()) {
                    if (v != null && v.toLowerCase(Locale.ROOT).contains(q)) {
                        match = true;
                        break;
                    }
                }
                if (match) filteredData.add(r);
            }
        }

        // Im Client-Modus den totalCount aktualisieren
        if (!serverPaginationEnabled) {
            stateModel.setTotalCount(filteredData.size());
        }

        refreshView();
    }

    protected Node createServerPage(int pageIndex) {
        loadServerPageData(pageIndex);
        return new Label();
    }

    protected void loadServerPageData(int pageIndex) {
        DataLoader loader = resultModel.getPageLoader();
        if (loader == null) {
            log.error("DataLoader ist null, kann Seite {} nicht laden.", pageIndex);
            return;
        }

        EXECUTOR.submit(() -> {
            try {
                List<RowData> page = loader.loadPage(pageIndex, stateModel.getRowsPerPage());
                final List<RowData> finalPage = (page == null) ? Collections.emptyList() : page;
                Platform.runLater(() -> {
                    filteredData = finalPage;
                    refreshView();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> log.error("Seite {} konnte nicht geladen werden", pageIndex, ex));
            }
        });
    }

    protected void recomputeGroupStripes() {
        if (!groupStripingEnabled || groupStripingHeader == null) return;
        int colIdx = findHeaderIndexCaseInsensitive(groupStripingHeader);
        if (colIdx < 0) return;

        this.groupColIndex = colIdx;
        int n = getVisibleRowCount();
        stripeIsA = new ArrayList<>(n);
        String lastKey = null;
        boolean useA = true;
        for (int i = 0; i < n; i++) {
            // Achtung: getVisibleCellValue muss den formatierten Wert liefern
            String key = Optional.ofNullable(getVisibleCellValue(i, colIdx)).orElse("");
            if (!Objects.equals(key, lastKey)) {
                useA = !useA;
                lastKey = key;
            }
            stripeIsA.add(useA);
        }
        requestRefresh();
    }

    protected int findHeaderIndexCaseInsensitive(String header) {
        List<String> headers = currentHeaders();
        if (headers == null) return -1;
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h.equalsIgnoreCase(header) || h.replace(" ", "").equalsIgnoreCase(header.replace(" ", ""))) {
                return i;
            }
        }
        return -1;
    }

    // Groupement/Striping (ajouté pour la compatibilité)
    public AbstractTableManager enableGroupStripingByHeader(String headerName) {
        applyGroupingConfig(true, headerName, null, null);
        return this;
    }

    public void configureGrouping(String headerName, Color colorA, Color colorB) {
        applyGroupingConfig(headerName != null && !headerName.isBlank(), headerName, colorA, colorB);
    }

    public void disableGrouping() {
        applyGroupingConfig(false, null, null, null);
    }

    public void applyGroupingConfig(boolean enabled, String headerName, Color colorA, Color colorB) {
        this.groupStripingEnabled = enabled && headerName != null && !headerName.isBlank();
        this.groupStripingHeader = this.groupStripingEnabled ? headerName : null;
        this.groupColorA = this.groupStripingEnabled ? colorA : null;
        this.groupColorB = this.groupStripingEnabled ? colorB : null;
        if (!groupStripingEnabled) stripeIsA.clear();
        installGroupRowFactory(); // Hook abstraite
        recomputeGroupStripes();
    }

    protected void refreshCommonState() {
        hasData.set(!filteredData.isEmpty());
        updateResultsCount();
    }



    // ---------- Abstrakte Hooks (UI-spezifisch) ----------

    /** Liefert die Datenbasis (alle Daten) für den Client-Filter. */
    protected abstract List<RowData> getOriginalDataForClientFilter();

    protected abstract void configureSearchSection(boolean visible);
    protected abstract List<String> currentHeaders();
    protected abstract int getVisibleRowCount();
    protected abstract String getVisibleCellValue(int rowIndex, int columnIndex);
    protected abstract void installGroupRowFactory();
    protected abstract void refreshView();
    protected abstract void updateResultsCount();
    protected abstract void clearView();
    protected abstract void requestRefresh();

    public abstract List<String> getDisplayHeaders();
    public abstract List<String> getOriginalKeys();
}