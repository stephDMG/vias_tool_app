package gui.controller.model;

import gui.controller.manager.DataLoader;
import javafx.beans.property.*;
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
    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.KF);
    private final ObjectProperty<CoverFilter> filter = new SimpleObjectProperty<>(null);
    private final IntegerProperty totalCount = new SimpleIntegerProperty(0);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    // NOUVEAU: Eigenschaft pour le binding de l'export
    private final ReadOnlyBooleanWrapper canExport = new ReadOnlyBooleanWrapper(false);
    private final BooleanProperty fullNameMode = new SimpleBooleanProperty(false);
    /**
     * Optional: Speichert, ob der Baum (Tree) aktiv ist – hilfreich für Export-Entscheidungen.
     */
    private final BooleanProperty treeViewActive = new SimpleBooleanProperty(false);
    private CoverFilter lastValidFilter = null;
    /**
     * Loader, der <b>exakt</b> den gleichen Datenpfad nutzt wie die UI beim Paging.
     * Wird beim Export seitenweise aufgerufen.
     */
    private DataLoader pageLoader;

    public ResultContextModel() {
        // Ajout de listeners pour mettre à jour canExportProperty
        filter.addListener((obs, oldV, newV) -> updateCanExport());
        totalCount.addListener((obs, oldV, newV) -> updateCanExport());
        // pageLoader n'a pas de property, sa mise à jour est manuelle via setPageLoader
        updateCanExport(); // Initial call
    }

    public final boolean isFullNameMode() {
        return fullNameMode.get();
    }

    public final void setFullNameMode(boolean v) {
        fullNameMode.set(v);
    }

    public final BooleanProperty fullNameModeProperty() {
        return fullNameMode;
    }

    private void updateCanExport() {
        boolean isExportPossible = pageLoader != null && getTotalCount() > 0 && getFilter() != null;
        canExport.set(isExportPossible);
    }

    public ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    // --- Mode ---

    public Mode getMode() {
        return mode.get();
    }

    public void setMode(Mode m) {
        if (m == null) m = Mode.KF;
        Mode old = mode.get();
        mode.set(m);
        if (old != m) {
            log.info("ResultContextModel: mode={} -> {}", old, m);
        }
    }

    public ObjectProperty<CoverFilter> filterProperty() {
        return filter;
    }

    // --- Filter ---

    public CoverFilter getFilter() {
        return filter.get();
    }

    /**
     * Setzt den aktiven Filter und speichert diesen als den letzten bekannten gültigen Filter.
     *
     * @param f Der neue Filter (darf nicht null sein).
     */
    public void setFilter(CoverFilter f) {
        Objects.requireNonNull(f, "CoverFilter darf nicht null sein. Verwenden Sie stattdessen einen leeren Filter.");
        this.filter.set(f);
        this.lastValidFilter = f; // NEU: Speicherung des Filters
        log.debug("ResultContextModel: filter gesetzt (withVersion={}, searchTerm={})",
                f.getWithVersion(), f.getSearchTerm());
        updateCanExport();
    }

    public IntegerProperty totalCountProperty() {
        return totalCount;
    }

    // --- Total Count ---

    public int getTotalCount() {
        return totalCount.get();
    }

    public void setTotalCount(int count) {
        this.totalCount.set(Math.max(0, count));
        log.debug("ResultContextModel: totalCount={}", this.totalCount.get());
        updateCanExport();
    }

    public DataLoader getPageLoader() {
        return pageLoader;
    }

    // --- Page Loader ---

    /**
     * Setzt den Seiten-Loader, der 1:1 von der UI genutzt wird.
     * <p>Wichtig: beim Wechsel KF ↔ Suche stets den passenden Loader setzen.</p>
     */
    public void setPageLoader(DataLoader loader) {
        this.pageLoader = loader;
        log.debug("ResultContextModel: pageLoader gesetzt ({})", loader != null ? "OK" : "null");
        updateCanExport();
    }

    public BooleanProperty treeViewActiveProperty() {
        return treeViewActive;
    }

    // --- TreeView Status (optional) ---

    public boolean isTreeViewActive() {
        return treeViewActive.get();
    }

    public void setTreeViewActive(boolean active) {
        boolean old = treeViewActive.get();
        treeViewActive.set(active);
        if (old != active) {
            log.debug("ResultContextModel: treeViewActive={} -> {}", old, active);
        }
    }

    /**
     * Liefert true, wenn ein Export sinnvoll möglich ist (beliebiger Modus, aber mit Daten & Loader).
     */
    // La méthode existante est conservée pour la rétrocompatibilité du getter direct
    public boolean canExport() {
        return canExport.get();
    }

    // --- Hilfsprüfungen ---

    /**
     * NEU: ReadOnlyProperty für UI-Binding (résout l'erreur).
     */
    public ReadOnlyBooleanProperty canExportProperty() {
        return canExport.getReadOnlyProperty();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public void setLoading(boolean v) {
        loading.set(v);
    }

    /**
     * Modus der Ergebnis-Erzeugung.
     */
    public enum Mode {KF, SEARCH}
}