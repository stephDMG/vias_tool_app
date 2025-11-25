package gui.controller.model;

import java.util.Optional;

/**
 * ColumnLayoutPersistence
 *
 * <p>
 * Diese Schnittstelle definiert die notwendigen Operationen, um
 * {@link ColumnLayoutModel}-Instanzen benutzerspezifisch dauerhaft zu
 * speichern und wieder zu laden (z.B. in einer JSON-Datei oder Datenbank).
 * </p>
 *
 * <p>
 * Die konkrete Implementierung ist bewusst offen gelassen, damit sie an die
 * bestehenden Infrastruktur-Komponenten (FileService, Konfig-Verzeichnis,
 * Datenbank, ...) angepasst werden kann.
 * </p>
 */
public interface ColumnLayoutPersistence {

    /**
     * Speichert das gegebene Layout für einen bestimmten Benutzer.
     *
     * @param username Benutzerkennung (z.B. Windows-Login)
     * @param model    Layout-Modell mit Layout-Id und Spaltenkonfiguration
     */
    void saveLayout(String username, ColumnLayoutModel model);

    /**
     * Versucht, ein gespeichertes Layout für einen Benutzer zu laden.
     *
     * @param username         Benutzerkennung
     * @param layoutId         logische Layout-Id (z.B. "COVER_MAIN")
     * @param columnStateModel optionaler Legacy-{@link ColumnStateModel}, der
     *                         für das neu erzeugte Modell verwendet werden soll,
     *                         falls die Persistenzschicht diesen benötigt
     * @return {@link Optional} mit einem geladenen Layout; {@link Optional#empty()},
     * wenn kein Layout gefunden wurde
     */
    Optional<ColumnLayoutModel> loadLayout(String username,
                                           String layoutId,
                                           ColumnStateModel columnStateModel);
}
