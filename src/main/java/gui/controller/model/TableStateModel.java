package gui.controller.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TableStateModel
 *
 * <p>Centralise l'état dynamique de l'interface utilisateur (UI)
 * partagée entre TableView et TreeTableView.
 *
 * <h3>Contenu (Synchronisation UI)</h3>
 * <ul>
 * <li>{@code searchText}: Texte de la barre de recherche.</li>
 * <li>{@code expansionState}: État d'expansion des nœuds (pour TreeTable).</li>
 * <li>{@code cleaningApplied}: Indique qu'une action de nettoyage global a été effectuée.</li>
 * <li>{@code totalCount}: Nombre total de résultats (pour la cohérence des labels).</li>
 * <li>{@code rowsPerPage}: Taille de page actuelle (pour la cohérence de la pagination).</li>
 * </ul>
 *
 * <p>Remarque : l'état des colonnes masquées est géré dans {@link ColumnStateModel}.
 */
public final class TableStateModel {

    private static final Logger log = LoggerFactory.getLogger(TableStateModel.class);

    private final StringProperty searchText = new SimpleStringProperty("");
    private final BooleanProperty searchActive = new SimpleBooleanProperty(false);
    private final IntegerProperty totalCount = new SimpleIntegerProperty(0);
    private final IntegerProperty currentPageIndex = new SimpleIntegerProperty(0);
    private final IntegerProperty rowsPerPage = new SimpleIntegerProperty(100);

    /** État d'expansion par clé de nœud (pour TreeTable). */
    private final ObservableMap<String, Boolean> expansionState = FXCollections.observableHashMap();

    /** État informatif: True si la fonction Bereinigen (global) a déjà été exécutée. */
    private final BooleanProperty cleaningApplied = new SimpleBooleanProperty(false);


    // --- Search Text ---

    public StringProperty searchTextProperty() { return searchText; }
    public String getSearchText() { return searchText.get(); }
    public void setSearchText(String text) {
        String old = searchText.get();
        searchText.set(text == null ? "" : text.trim());
        if (!String.valueOf(old).trim().equals(searchText.get())) {
            log.debug("TableStateModel: searchText='{}'", getSearchText());
        }
    }

    // --- Search Active ---

    public BooleanProperty searchActiveProperty() { return searchActive; }
    public boolean isSearchActive() { return searchActive.get(); }
    public void setSearchActive(boolean active) {
        boolean old = searchActive.get();
        searchActive.set(active);
        if (old != active) log.debug("TableStateModel: searchActive={} -> {}", old, active);
    }

    // --- Total Count ---

    public IntegerProperty totalCountProperty() { return totalCount; }
    public int getTotalCount() { return totalCount.get(); }
    public void setTotalCount(int count) {
        int old = totalCount.get();
        totalCount.set(Math.max(0, count));
        if (old != count) log.debug("TableStateModel: totalCount={}", getTotalCount());
    }

    // --- Rows Per Page ---

    public IntegerProperty rowsPerPageProperty() { return rowsPerPage; }
    public int getRowsPerPage() { return rowsPerPage.get(); }
    public void setRowsPerPage(int count) {
        int old = rowsPerPage.get();
        rowsPerPage.set(Math.max(10, count)); // Minimum de 10
        if (old != rowsPerPage.get()) log.debug("TableStateModel: rowsPerPage={}", getRowsPerPage());
    }

    // --- Expansion State (Tree) ---

    public ObservableMap<String, Boolean> getExpansionState() { return expansionState; }

    public void putExpansion(String key, boolean expanded) {
        expansionState.put(key, expanded);
    }

    public void clearExpansion() {
        expansionState.clear();
    }

    // --- Cleaning Status ---

    public BooleanProperty cleaningAppliedProperty() { return cleaningApplied; }
    public boolean isCleaningApplied() { return cleaningApplied.get(); }
    public void setCleaningApplied(boolean applied) {
        boolean old = cleaningApplied.get();
        cleaningApplied.set(applied);
        if (old != applied) log.info("TableStateModel: cleaningApplied={} -> {}", old, applied);
    }

    public IntegerProperty currentPageIndexProperty() { return currentPageIndex; }
    public int getCurrentPageIndex() { return currentPageIndex.get(); }
    public void setCurrentPageIndex(int index) {
        int old = currentPageIndex.get();
        int clamped = Math.max(0, index);
        currentPageIndex.set(clamped);
        if (old != clamped) {
            log.debug("TableStateModel: currentPageIndex={} -> {}", old, clamped);
        }
    }

    /** Réinitialise les états de recherche et de nettoyage. */
    public void reset() {
        setSearchText("");
        setSearchActive(false);
        setTotalCount(0);
        setCleaningApplied(false);
        setCurrentPageIndex(0);
        clearExpansion();
        log.info("TableStateModel: Reset complete.");
    }

    @Override
    public String toString() {
        return "TableStateModel{search='" + getSearchText() + "', active=" + isSearchActive() +
                ", expanded=" + expansionState.size() + ", cleaned=" + isCleaningApplied() +
                ", total=" + getTotalCount() + ", pageSize=" + getRowsPerPage() + "}";
    }
}