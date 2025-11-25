package gui.controller.manager;

import model.RowData;

import java.util.List;

/**
 * Einheitliche Schnittstelle für serverseitiges Paging.
 */
@FunctionalInterface
public interface DataLoader {
    List<RowData> loadPage(int pageIndex, int pageSize) throws Exception;
}
