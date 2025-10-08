package service.op;

import formatter.op.OpListeFormatter;
import model.RowData;
import service.interfaces.DatabaseService;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OpRepository {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpRepository.class);

    private final DatabaseService db;
    private final OpListeFormatter formatter;

    // Cache
    private List<RowData> mainCache = Collections.emptyList();
    private Instant cacheLoadedAt = null;

    private Duration cacheTtl = Duration.ofHours(24);

    public OpRepository(DatabaseService db, OpListeFormatter formatter) {
        this.db = Objects.requireNonNull(db, "DatabaseService darf nicht null sein");
        this.formatter = Objects.requireNonNull(formatter, "OpListeFormatter darf nicht null sein");
        log.info("OpRepository created id={}", System.identityHashCode(this));
    }

    public void setCacheTtl(Duration ttl) {
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            this.cacheTtl = ttl;
        }
    }

    public boolean isCacheEmpty() {
        boolean empty = (mainCache == null || mainCache.isEmpty());
        boolean stale = isCacheStale();
        log.info("isCacheEmpty? {} (stale={}, repoId={}, size={})",
                (empty || stale),
                stale,
                System.identityHashCode(this),
                (mainCache == null ? 0 : mainCache.size()));
        return empty || stale;
    }

    private boolean isCacheStale() {
        if (cacheLoadedAt == null) return true;
        return Instant.now().isAfter(cacheLoadedAt.plus(cacheTtl));
    }

    public List<RowData> getMainCache() {
        return (mainCache == null) ? Collections.emptyList() : mainCache;
    }


    public void invalidateCache() {
        mainCache = Collections.emptyList();
        cacheLoadedAt = null;
        log.info("OP cache invalidated (repoId={})", System.identityHashCode(this));
    }


    public List<RowData> loadAndCacheMainList() throws Exception {
        final String sql = """
                SELECT A.LU_VMT, A.LU_RNR, A.LU_RNR_Makler, A.LU_RNR_R, A.LU_VSN, LA.LU_VSN_Makler, A.LU_ZJ,
                       LMP.LU_NAM, A.LU_RDT, A.LU_BDT, A.LU_FLG, A.LU_Waehrung, A.LU_VSTLD, A.LU_SD_WART,
                       MAX(A.LU_NET_100) AS LU_NET_100, AVG(A.LU_VST) AS LU_VST, SUM(A.LU_VSTBetrag) AS LU_VSTBetrag,
                       SUM(A.LU_Praemie) AS LU_Praemie, SUM(A.LU_OBT) AS LU_OBT, MAX(A.LU_SPAKZ) AS LU_SPAKZ,
                       SUM(A.LU_NET) AS LU_NET, SUM(A.LU_WProvision) AS LU_WProvision, SUM(A.LU_Restbetrag) AS LU_Restbetrag,
                       A.LU_INK, MAX(A.LU_MA1) AS LU_MA1, MAX(A.LU_MA2) AS LU_MA2, MAX(A.LU_MAHN_Bemerkung) AS LU_MAHN_Bemerkung,
                       MAX(LA.LU_STATISTIK_CODE1) AS STAT_CODE1, MAX(LA.LU_STATISTIK_CODE2) AS STAT_CODE2,
                       MAX(LA.LU_STATISTIK_CODE3) AS STAT_CODE3, MAX(LA.LU_STATISTIK_CODE4) AS STAT_CODE4,
                       MAX(LA.LU_STATISTIK_CODE5) AS STAT_CODE5, MAX(LA.LU_STATISTIK_CODE6) AS STAT_CODE6, A.LU_ABW
                FROM ABRECHNUNG AS A
                INNER JOIN LU_ALLE AS LA ON A.VPointer = LA.VPointer
                INNER JOIN LU_MASKEP AS LMP ON A.PPointer = LMP.PPointer
                WHERE A.LU_TES IN ('SO','SOT','GR') AND A.LU_Restbetrag <> 0
                GROUP BY A.APointer, A.LU_VMT, A.LU_RNR, A.LU_RNR_Makler, A.LU_RNR_R,
                         A.LU_VSN, LA.LU_VSN_Makler, A.LU_ZJ, LMP.LU_NAM, A.LU_RDT, A.LU_BDT,
                         A.LU_FLG, A.LU_Waehrung, A.LU_VSTLD, A.LU_SD_WART, A.LU_INK, A.LU_ABW
                ORDER BY A.LU_VMT, A.LU_VSN, A.LU_ZJ
                """;

        log.info("Loading OP main list from DB… (repoId={})", System.identityHashCode(this));
        List<RowData> rawData = db.executeRawQuery(sql);
        if (rawData == null) rawData = Collections.emptyList();

        mainCache = formatter.format(rawData);
        cacheLoadedAt = Instant.now();

        log.info("OP cache filled: size={}, repoId={}, at={}", mainCache.size(),
                System.identityHashCode(this), cacheLoadedAt);
        return mainCache;
    }


    public List<RowData> getOrLoadMainList() throws Exception {
        if (isCacheEmpty()) {
            return loadAndCacheMainList();
        }
        log.info("Serving OP main list from cache: size={}, repoId={}", mainCache.size(),
                System.identityHashCode(this));
        return mainCache;
    }


    public List<RowData> findByPolicyFromCache(String vsn) {
        if (mainCache == null || mainCache.isEmpty()) return List.of();
        String key = (vsn == null) ? "" : vsn.trim();
        return mainCache.stream()
                .filter(r -> key.equals(r.getValues().get("Policen-Nr")))
                .collect(Collectors.toList());
    }
}
