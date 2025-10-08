package service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import model.contract.CoverRecord;
import model.contract.filters.CoverFilter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CoverCacheService
 * <p>
 * Verwaltet einen lokalen In-Memory-Cache für verschiedene Datentypen,
 * um die Anwendungsleistung zu verbessern und die Datenbanklast zu reduzieren.
 * Nutzt die robuste Caffeine-Bibliothek für effizientes Caching mit TTL-Verwaltung.
 */
public class CoverCacheService {

    // Konfiguration der Cache-TTL (Time-to-Live)
    private static final Duration TTL_DICTS = Duration.ofHours(1);
    private static final Duration TTL_PAGES = Duration.ofMinutes(20);
    private static final Duration TTL_COUNTS = Duration.ofMinutes(3);
    private static final Duration TTL_VSN_SUGGESTIONS = Duration.ofMinutes(5);

    // Cache-Instanzen für verschiedene Datenregionen
    private final Cache<String, Map<String, String>> dictCache;
    private final Cache<String, List<CoverRecord>> pageCache;
    private final Cache<String, Integer> countCache;
    private final Cache<String, List<String>> vsnSuggestCache;

    /**
     * Konstruktor
     * Initialisiert die Cache-Instanzen mit ihren jeweiligen TTLs.
     */
    public CoverCacheService() {
        this.dictCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL_DICTS)
                .build();
        this.pageCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL_PAGES)
                .maximumSize(300)
                .build();
        this.countCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL_COUNTS)
                .maximumSize(500)
                .build();
        this.vsnSuggestCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL_VSN_SUGGESTIONS)
                .maximumSize(1000)
                .build();
    }

    /**
     * Erzeugt einen stabilen Schlüssel für Seiten (Suchergebnisse) basierend auf Filter und Paging.
     *
     * @param filter Filterobjekt.
     * @param page   Seite (beginnend bei 1).
     * @param size   Seitengröße.
     * @return stabiler Cache-Key.
     */
    public String buildSearchKey(CoverFilter filter, int page, int size) {
        String fh = hashFilterNormalized(filter);
        return "search|" + fh + "|p=" + page + "|s=" + size;
    }

    /**
     * Erzeugt einen stabilen Schlüssel für Counts (Totalanzahl) basierend auf Filter.
     *
     * @param filter Filterobjekt.
     * @return stabiler Cache-Key.
     */
    public String buildCountKey(CoverFilter filter) {
        String fh = hashFilterNormalized(filter);
        return "count|" + fh;
    }

    /**
     * Erzeugt einen stabilen Schlüssel für Dictionaries (Tabellenname).
     *
     * @param dictTable Tabellenname, z. B. "MAKLERV".
     * @return stabiler Cache-Key.
     */
    public String buildDictKey(String dictTable) {
        return "dict|" + safeUpper(dictTable);
    }

    /**
     * Holt eine gecachte Seite (Suchergebnisliste) oder null, wenn nicht im Cache.
     *
     * @param key Cache-Key von {@link #buildSearchKey(CoverFilter, int, int)}.
     * @return Liste CoverRecord oder null bei Miss.
     */
    public List<CoverRecord> getPage(String key) {
        return pageCache.getIfPresent(key);
    }

    /**
     * Legt eine Seite (Suchergebnisliste) in den Cache.
     *
     * @param key     Cache-Key.
     * @param records Ergebnisliste.
     */
    public void putPage(String key, List<CoverRecord> records) {
        pageCache.put(key, records);
    }

    /**
     * Holt einen gecachten Count für ein Filterset oder null, wenn nicht im Cache.
     *
     * @param key Cache-Key von {@link #buildCountKey(CoverFilter)}.
     * @return Integer-Count oder null bei Miss.
     */
    public Integer getCount(String key) {
        return countCache.getIfPresent(key);
    }

    /**
     * Legt einen Count für ein Filterset in den Cache.
     *
     * @param key   Cache-Key.
     * @param count Gesamtanzahl.
     */
    public void putCount(String key, Integer count) {
        countCache.put(key, count);
    }

    /**
     * Holt ein Dictionary (z. B. MAKLERV) aus dem Cache.
     *
     * @param dictKey Schlüssel von {@link #buildDictKey(String)}.
     * @return Map oder null bei Miss.
     */
    public Map<String, String> getDictionary(String dictKey) {
        return dictCache.getIfPresent(dictKey);
    }

    /**
     * Legt ein Dictionary in den Cache.
     *
     * @param dictKey Cache-Key.
     * @param map     Key-Value-Paare.
     */
    public void putDictionary(String dictKey, Map<String, String> map) {
        dictCache.put(dictKey, map);
    }

    /**
     * Liefert VSN-Vorschläge aus Cache oder lädt frische via Loader.
     *
     * @param key    z.B. "vsn|w13|20"
     * @param loader Supplier, der bei Cache-Miss die DB abfragt.
     * @return Liste von VSN-Vorschlägen.
     */
    public List<String> getVsnSuggestions(String key, java.util.function.Supplier<List<String>> loader) {
        return vsnSuggestCache.get(key, k -> {
            try {
                return loader.get();
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    /**
     * Leert alle Cache-Regionen.
     */
    public void clearAll() {
        dictCache.invalidateAll();
        pageCache.invalidateAll();
        countCache.invalidateAll();
        vsnSuggestCache.invalidateAll();
    }

    /**
     * Leert nur die Cache-Region für Seiten.
     */
    public void clearPages() {
        pageCache.invalidateAll();
    }

    /**
     * Leert nur die Cache-Region für Zählungen.
     */
    public void clearCounts() {
        countCache.invalidateAll();
    }

    /**
     * Leert nur die Cache-Region für Dictionaries.
     */
    public void clearDictionaries() {
        dictCache.invalidateAll();
    }

    /**
     * Manuelle Invalidierung eines bestimmten Dictionary-Eintrags.
     */
    public void invalidateDictionary(String dictKey) {
        dictCache.invalidate(dictKey);
    }

    // ==========================
    // Interne Hilfsfunktionen
    // ==========================

    /**
     * Erzeugt eine normalisierte Darstellung des Filters, um einen stabilen Hash zu bilden.
     *
     * @param filter Filterobjekt.
     * @return Normalisierte String-Repräsentation des Filters.
     */
    private String hashFilterNormalized(CoverFilter filter) {
        String from = norm(filter.getFromDate());
        String to = norm(filter.getToDate());
        String ab = filter.getAbDate() != null ? filter.getAbDate().toString() : "";
        String bis = filter.getBisDate() != null ? filter.getBisDate().toString() : "";

        String status = norm(filter.getStatus());
        String broker = norm(filter.getBroker());
        String text = norm(filter.getTextSearch());

        String contractStatus = norm(filter.getContractStatus());
        String contractStatusList = filter.getContractStatusList() != null
                ? String.join(",", filter.getContractStatusList())
                : "";

        String bearbeitungsstaende = filter.getBearbeitungsstandIds() != null
                ? String.join(",", filter.getBearbeitungsstandIds())
                : "";

        String mode = norm(filter.getMode());

        String kuendigDat = filter.getKuendigVerkDatum() != null
                ? filter.getKuendigVerkDatum().toString()
                : "";
        String kuendigInit = norm(filter.getKuendigVerkInitiator());

        String stornoGrund = norm(filter.getStornoGrund());

        String groupBy = (filter.getGroupBy() != null && !filter.getGroupBy().isEmpty())
                ? norm(String.join(",", filter.getGroupBy()))
                : "";

        return String.valueOf(Objects.hash(
                from, to, ab, bis,
                status, broker, text,
                contractStatus, contractStatusList,
                bearbeitungsstaende,
                mode, kuendigDat, kuendigInit,
                stornoGrund, groupBy
        ));
    }


    private String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String safeUpper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
