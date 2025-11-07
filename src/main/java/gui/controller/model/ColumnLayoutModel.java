package gui.controller.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ColumnLayoutModel
 *
 * <p>
 * Repräsentiert die vollständige Spaltenkonfiguration für eine bestimmte logische Ansicht
 * (z.B. "COVER_MAIN"). Ziel ist es, Folgendes zu zentralisieren:
 * </p>
 *
 * <ul>
 *   <li>die Reihenfolge der Spalten,</li>
 *   <li>deren Sichtbarkeit (entfernt / angezeigt),</li>
 *   <li>deren angezeigte Beschriftung (Umbenennung),</li>
 *   <li>einen abgeleiteten "bereinigten" Zustand.</li>
 * </ul>
 *
 * <p>
 * Dieses Modell ist dafür konzipiert, von mehreren Anzeigekomponenten geteilt zu werden,
 * typischerweise einem {@code TableView} und einem {@code TreeTableView}, um eine
 * vollständige Synchronisation (Umbenennen, Löschen, Neuanordnen, Bereinigen) zu gewährleisten.
 * </p>
 *
 * <p>
 * Es kann zusammen mit {@link ColumnStateModel} aus Gründen der Abwärtskompatibilität verwendet werden:
 * Wenn ein {@code ColumnStateModel} bereitgestellt wird, ist die Eigenschaft {@link #cleanedProperty()}
 * automatisch an dessen Zustand gebunden.
 * </p>
 */
public class ColumnLayoutModel {

    private static final Logger log = LoggerFactory.getLogger(ColumnLayoutModel.class);

    /** Logische ID des Layouts (z.B. "COVER_MAIN"). */
    private final StringProperty layoutId = new SimpleStringProperty("");

    /** Geordnete Einträge, die jede Spalte beschreiben. */
    private final ObservableList<ColumnLayoutEntry> columns =
            FXCollections.observableArrayList();

    /**
     * Zeigt an, ob mindestens eine Spalte als nicht sichtbar markiert ist.
     *
     * <ul>
     *   <li>Wenn ein {@link ColumnStateModel} dem Konstruktor übergeben wird, ist diese
     *       Eigenschaft an dessen {@code cleanedProperty()} gebunden.</li>
     *   <li>Andernfalls wird sie lokal aus {@link #columns} abgeleitet.</li>
     * </ul>
     */
    private final ReadOnlyBooleanWrapper cleaned = new ReadOnlyBooleanWrapper(false);

    /** Optional: Kompatibilitätslink zum historischen Modell. */
    private final ColumnStateModel columnStateModel;

    /**
     * Erstellt ein Layout ohne explizite Integration in {@link ColumnStateModel}.
     *
     * @param layoutId logische ID (nicht null, vorzugsweise nicht leer)
     */
    public ColumnLayoutModel(String layoutId) {
        this(layoutId, null);
    }

    /**
     * Erstellt ein Layout und verbindet es (optional) mit einem existierenden
     * {@link ColumnStateModel}, um das historische Verhalten (hiddenKeys / cleaned) beizubehalten.
     *
     * @param layoutId         logische ID
     * @param columnStateModel Legacy-Modell für den Bereinigungszustand
     */
    public ColumnLayoutModel(String layoutId, ColumnStateModel columnStateModel) {
        this.columnStateModel = columnStateModel;
        setLayoutId(Objects.requireNonNull(layoutId, "layoutId"));
        initCleanBinding();
        // Überwacht Änderungen an der Spaltenliste, wenn wir nicht
        // an ColumnStateModel gebunden sind.
        if (this.columnStateModel == null) {
            columns.addListener((ListChangeListener<ColumnLayoutEntry>) change -> updateCleanedFromColumns());
        }
    }

    // -------------------------------------------------------------------------
    // Initialisierung / Ableitung von "cleaned"
    // -------------------------------------------------------------------------

    private void initCleanBinding() {
        if (columnStateModel != null) {
            // Kompatibilität: "cleaned" ist direkt an ColumnStateModel.cleanedProperty() gebunden.
            cleaned.bind(columnStateModel.cleanedProperty());
        } else {
            // Leitet "cleaned" lokal aus der Sichtbarkeit der Spalten ab.
            updateCleanedFromColumns();
        }
    }

    private void updateCleanedFromColumns() {
        if (columnStateModel != null) {
            return; // in diesem Fall sind wir bereits gebunden, keine lokale Logik
        }
        boolean anyHidden = columns.stream().anyMatch(c -> !c.isVisible());
        cleaned.set(anyHidden);
    }

    // -------------------------------------------------------------------------
    // Haupt-API
    // -------------------------------------------------------------------------

    public final String getLayoutId() {
        return layoutId.get();
    }

    public final void setLayoutId(String layoutId) {
        this.layoutId.set(layoutId);
    }

    public StringProperty layoutIdProperty() {
        return layoutId;
    }

    /**
     * Beobachtbare Liste von Spalteneinträgen.
     * <p>
     * Achtung: Direkte Änderungen (add/remove) müssen konsistent bleiben.
     * In den meisten Fällen ist es besser, die Hilfsmethoden
     * {@code applyDefaultFromHeaders}, {@code renameColumn}, {@code setColumnVisibility},
     * {@code moveColumn} usw. zu verwenden.
     * </p>
     */
    public ObservableList<ColumnLayoutEntry> getColumns() {
        return columns;
    }

    /** Read-only Eigenschaft, die anzeigt, ob Spalten ausgeblendet wurden (Bereinigung). */
    public ReadOnlyBooleanProperty cleanedProperty() {
        return cleaned.getReadOnlyProperty();
    }

    public boolean isCleaned() {
        return cleaned.get();
    }

    // -------------------------------------------------------------------------
    // Hochrangige Methoden zur Layoutverwaltung
    // -------------------------------------------------------------------------

    /**
     * Initialisiert das Layout aus einer Liste von Headern (natürliche Reihenfolge).
     * <p>
     * Jeder Header wird zu:
     * <ul>
     *   <li>originalKey = header.trim()</li>
     *   <li>displayName = header.trim()</li>
     *   <li>visible = true</li>
     *   <li>orderIndex = Index in der Liste</li>
     * </ul>
     *
     * Jede vorherige Konfiguration wird gelöscht.
     */
    public void applyDefaultFromHeaders(List<String> headers) {
        Objects.requireNonNull(headers, "headers");
        columns.clear();
        int index = 0;
        for (String header : headers) {
            if (header == null || header.trim().isEmpty()) {
                continue;
            }
            String key = header.trim();
            ColumnLayoutEntry entry = new ColumnLayoutEntry(key, key, true, index++);
            columns.add(entry);
        }
        updateCleanedFromColumns();
        if (columnStateModel != null) {
            // Minimale Aktualisierung des Legacy-Modells, um konsistent zu bleiben
            columnStateModel.replaceHiddenKeys(Collections.emptyList());
        }
        log.debug("ColumnLayoutModel[{}]: applyDefaultFromHeaders -> {} Spalten",
                getLayoutId(), columns.size());
    }

    /**
     * Benennt die Spalte, die {@code originalKey} entspricht, um.
     *
     * @param originalKey     technischer Schlüssel (RowData)
     * @param newDisplayName  neue angezeigte Beschriftung (nicht null)
     * @return true, wenn eine Spalte gefunden und geändert wurde
     */
    public boolean renameColumn(String originalKey, String newDisplayName) {
        Objects.requireNonNull(originalKey, "originalKey");
        Objects.requireNonNull(newDisplayName, "newDisplayName");
        for (ColumnLayoutEntry entry : columns) {
            if (originalKey.equals(entry.getOriginalKey())) {
                String old = entry.getDisplayName();
                entry.setDisplayName(newDisplayName);
                log.debug("ColumnLayoutModel[{}]: renameColumn {} -> {}",
                        getLayoutId(), old, newDisplayName);
                return true;
            }
        }
        log.warn("ColumnLayoutModel[{}]: renameColumn - unbekannter Schlüssel={}", getLayoutId(), originalKey);
        return false;
    }

    /**
     * Ändert die Sichtbarkeit einer Spalte.
     *
     * @param originalKey technischer Schlüssel
     * @param visible     true zum Anzeigen, false zum Ausblenden
     * @return true, wenn geändert
     */
    public boolean setColumnVisibility(String originalKey, boolean visible) {
        Objects.requireNonNull(originalKey, "originalKey");
        for (ColumnLayoutEntry entry : columns) {
            if (originalKey.equals(entry.getOriginalKey())) {
                if (entry.isVisible() == visible) {
                    return false; // keine Änderung
                }
                entry.setVisible(visible);
                // Minimale Synchronisation mit ColumnStateModel, falls vorhanden
                if (columnStateModel != null) {
                    if (!visible) {
                        columnStateModel.addHiddenKey(originalKey);
                    } else {
                        columnStateModel.removeHiddenKey(originalKey);
                    }
                } else {
                    updateCleanedFromColumns();
                }
                log.debug("ColumnLayoutModel[{}]: setColumnVisibility key={} visible={}",
                        getLayoutId(), originalKey, visible);
                return true;
            }
        }
        log.warn("ColumnLayoutModel[{}]: setColumnVisibility - unbekannter Schlüssel={}", getLayoutId(), originalKey);
        return false;
    }

    /**
     * Markiert eine Liste von Spalten als ausgeblendet (visible=false).
     * Nützlich für die globale Bereinigen-Funktion.
     */
    public void hideColumns(Collection<String> originalKeys) {
        Objects.requireNonNull(originalKeys, "originalKeys");
        boolean changed = false;
        for (String key : originalKeys) {
            if (setColumnVisibility(key, false)) {
                changed = true;
            }
        }
        if (changed && columnStateModel == null) {
            updateCleanedFromColumns();
        }
    }

    /**
     * Setzt die Sichtbarkeit aller Spalten auf true zurück.
     * Ändert weder Beschriftungen noch Reihenfolge.
     */
    public void showAllColumns() {
        boolean changed = false;
        for (ColumnLayoutEntry entry : columns) {
            if (!entry.isVisible()) {
                entry.setVisible(true);
                changed = true;
            }
        }
        if (columnStateModel != null) {
            columnStateModel.replaceHiddenKeys(Collections.emptyList());
        }
        if (changed && columnStateModel == null) {
            updateCleanedFromColumns();
        }
        log.debug("ColumnLayoutModel[{}]: showAllColumns() -> {} sichtbare Spalten",
                getLayoutId(), columns.size());
    }

    /**
     * Verschiebt die durch {@code originalKey} identifizierte Spalte an die neue Position {@code newIndex}.
     * Die anderen {@link ColumnLayoutEntry#orderIndex} werden von 0..N-1 neu berechnet.
     *
     * @param originalKey technischer Schlüssel
     * @param newIndex    Zielindex (wird zwischen 0 und size-1 geklemmt)
     * @return true, wenn die Spalte gefunden und verschoben wurde
     */
    public boolean moveColumn(String originalKey, int newIndex) {
        Objects.requireNonNull(originalKey, "originalKey");
        if (columns.isEmpty()) {
            return false;
        }
        int fromIndex = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (originalKey.equals(columns.get(i).getOriginalKey())) {
                fromIndex = i;
                break;
            }
        }
        if (fromIndex < 0) {
            log.warn("ColumnLayoutModel[{}]: moveColumn - unbekannter Schlüssel={}", getLayoutId(), originalKey);
            return false;
        }
        if (fromIndex == newIndex) {
            return false;
        }
        // Klemmen
        int targetIndex = Math.max(0, Math.min(newIndex, columns.size() - 1));
        ColumnLayoutEntry entry = columns.remove(fromIndex);
        columns.add(targetIndex, entry);
        // Neuberechnung der orderIndex
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).setOrderIndex(i);
        }
        log.debug("ColumnLayoutModel[{}]: moveColumn key={} von={} nach={}",
                getLayoutId(), originalKey, fromIndex, targetIndex);
        return true;
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden zum Lesen
    // -------------------------------------------------------------------------

    /** Gibt die sichtbaren Einträge zurück, sortiert nach orderIndex. */
    public List<ColumnLayoutEntry> getVisibleEntries() {
        return columns.stream()
                .filter(ColumnLayoutEntry::isVisible)
                .sorted(Comparator.comparingInt(ColumnLayoutEntry::getOrderIndex))
                .collect(Collectors.toList());
    }

    /** Gibt die sichtbaren originalKeys in der Anzeigereihenfolge zurück. */
    public List<String> getVisibleOriginalKeys() {
        return getVisibleEntries().stream()
                .map(ColumnLayoutEntry::getOriginalKey)
                .collect(Collectors.toList());
    }

    /** Gibt die sichtbaren displayNames in der Anzeigereihenfolge zurück. */
    public List<String> getVisibleDisplayNames() {
        return getVisibleEntries().stream()
                .map(ColumnLayoutEntry::getDisplayName)
                .collect(Collectors.toList());
    }

    /** Gibt den Eintrag zurück, der einem technischen Schlüssel entspricht, oder null, falls nicht vorhanden. */
    public ColumnLayoutEntry findByOriginalKey(String originalKey) {
        if (originalKey == null) {
            return null;
        }
        for (ColumnLayoutEntry entry : columns) {
            if (originalKey.equals(entry.getOriginalKey())) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ColumnLayoutModel{" +
                "layoutId=" + getLayoutId() +
                ", columns=" + columns +
                '}';
    }

    // -------------------------------------------------------------------------
    // Innere Klasse: ColumnLayoutEntry
    // -------------------------------------------------------------------------

    /**
     * Layout-Eintrag für eine Spalte.
     *
     * <p>
     * Trennt klar:
     * </p>
     * <ul>
     *   <li>{@code originalKey}: stabiler Schlüssel, der in RowData / DB verwendet wird,</li>
     *   <li>{@code displayName}: angezeigte Beschriftung (umbenennbar),</li>
     *   <li>{@code visible}: Spalte angezeigt oder nicht,</li>
     *   <li>{@code orderIndex}: Position in der Anzeigereihenfolge.</li>
     * </ul>
     */
    public static class ColumnLayoutEntry {

        /** Der ursprüngliche, technische Schlüssel der Spalte (z.B. der Feldname in der Datenbank). */
        private final StringProperty originalKey = new SimpleStringProperty();
        /** Der für die Anzeige verwendete Name der Spalte, der umbenannt werden kann. */
        private final StringProperty displayName = new SimpleStringProperty();
        /** Gibt an, ob die Spalte sichtbar ist (true) oder ausgeblendet (false). */
        private final BooleanProperty visible = new SimpleBooleanProperty(true);
        /** Der Index, der die Position der Spalte in der Anzeigereihenfolge bestimmt. */
        private final IntegerProperty orderIndex = new SimpleIntegerProperty(0);

        public ColumnLayoutEntry(String originalKey,
                                 String displayName,
                                 boolean visible,
                                 int orderIndex) {
            this.originalKey.set(Objects.requireNonNull(originalKey, "originalKey"));
            this.displayName.set(Objects.requireNonNull(displayName, "displayName"));
            this.visible.set(visible);
            this.orderIndex.set(orderIndex);
        }

        public String getOriginalKey() {
            return originalKey.get();
        }

        public StringProperty originalKeyProperty() {
            return originalKey;
        }

        public String getDisplayName() {
            return displayName.get();
        }

        public void setDisplayName(String displayName) {
            this.displayName.set(Objects.requireNonNull(displayName, "displayName"));
        }

        public StringProperty displayNameProperty() {
            return displayName;
        }

        public boolean isVisible() {
            return visible.get();
        }

        public void setVisible(boolean visible) {
            this.visible.set(visible);
        }

        public BooleanProperty visibleProperty() {
            return visible;
        }

        public int getOrderIndex() {
            return orderIndex.get();
        }

        public void setOrderIndex(int orderIndex) {
            this.orderIndex.set(orderIndex);
        }

        public IntegerProperty orderIndexProperty() {
            return orderIndex;
        }

        @Override
        public String toString() {
            return "ColumnLayoutEntry{" +
                    "originalKey=" + getOriginalKey() +
                    ", displayName=" + getDisplayName() +
                    ", visible=" + isVisible() +
                    ", orderIndex=" + getOrderIndex() +
                    '}';
        }
    }
}