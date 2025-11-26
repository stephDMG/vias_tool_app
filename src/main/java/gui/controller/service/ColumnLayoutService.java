package gui.controller.service;

import gui.controller.model.ColumnLayoutModel;
import gui.controller.model.ColumnLayoutPersistence;
import gui.controller.model.ColumnStateModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ColumnLayoutService
 *
 * <p>
 * Dieser Service kapselt die typische Geschäftslogik rund um Spalten-Layouts
 * für Tabellen und TreeTables. Ziel ist es, eine zentrale Stelle zu haben,
 * an der Rename-, Delete-, Reorder- und „Bereinigen“-Operationen durchgeführt
 * werden, anstatt dass jede GUI-Komponente diese Logik selbst implementiert.
 * </p>
 *
 * <p>
 * Der Service arbeitet mit einem {@link ColumnLayoutModel}, welches von
 * mehreren Views (z.B. TableView und TreeTableView) gemeinsam verwendet
 * werden kann. Optional kann eine {@link ColumnLayoutPersistence} injiziert
 * werden, um Layouts pro Benutzer und Layout-Id dauerhaft zu speichern.
 * </p>
 */
public class ColumnLayoutService {

    private static final Logger log = LoggerFactory.getLogger(ColumnLayoutService.class);

    /**
     * Optionale Persistenz-Schicht. Wenn {@code null}, werden keine Layouts
     * gespeichert oder geladen.
     */
    private final ColumnLayoutPersistence persistence;

    /**
     * Erstellt einen Service ohne Persistenz. Save-/Load-Methoden haben in
     * diesem Fall keine Wirkung.
     */
    public ColumnLayoutService() {
        this(null);
    }

    /**
     * Erstellt einen Service mit optionaler Persistenz-Implementierung.
     *
     * @param persistence Implementierung zur dauerhaften Speicherung von Layouts,
     *                    oder {@code null}, wenn keine Persistenz verwendet werden soll.
     */
    public ColumnLayoutService(ColumnLayoutPersistence persistence) {
        this.persistence = persistence;
    }

    // -------------------------------------------------------------------------
    // Initialisierung / Defaults
    // -------------------------------------------------------------------------

