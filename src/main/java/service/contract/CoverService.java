package service.contract;

import formatter.contract.CoverFormatter;
import gui.cover.CoverDomainController;
import model.GroupingUtil;
import model.RowData;
import model.contract.CoverDetails;
import model.contract.CoverRecord;
import model.contract.CoverStats;
import model.contract.filters.CoverFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.cache.CoverCacheService;
import service.contract.rbac.CoverAccessGuard;
import service.contract.repository.CoverDetailsRepository;
import service.contract.repository.CoverRepository;
import gui.controller.manager.DataLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CoverService
 *
 * <p>Zentrale Fassade für Anwendungsfälle im Bereich „COVER“.</p>
 * <p>Verantwortlichkeiten:</p>
 * <ul>
 * <li>Suche (Paged) mit Cache-Unterstützung</li>
 * <li>Count (für Pagination) mit Cache-Unterstützung</li>
 * <li>Laden von Detaildaten (ein Vertrag per VSN)</li>
 * <li>Bereitstellung eines DataLoader für effiziente Exporte</li>
 * <li>Aggregierte Kennzahlen (KPIs)</li>
 * </ul>
 */
public class CoverService {
    private static final Logger logger = LoggerFactory.getLogger(CoverService.class);

    private final CoverAccessGuard accessGuard;
    private final CoverCacheService coverCache;
    private final CoverRepository coverRepository;
    private final CoverDetailsRepository detailsRepository;
    private final CoverFormatter coverFormatter;

    public CoverService(CoverAccessGuard accessGuard,
                        CoverCacheService coverCache,
                        CoverRepository coverRepository,
                        CoverDetailsRepository detailsRepository,
                        CoverFormatter coverFormatter) {
        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard");
        this.coverCache = Objects.requireNonNull(coverCache, "coverCache");
        this.coverRepository = Objects.requireNonNull(coverRepository, "coverRepository");
        this.detailsRepository = Objects.requireNonNull(detailsRepository, "detailsRepository");
        this.coverFormatter = Objects.requireNonNull(coverFormatter, "coverFormatter");
    }

    // =====================================================================================
    // Suche & Pagination
    // =====================================================================================

    public CoverPageRaw searchRaw(String username, CoverFilter filter, int page, int pageSize) {
        //accessGuard.checkView(username);

        int p = Math.max(0, page);
        int s = Math.max(1, pageSize);

        String countKey = coverCache.buildCountKey(filter);
        Integer total = coverCache.getCount(countKey);
        if (total == null) {
            total = coverRepository.fetchCount(filter);
            coverCache.putCount(countKey, total);
        }

        List<RowData> rows = coverRepository.fetchPageRaw(filter, p, s);

        if (filter.getGroupBy() != null && !filter.getGroupBy().isEmpty()) {
            var grouped = GroupingUtil.groupRows(rows, filter.getGroupBy());
            List<RowData> groupedRows = new ArrayList<>();
            grouped.values().forEach(groupedRows::addAll);
            rows = groupedRows;
        }

        return new CoverPageRaw(rows, total);
    }

    /*
    public Map<String, List<RowData>> searchRawGrouped(String username, CoverFilter filter, int page, int pageSize, List<String> groupByCols) {
        List<RowData> rows = searchRaw(username, filter, page, pageSize).getRows();

        return GroupingUtil.groupRows(rows, groupByCols);
    }
     */


    public CoverPage search(String username, CoverFilter filter, int page, int pageSize) {
        //accessGuard.checkView(username);

        int p = Math.max(0, page);
        int s = Math.max(1, pageSize);

        String pageKey = coverCache.buildSearchKey(filter, p, s);
        String countKey = coverCache.buildCountKey(filter);

        List<CoverRecord> rows = coverCache.getPage(pageKey);
        Integer total = coverCache.getCount(countKey);

        if (rows == null) {
            rows = coverRepository.fetchPage(filter, p, s);
            coverCache.putPage(pageKey, rows);
        }
        if (total == null) {
            total = coverRepository.fetchCount(filter);
            coverCache.putCount(countKey, total);
        }

        return new CoverPage(rows, total);
    }

    public int count(String username, CoverFilter filter) {
        //accessGuard.checkView(username);
        String countKey = coverCache.buildCountKey(filter);
        Integer total = coverCache.getCount(countKey);
        if (total != null) return total;
        total = coverRepository.fetchCount(filter);
        coverCache.putCount(countKey, total);
        return total;
    }

    // =====================================================================================
    // KPIs
    // =====================================================================================

    /**
     * Liefert aggregierte Kennzahlen (KPIs) für das aktuelle Filterset.
     *
     * @param username aktueller Benutzer
     * @param filter   Filterkriterien
     * @return CoverStats (KPIs)
     */
    public CoverStats getStats(String username, CoverFilter filter) {
        //accessGuard.checkView(username);
        return coverRepository.fetchStats(filter);
    }

    // =====================================================================================
    // Suggest / Dictionaries / Details
    // =====================================================================================

    public List<String> suggestVsn(String username, String query, int limit) {
        //accessGuard.checkView(username);
        String q = (query == null) ? "" : query.trim();
        if (q.length() < 2 || limit <= 0) return List.of();
        return coverRepository.suggestVsnOptimized(q, limit);
    }

    public CoverDetails getDetailsByVsn(String username, String vsn) {
        //accessGuard.checkView(username);
        return detailsRepository.fetchDetailsByVsn(vsn);
    }

    public DataLoader getRawDataLoader(String username, CoverFilter filter) {
        return (pageIndex, rowsPerPage) -> searchRaw(username, filter, pageIndex, rowsPerPage).getRows();
    }

    public Map<String, String> getDictionary(String username, String table) {
        //accessGuard.checkView(username);
        String key = coverCache.buildDictKey(table);
        Map<String, String> dict = coverCache.getDictionary(key);
        if (dict == null) {
            dict = coverRepository.fetchDictionary(table);
            coverCache.putDictionary(key, dict);
        }
        return dict;
    }

    public void preloadDicts(String username) {
        //accessGuard.checkView(username);
        String[] tables = new String[]{
                "MAKLERV",
                "MAP_ALLE_OPZ",
                "MAP_ALLE_STA",
                "MAP_ALLE_BETSTAT",
                "MAP_ALLE_GBEREICH",
                "MAP_ALLE_COVERRIS"
        };
        for (String t : tables) {
            String key = coverCache.buildDictKey(t);
            if (coverCache.getDictionary(key) == null) {
                var dict = coverRepository.fetchDictionary(t);
                coverCache.putDictionary(key, dict);
            }
        }
    }

    // =====================================================================================
    // DTOs für Paged-Ergebnisse
    // =====================================================================================

    public static final class CoverPage {
        private final List<CoverRecord> rows;
        private final int total;

        public CoverPage(List<CoverRecord> rows, int total) {
            this.rows = rows;
            this.total = total;
        }

        public List<CoverRecord> getRows() {
            return rows;
        }

        public int getTotal() {
            return total;
        }
    }

    public static final class CoverPageRaw {
        private final List<RowData> rows;
        private final int total;

        public CoverPageRaw(List<RowData> rows, int total) {
            this.rows = rows;
            this.total = total;
        }

        public List<RowData> getRows() {
            return rows;
        }

        public int getTotal() {
            return total;
        }
    }
}
