package gui.controller.manager;

import model.RowData;
import java.util.List;

/**
 * <p>Funktionale Schnittstelle, die eine Methode zum Laden einer Seite von Daten definiert.</p>
 *
 * <p>Diese Schnittstelle wird vom EnhancedTableManager verwendet, um Daten asynchron aus einer
 * externen Quelle (z. B. einer Datenbank) abzurufen, ohne die Logik der Datenquelle
 * direkt zu kennen.</p>
 */
@FunctionalInterface
public interface DataLoader {
    /**
     * Lädt eine Seite von Daten.
     *
     * @param pageIndex Der Index der zu ladenden Seite (beginnend bei 0).
     * @param rowsPerPage Die Anzahl der Zeilen pro Seite.
     * @return Eine Liste von RowData-Objekten, die die Daten für die angeforderte Seite enthalten.
     */
    List<RowData> loadPage(int pageIndex, int rowsPerPage);
}
