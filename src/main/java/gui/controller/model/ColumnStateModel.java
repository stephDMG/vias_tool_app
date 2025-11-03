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
public class ColumnStateModel {

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
        Collection<String> safe = (keys == null) ? java.util.Collections.emptySet() : keys;

        // Remplacer le contenu : clear() puis addAll(...)
        if (!hiddenKeys.isEmpty()) {
            hiddenKeys.clear();                     // déclenche un change event (SetChangeListener)
        }
        if (!safe.isEmpty()) {
            hiddenKeys.addAll(safe);                // déclenche aussi des change events
        }

        // Ne passe cleaned -> true qu’une seule fois (éviter flip-flop)
        if (!cleaned.get()) {
            cleaned.set(true);
        }

        log.info("ColumnStateModel: replaceHiddenKeys -> {}", hiddenKeys);
    }

    /** Fügt eine einzelne Spalte (originalKey) hinzu. */
    public void addHiddenKey(String key) {
        if (key == null || key.isBlank()) return;
        if (hiddenKeys.add(key)) {
            log.debug("ColumnStateModel: addHiddenKey({})", key);
        }
    }


    public void removeHiddenKey(String key) {
        if (key == null || key.isBlank()) return;
        if (hiddenKeys.remove(key)) {
            log.debug("ColumnStateModel: removeHiddenKey({})", key);
        }
    }

    /** Löscht alle Hidden-Keys (setzt den globalen Bereinigungszustand zurück). */
    public void clear() {
        if (!hiddenKeys.isEmpty()) {
            hiddenKeys.clear();
        }
        cleaned.set(false);
        log.info("ColumnStateModel: clear(); cleaned=false");
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