package gui.controller.model;

import gui.controller.manager.DataLoader;
import javafx.beans.property.*;
import model.RowData;
import model.contract.filters.CoverFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * ResultContextModel
 *
 * <p>Beschreibt den aktuell sichtbaren Ergebnis-Kontext – unabhängig davon,
 * ob er aus einer Kernfrage (Ausführen) oder einer freien Suche (Suchen) stammt.</p>
 */
public class ResultContextModel {

    private static final Logger log = LoggerFactory.getLogger(ResultContextModel.class);

    /** Modus der Ergebnis-Erzeugung. */
    public enum Mode { KF, SEARCH }

    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.KF);
    private final ObjectProperty<CoverFilter> filter = new SimpleObjectProperty<>(null);
    private final IntegerProperty totalCount = new SimpleIntegerProperty(0);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    // NOUVEAU: Eigenschaft pour le binding de l'export
    private final ReadOnlyBooleanWrapper canExport = new ReadOnlyBooleanWrapper(false);

    /**
     * Loader, der <b>exakt</b> den gleichen Datenpfad nutzt wie die UI beim Paging.
     * Wird beim Export seitenweise aufgerufen.
     */
    private DataLoader pageLoader;

    /** Optional: Speichert, ob der Baum (Tree) aktiv ist – hilfreich für Export-Entscheidungen. */
    private final BooleanProperty treeViewActive = new SimpleBooleanProperty(false);

    public ResultContextModel() {
        // Ajout de listeners pour mettre à jour canExportProperty
        filter.addListener((obs, oldV, newV) -> updateCanExport());
        totalCount.addListener((obs, oldV, newV) -> updateCanExport());
        // pageLoader n'a pas de property, sa mise à jour est manuelle via setPageLoader
        updateCanExport(); // Initial call
    }

    private void updateCanExport() {
        boolean isExportPossible = pageLoader != null && getTotalCount() > 0 && getFilter() != null;
        canExport.set(isExportPossible);
    }

    // --- Mode ---

    public ObjectProperty<Mode> modeProperty() { return mode; }
    public Mode getMode() { return mode.get(); }

    public void setMode(Mode m) {
        if (m == null) m = Mode.KF;
        Mode old = mode.get();
        mode.set(m);
        if (old != m) {
            log.info("ResultContextModel: mode={} -> {}", old, m);
        }
    }

    // --- Filter ---

    public ObjectProperty<CoverFilter> filterProperty() { return filter; }
    public CoverFilter getFilter() { return filter.get(); }

    public void setFilter(CoverFilter f) {
        Objects.requireNonNull(f, "CoverFilter");
        this.filter.set(f);
        log.debug("ResultContextModel: filter gesetzt (withVersion={}, searchTerm={})",
                f.getWithVersion(), f.getSearchTerm());
        updateCanExport();
    }

    // --- Total Count ---

    public IntegerProperty totalCountProperty() { return totalCount; }
    public int getTotalCount() { return totalCount.get(); }

    public void setTotalCount(int count) {
        this.totalCount.set(Math.max(0, count));
        log.debug("ResultContextModel: totalCount={}", this.totalCount.get());
        updateCanExport();
    }

    // --- Page Loader ---

    public DataLoader getPageLoader() { return pageLoader; }

    /**
     * Setzt den Seiten-Loader, der 1:1 von der UI genutzt wird.
     * <p>Wichtig: beim Wechsel KF ↔ Suche stets den passenden Loader setzen.</p>
     */
    public void setPageLoader(DataLoader loader) {
        this.pageLoader = loader;
        log.debug("ResultContextModel: pageLoader gesetzt ({})", loader != null ? "OK" : "null");
        updateCanExport();
    }

    // --- TreeView Status (optional) ---

    public BooleanProperty treeViewActiveProperty() { return treeViewActive; }
    public boolean isTreeViewActive() { return treeViewActive.get(); }
    public void setTreeViewActive(boolean active) {
        boolean old = treeViewActive.get();
        treeViewActive.set(active);
        if (old != active) {
            log.debug("ResultContextModel: treeViewActive={} -> {}", old, active);
        }
    }

    // --- Hilfsprüfungen ---

    /** Liefert true, wenn ein Export sinnvoll möglich ist (beliebiger Modus, aber mit Daten & Loader). */
    // La méthode existante est conservée pour la rétrocompatibilité du getter direct
    public boolean canExport() {
        return canExport.get();
    }

    /** NEU: ReadOnlyProperty für UI-Binding (résout l'erreur). */
    public ReadOnlyBooleanProperty canExportProperty() {
        return canExport.getReadOnlyProperty();
    }

    public BooleanProperty loadingProperty() { return loading; }
    public boolean isLoading() { return loading.get(); }
    public void setLoading(boolean v) { loading.set(v); }
}