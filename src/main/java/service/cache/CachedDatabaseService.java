package service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import javafx.collections.ObservableList;
import model.RowData;
import model.enums.ExportFormat;
import model.enums.QueryRepository;
import service.interfaces.DatabaseService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Decorator-Service, der Caching-Funktionalität zu einem DatabaseService hinzufügt.
 * Verwendet Caffeine für eine performante, thread-sichere Cache-Implementierung.
 */
public class CachedDatabaseService implements DatabaseService {

    private final DatabaseService delegate;
    private final Cache<CacheKey, List<RowData>> cache;

    public CachedDatabaseService(DatabaseService delegate) {
        this(delegate, 64, Duration.ofMinutes(10));
    }

    public CachedDatabaseService(DatabaseService delegate, int maxEntries, Duration ttl) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(ttl)
                .build();
    }

    // --- Methoden mit Caching-Logik ---

    @Override
    public List<RowData> executeQuery(QueryRepository query, List<String> parameters) throws Exception {
        CacheKey key = new CacheKey(query, parameters);
        return cache.get(key, k -> {
            try {
                List<RowData> data = delegate.executeQuery(query, parameters);
                return deepCopy(data);
            } catch (Exception e) {
                throw new RuntimeException("Fehler beim Ausführen der gecachten Abfrage", e);
            }
        });
    }

    @Override
    public List<RowData> executeRawQuery(String sql) throws Exception {
        CacheKey key = new CacheKey(null, List.of(sql)); // Einfacher Schlüssel
        return cache.get(key, k -> {
            try {
                List<RowData> data = delegate.executeRawQuery(sql);
                return deepCopy(data);
            } catch (Exception e) {
                throw new RuntimeException("Fehler beim Ausführen der gecachten rohen Abfrage", e);
            }
        });
    }

    // --- Methoden, die an den Delegaten weitergeleitet werden ---

    @Override
    public void executeQuery(String sql, Consumer<RowData> processor) {
        delegate.executeQuery(sql, processor);
    }

    @Override
    public void exportToFile(String sql, String outputPath, ExportFormat format) {
        delegate.exportToFile(sql, outputPath, format);
    }

    @Override
    public void exportToFile(QueryRepository query, List<String> parameters, String outputPath, ExportFormat format) throws Exception {
        delegate.exportToFile(query, parameters, outputPath, format);
    }


    @Override
    public void exportRawQueryToFile(String sql, String outputPath, ExportFormat format) throws Exception {
        delegate.exportRawQueryToFile(sql, outputPath, format);
    }

    @Override
    public Map<String, Integer> getDashboardStatistics() throws Exception {
        return delegate.getDashboardStatistics();
    }

    @Override
    public Map<String, RowData> getSchadenDetailsByMaklerSnrBulk(List<String> snrMaklerList) throws Exception {
        return delegate.getSchadenDetailsByMaklerSnrBulk(snrMaklerList);
    }

    @Override
    public List<RowData> executeHartrodtQuery() throws Exception {
        // Ein einfacher Cache-Schlüssel ohne Parameter
        CacheKey key = new CacheKey(QueryRepository.OFFENE_SCHAEDEN_TOP_25, List.of("Hartrodt"));

        return cache.get(key, k -> {
            try {
                List<RowData> data = delegate.executeHartrodtQuery();
                return deepCopy(data);
            } catch (Exception e) {
                throw new RuntimeException("Fehler beim Ausführen der gecachten Hartrodt-Abfrage", e);
            }
        });
    }


    @Override
    public List<RowData> executeOpListeQuery(String policyNr) throws Exception {
        // Erstelle einen Cache-Schlüssel mit der Policennummer als Parameter
        CacheKey key = new CacheKey(QueryRepository.SCHADEN_REPORT_BY_MAKLER, List.of(policyNr)); // Dummy-QueryRepository für den Cache-Key

        return cache.get(key, k -> {
            try {
                List<RowData> data = delegate.executeOpListeQuery(policyNr);
                return deepCopy(data);
            } catch (Exception e) {
                throw new RuntimeException("Fehler beim Ausführen der gecachten Op-Liste-Abfrage", e);
            }
        });
    }

    @Override
    public void invalidateCache() {
        cache.invalidateAll();
    }

    // --- Hilfsmethoden für den Cache ---
    private static List<RowData> deepCopy(List<RowData> src) {
        if (src == null) return new ArrayList<>();
        List<RowData> copy = new ArrayList<>(src.size());
        for (RowData r : src) {
            Map<String, String> newMap = new LinkedHashMap<>(r.getValues());
            RowData newRow = new RowData();
            newRow.getValues().putAll(newMap);
            copy.add(newRow);
        }
        return copy;
    }

    private static final class CacheKey {
        private final QueryRepository query;
        private final List<String> params;

        private CacheKey(QueryRepository query, List<String> params) {
            this.query = query;
            this.params = List.copyOf(params);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;
            CacheKey that = (CacheKey) o;
            return query == that.query && Objects.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, params);
        }
    }
}