    /**
     * Initialisiert das gegebene {@link ColumnLayoutModel} mit Standard-Headern.
     * Diese Methode ruft intern {@link ColumnLayoutModel#applyDefaultFromHeaders(List)} auf
     * und kann als zentrale Stelle für zukünftige Erweiterungen dienen.
     *
     * @param model   Layout-Modell (nicht null)
     * @param headers Liste der Header in gewünschter Reihenfolge
     */
    public void initDefaultLayout(ColumnLayoutModel model, List<String> headers) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(headers, "headers");
        model.applyDefaultFromHeaders(headers);
        log.debug("ColumnLayoutService: initDefaultLayout layoutId={} headers={}",
                model.getLayoutId(), headers.size());
    }

    // -------------------------------------------------------------------------
    // Rename / Delete / Visibility
    // -------------------------------------------------------------------------

    /**
     * Benennt eine Spalte im Layout um.
     *
     * @param model          Layout-Modell
     * @param originalKey    technische Spaltenkennung (RowData-Key)
     * @param newDisplayName neuer angezeigter Name
     * @return {@code true}, wenn die Spalte gefunden und geändert wurde
     */
    public boolean renameColumn(ColumnLayoutModel model,
                                String originalKey,
                                String newDisplayName) {
        Objects.requireNonNull(model, "model");
        return model.renameColumn(originalKey, newDisplayName);
    }

    /**
     * Markiert eine Spalte im Layout als unsichtbar (Delete auf GUI-Ebene).
     * Die Daten bleiben vorhanden, werden aber nicht mehr angezeigt.
     *
     * @param model       Layout-Modell
     * @param originalKey technische Spaltenkennung
     * @return {@code true}, wenn eine Spalte verändert wurde
     */
    public boolean deleteColumn(ColumnLayoutModel model, String originalKey) {
        Objects.requireNonNull(model, "model");
        return model.setColumnVisibility(originalKey, false);
    }

    /**
     * Markiert mehrere Spalten als unsichtbar.
     *
     * @param model        Layout-Modell
     * @param originalKeys Menge oder Liste von technischen Spaltenkennungen
     */
    public void deleteColumns(ColumnLayoutModel model, Collection<String> originalKeys) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(originalKeys, "originalKeys");
        model.hideColumns(originalKeys);
    }

    /**
     * Setzt alle Spalten auf sichtbar zurück, ohne Reihenfolge oder Anzeige-Namen
     * zu verändern. Dies kann z.B. für einen „Reset Layout“-Button verwendet werden.
     *
     * @param model Layout-Modell
     */
    public void showAllColumns(ColumnLayoutModel model) {
        Objects.requireNonNull(model, "model");
        model.showAllColumns();
    }

    // -------------------------------------------------------------------------
    // Reordering
    // -------------------------------------------------------------------------

    /**
     * Verschiebt eine Spalte an eine neue Position.
     *
     * @param model       Layout-Modell
     * @param originalKey technische Spaltenkennung
     * @param newIndex    Zielindex (wird intern begrenzt)
     * @return {@code true}, wenn die Spalte gefunden und verschoben wurde
     */
    public boolean moveColumn(ColumnLayoutModel model,
                              String originalKey,
                              int newIndex) {
        Objects.requireNonNull(model, "model");
        return model.moveColumn(originalKey, newIndex);
    }

    /**
     * Setzt die Reihenfolge der Spalten anhand einer Liste von technischen Schlüsseln.
     * Spalten, die im Modell vorhanden, aber nicht in der Liste enthalten sind, behalten
     * ihren bisherigen Index hinter den explizit genannten Spalten.
     *
     * @param model       Layout-Modell
     * @param orderedKeys Liste der Original-Keys in gewünschter Reihenfolge
     */
    public void reorderColumns(ColumnLayoutModel model, List<String> orderedKeys) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(orderedKeys, "orderedKeys");

        // Einfache Implementierung: für jeden Key moveColumn(...) aufrufen,
        // basierend auf seiner Position in orderedKeys.
        int targetIndex = 0;
        for (String key : orderedKeys) {
            if (key == null) {
                continue;
            }
            boolean moved = model.moveColumn(key, targetIndex);
            if (moved) {
                targetIndex++;
            }
        }
        log.debug("ColumnLayoutService: reorderColumns layoutId={} orderedKeys={}",
                model.getLayoutId(), orderedKeys.size());
    }

    // -------------------------------------------------------------------------
    // Bereinigen-Unterstützung
    // -------------------------------------------------------------------------

    /**
     * Wendet ein globales „Bereinigen“ an, indem alle Spalten, die in
     * {@code keysToHide} enthalten sind, im Layout ausgeblendet werden.
     * <p>
     * Typischer Anwendungsfall: {@code AbstractTableManager.cleanColumnsAllPages()}
     * ermittelt anhand aller Seiten diejenigen Spalten, die komplett leer sind,
     * und übergibt diese Liste an diese Methode.
     * </p>
     *
     * @param model      Layout-Modell
     * @param keysToHide technische Spaltenkennungen, die versteckt werden sollen
     */
    public void applyGlobalClean(ColumnLayoutModel model, Collection<String> keysToHide) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(keysToHide, "keysToHide");
        if (keysToHide.isEmpty()) {
            log.debug("ColumnLayoutService: applyGlobalClean layoutId={} -> keine Spalten zu verstecken",
                    model.getLayoutId());
            return;
        }
        model.hideColumns(keysToHide);
        log.debug("ColumnLayoutService: applyGlobalClean layoutId={} hiddenKeys={}",
                model.getLayoutId(), keysToHide.size());
    }

    // -------------------------------------------------------------------------
    // Persistenz (optional)
    // -------------------------------------------------------------------------

    /**
     * Speichert das Layout für einen Benutzer, falls eine {@link ColumnLayoutPersistence}
     * hinterlegt wurde. Wenn keine Persistenz vorhanden ist, passiert nichts.
     *
     * @param username Benutzerkennung (z.B. Windows-Login)
     * @param model    Layout-Modell
     */
    public void saveLayoutForUser(String username, ColumnLayoutModel model) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(model, "model");
        if (persistence == null) {
            log.info("ColumnLayoutService: saveLayoutForUser() ohne Persistence-Konfiguration aufgerufen -> ignoriert");
            return;
        }
        persistence.saveLayout(username, model);
    }

    /**
     * Lädt ein Layout für einen Benutzer. Wenn:
     * <ul>
     *   <li>keine Persistenz konfiguriert ist, oder</li>
     *   <li>kein Layout gefunden wird,</li>
     * </ul>
     * wird ein neues {@link ColumnLayoutModel} mit den gegebenen Default-Headern
     * erzeugt.
     *
     * @param username         Benutzerkennung
     * @param layoutId         logische Layout-Id (z.B. "COVER_MAIN")
     * @param columnStateModel optionaler Legacy-{@link ColumnStateModel}
     * @param defaultHeaders   Default-Header für den Fall, dass kein gespeichertes Layout existiert
     * @return geladene oder neu erzeugte Layout-Instanz (niemals {@code null})
     */
    public ColumnLayoutModel loadOrCreateLayoutForUser(String username,
                                                       String layoutId,
                                                       ColumnStateModel columnStateModel,
                                                       List<String> defaultHeaders) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(layoutId, "layoutId");
        Objects.requireNonNull(defaultHeaders, "defaultHeaders");

        ColumnLayoutModel model;
        if (persistence != null) {
            Optional<ColumnLayoutModel> loaded = persistence.loadLayout(username, layoutId, columnStateModel);
            if (loaded.isPresent()) {
                model = loaded.get();
                log.debug("ColumnLayoutService: loadOrCreateLayoutForUser user={} layoutId={} -> geladen",
                        username, layoutId);
                return model;
            }
        }
        // Fallback: neues Layout aus Defaults erzeugen
        model = new ColumnLayoutModel(layoutId, columnStateModel);
        model.applyDefaultFromHeaders(defaultHeaders);
        log.debug("ColumnLayoutService: loadOrCreateLayoutForUser user={} layoutId={} -> neu erstellt",
                username, layoutId);
        return model;
    }
}
