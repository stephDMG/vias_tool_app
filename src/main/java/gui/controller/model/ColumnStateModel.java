package gui.controller.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;

/**
 * ColumnStateModel
 *
 * <p>Zentraler, gemeinsam genutzter Zustand für die Spaltenkonfiguration
 * (z.B. globale "Bereinigen"-Aktionen) zwischen TableView und TreeTableView.
 *
 * <h3>Grundsätze</h3>
 * <ul>
 *   <li>Die Menge {@code hiddenKeys} enthält ausschließlich <b>kanonische</b> Spaltenschlüssel
 *       (also die {@code originalKeys}), niemals Anzeigenamen.</li>
 *   <li>{@code cleaned} ist eine abgeleitete Sicht, die signalisiert, ob aktuell mindestens
 *       eine Spalte global ausgeblendet ist.</li>
 *   <li>Beide Manager (EnhancedTableManager, TreeTableManager) sollen <b>nur lesen/schreiben</b>
 *       über dieses Modell, jedoch keinen eigenen lokalen "Hidden-Status" pflegen.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * <p>Das Modell nutzt JavaFX-Observable-Datenstrukturen. Mutationen sollten auf dem FX-Thread erfolgen.</p>
 */
public final class ColumnStateModel {

    private static final Logger log = LoggerFactory.getLogger(ColumnStateModel.class);

    /** Kanonische Spaltenschlüssel (originalKeys), die global ausgeblendet werden sollen. */
    private final ObservableSet<String> hiddenKeys = FXCollections.observableSet();

    /** Abgeleitet: true, sobald {@code hiddenKeys} nicht leer ist. */
    private final ReadOnlyBooleanWrapper cleaned = new ReadOnlyBooleanWrapper(false);

    public ColumnStateModel() {
        // Ableiten von "cleaned" aus hiddenKeys (Größe > 0)
        hiddenKeys.addListener((javafx.collections.SetChangeListener<String>) change -> {
            boolean nowCleaned = !hiddenKeys.isEmpty();
            boolean old = cleaned.get();
            cleaned.set(nowCleaned);
            if (nowCleaned != old) {
                log.info("ColumnStateModel: cleaned={} (hiddenKeys.size={})", nowCleaned, hiddenKeys.size());
            } else {
                log.debug("ColumnStateModel: hiddenKeys updated (size={})", hiddenKeys.size());
            }
        });
    }

    /**
     * Liefert die <b>Observable</b>-Menge der kanonischen Spaltenschlüssel, die global auszublenden sind.
     * <p>Nur {@code originalKeys} verwenden – keine Anzeigenamen.</p>
     */
    public ObservableSet<String> getHiddenKeys() {
        return hiddenKeys;
    }

    /**
     * Ersetzt den aktuellen Satz an versteckten Spalten vollständig.
     * @param keys neue Menge (null oder leer → keine Spalten ausblenden)
     */
    public void replaceHiddenKeys(Collection<String> keys) {
        hiddenKeys.clear();
        if (keys != null && !keys.isEmpty()) {
            for (String k : keys) {
                if (k != null && !k.isBlank()) {
                    hiddenKeys.add(k);
                }
            }
        }
        log.info("ColumnStateModel: replaceHiddenKeys -> {}", hiddenKeys);
    }

    /** Fügt eine einzelne Spalte (originalKey) hinzu. */
    public void addHiddenKey(String originalKey) {
        Objects.requireNonNull(originalKey, "originalKey");
        if (hiddenKeys.add(originalKey)) {
            log.debug("ColumnStateModel: addHiddenKey {}", originalKey);
        }
    }

    /** Entfernt eine einzelne Spalte (originalKey). */
    public void removeHiddenKey(String originalKey) {
        Objects.requireNonNull(originalKey, "originalKey");
        if (hiddenKeys.remove(originalKey)) {
            log.debug("ColumnStateModel: removeHiddenKey {}", originalKey);
        }
    }

    /** Löscht alle Hidden-Keys (setzt den globalen Bereinigungszustand zurück). */
    public void clear() {
        hiddenKeys.clear();
        log.info("ColumnStateModel: clear()");
    }

    /**
     * Abgeleitete Sicht (read-only): true, wenn mindestens eine Spalte ausgeblendet ist.
     * <p>Bindet Buttons wie "Bereinigen" bequem auf UI-Ebene.</p>
     */
    public ReadOnlyBooleanProperty cleanedProperty() {
        return cleaned.getReadOnlyProperty();
    }

    /** Direkter Getter für den aktuellen Zustand (siehe {@link #cleanedProperty()}). */
    public boolean isCleaned() {
        return cleaned.get();
    }
}
