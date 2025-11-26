package gui.controller.model;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.prefs.Preferences;

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

    /**
     * Kanonische Spaltenschlüssel (originalKeys), die global ausgeblendet werden sollen.
     */
    private final ObservableSet<String> hiddenKeys = FXCollections.observableSet();
    private final ObservableMap<String, String> aliases = FXCollections.observableHashMap();
    private final ObservableMap<String, String> sessionAliases = FXCollections.observableHashMap();
    private final ObservableMap<String, String> persistentAliases = FXCollections.observableHashMap();


    /**
     * Abgeleitet: true, sobald {@code hiddenKeys} nicht leer ist.
     */
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

    public void setAlias(String originalKey, String displayName) {
        if (originalKey == null || originalKey.isBlank()) return;
        if (displayName == null || displayName.isBlank()) return;
        aliases.put(originalKey, displayName.trim());
    }

    public ObservableMap<String, String> aliasesProperty() {
        return aliases;
    }

    public javafx.collections.ObservableMap<String, String> sessionAliasesProperty() {
        return sessionAliases;
    }

    public javafx.collections.ObservableMap<String, String> persistentAliasesProperty() {
        return persistentAliases;
    }

    // Résolution d'un titre : d'abord session, puis persistant, sinon clé originale
    public String resolveTitle(String originalKey) {
        if (originalKey == null) return "";
        String s = sessionAliases.get(originalKey);
        if (s != null && !s.isBlank()) return s;
        String p = persistentAliases.get(originalKey);
        if (p != null && !p.isBlank()) return p;
        return originalKey;
    }

    // Définir un alias : choisir persist=true (définitif) ou false (session)
    public void setAlias(String originalKey, String displayName, boolean persist) {
        if (originalKey == null || originalKey.isBlank()) return;
        if (displayName == null || displayName.isBlank()) return;
        if (persist) {
            persistentAliases.put(originalKey, displayName.trim());
        } else {
            sessionAliases.put(originalKey, displayName.trim());
        }
    }

    // Supprimer un alias (coté session ou persistant)
    public void removeAlias(String originalKey, boolean persist) {
        if (originalKey == null || originalKey.isBlank()) return;
        if (persist) {
            persistentAliases.remove(originalKey);
        } else {
            sessionAliases.remove(originalKey);
        }
    }

    // Snapshots utiles pour persister/inspecter
    public java.util.Map<String, String> getSessionAliasesSnapshot() {
        return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(sessionAliases));
    }

    public java.util.Map<String, String> getPersistentAliasesSnapshot() {
        return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(persistentAliases));
    }

    // Reset des alias (sans toucher aux hiddenKeys)
    public void clearSessionAliases() {
        sessionAliases.clear();
    }

    public void clearAllAliases() {
        sessionAliases.clear();
        persistentAliases.clear();
    }

    /**
     * Remet uniquement l’état transitoire (masquages) sans toucher aux alias.
     */
    public void clearTransient() {
        if (!hiddenKeys.isEmpty()) hiddenKeys.clear();
        cleaned.set(true);
    }

    public void clearAll() {
        clear();
        sessionAliases.clear();
        persistentAliases.clear();
    }

    public Map<String, String> getAliasesSnapshot() {
        return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(aliases));
    }

    public void replaceAliases(java.util.Map<String, String> newAliases) {
        aliases.clear();
        if (newAliases != null && !newAliases.isEmpty()) {
            aliases.putAll(newAliases);
        }
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
     *
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

    /**
     * Fügt eine einzelne Spalte (originalKey) hinzu.
     */
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

    /**
     * Löscht alle Hidden-Keys (setzt den globalen Bereinigungszustand zurück).
     */
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

    /**
     * Direkter Getter für den aktuellen Zustand (siehe {@link #cleanedProperty()}).
     */
    public boolean isCleaned() {
        return cleaned.get();
    }

    // --- [ADD persistence helpers – use ONLY java.util.prefs.Preferences] ---
    public void savePersistentTo(Preferences root) {
        if (root == null) return;
        try {
            Preferences node = root.node("column_aliases");
            // vider d'abord le noeud
            for (String k : node.keys()) {
                node.remove(k);
            }
            // enregistrer l'état courant
            for (java.util.Map.Entry<String, String> e : persistentAliases.entrySet()) {
                node.put(e.getKey(), e.getValue());
            }
            node.flush(); // rendre durable
            log.info("ColumnStateModel: persistent aliases saved ({} entries)", persistentAliases.size());
        } catch (Exception ex) {
            log.warn("ColumnStateModel: savePersistentTo failed", ex);
        }
    }

    // Charger les alias persistants depuis le sous-noeud "column_aliases"
    public void loadPersistentFrom(Preferences root) {
        if (root == null) return;
        try {
            Preferences node = root.node("column_aliases");
            persistentAliases.clear();
            for (String k : node.keys()) {
                String v = node.get(k, null);
                if (v != null && !v.isBlank()) {
                    persistentAliases.put(k, v);
                }
            }
            log.info("ColumnStateModel: persistent aliases loaded ({} entries)", persistentAliases.size());
        } catch (Exception ex) {
            log.warn("ColumnStateModel: loadPersistentFrom failed", ex);
        }
    }

}