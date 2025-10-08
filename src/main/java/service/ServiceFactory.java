package service;

import formatter.contract.CoverFormatter;
import formatter.op.OpListeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.cache.CachedDatabaseService;
import service.cache.CoverCacheService;
import service.contract.CoverService;
import service.contract.rbac.CoverAccessGuard;
import service.contract.repository.CoverDetailsRepository;
import service.contract.repository.CoverRepository;
import service.impl.DatabaseServiceImpl;
import service.impl.FileServiceImpl;
import service.impl.LocalAiServiceImpl;
import service.interfaces.AiService;
import service.interfaces.DatabaseService;
import service.interfaces.FileService;
import service.op.OPListenService;
import service.op.OpRepository;
import service.rbac.AccessControlService;
import service.rbac.LoginService;

/**
 * Zentrale Factory für Service-Singletons (Datenbank, Datei, OP-Liste, AI).
 * Bietet Umschaltung des AI-Modus (LOCAL/HYBRID/AUTO) und Caching für DB.
 */
public final class ServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(ServiceFactory.class);

    private static final DatabaseService databaseService;
    private static OPListenService opListenServiceInstance;
    private static volatile CoverService COVER_SERVICE;

    private static FileService fileService = new FileServiceImpl();
    private static AiMode currentAiMode = AiMode.LOCAL;
    private static AiService aiServiceInstance = null;

    private static volatile OpRepository OP_REPOSITORY;

    static {
        DatabaseService impl = new DatabaseServiceImpl(getFileService());
        databaseService = new CachedDatabaseService(impl);
    }

    private ServiceFactory() {
    }

    /**
     * Liefert den Singleton des neuen {@link OPListenService}.
     */
    public static OPListenService getOpListeService() {
        if (opListenServiceInstance == null) {
            synchronized (ServiceFactory.class) {
                if (opListenServiceInstance == null) {
                    opListenServiceInstance = new OPListenService(getDatabaseService(), getFileService());
                }
            }
        }
        return opListenServiceInstance;
    }

    /**
     * Repository für OP-Listen (Singleton).
     */
    public static OpRepository getOpRepository() {
        if (OP_REPOSITORY == null) {
            synchronized (ServiceFactory.class) {
                if (OP_REPOSITORY == null) {
                    var formatter = new OpListeFormatter();
                    OP_REPOSITORY = new service.op.OpRepository(getDatabaseService(), formatter);
                }
            }
        }
        return OP_REPOSITORY;
    }

    public static FileService getFileService() {
        logger.debug("📂 FileService bereitgestellt");
        return fileService;
    }

    public static void setFileService(FileService newFile) {
        fileService = newFile;
    }

    public static DatabaseService getDatabaseService() {
        logger.debug("📡 DatabaseService bereitgestellt");
        return databaseService;
    }

    public static AiService getAiService() {
        if (aiServiceInstance == null) {
            aiServiceInstance = createAiService();
        }
        logger.debug("🤖 AiService bereitgestellt (Mode: {})", currentAiMode);
        return aiServiceInstance;
    }

    public static CoverService getContractService() {
        if (COVER_SERVICE == null) {
            synchronized (ServiceFactory.class) {
                if (COVER_SERVICE == null) {
                    // RBAC / Guard
                    AccessControlService access = new AccessControlService();
                    CoverAccessGuard guard = new CoverAccessGuard(
                            access,
                            () -> new LoginService().getCurrentWindowsUsername()
                    );

                    // Infrastruktur
                    var db = getDatabaseService();
                    var formatter = new CoverFormatter();
                    var repo = new CoverRepository(db, formatter);
                    var details = new CoverDetailsRepository(db);
                    var coverCache = new CoverCacheService();

                    // Fassade
                    COVER_SERVICE = new CoverService(guard, coverCache, repo, details, formatter);
                }
            }
        }
        return COVER_SERVICE;
    }

    public static CoverService getCoverService() {
        return getContractService();
    }


    public static void setAiMode(AiMode mode) {
        if (currentAiMode != mode) {
            logger.info("🔄 AI-Mode wechselt von {} zu {}", currentAiMode, mode);
            currentAiMode = mode;
            aiServiceInstance = null; // Reset für Neuerstellung
        }
    }

    private static AiService createAiService() {
        return switch (currentAiMode) {
            case LOCAL -> new LocalAiServiceImpl();
            case HYBRID -> new HybridAiService();
            case AUTO -> detectBestAiService();
        };
    }

    private static AiService detectBestAiService() {
        try {
            logger.debug("🔍 Teste Hugging Face Verfügbarkeit...");
            java.net.URL url = new java.net.URL("https://huggingface.co");
            java.net.URLConnection connection = url.openConnection();
            connection.setConnectTimeout(2000);
            connection.connect();
            logger.info("✅ Hugging Face erreichbar - verwende Hybrid Service");
            return new HybridAiService();
        } catch (Exception e) {
            logger.warn("⚠️ Hugging Face nicht erreichbar ({}) - verwende lokalen Service", e.getMessage());
            return new LocalAiServiceImpl();
        }
    }

    public enum AiMode {
        LOCAL("Lokaler Pattern-Service"),
        HYBRID("Hybrid HF+Lokal"),
        AUTO("Automatische Erkennung");

        private final String description;

        AiMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}

/**
 * Hybrid AI Service - Kombiniert HuggingFace und lokalen Service
 */
class HybridAiService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(HybridAiService.class);
    private final AiService localService = new LocalAiServiceImpl();

    @Override
    public String generateQuery(String description) {
        return localService.generateQuery(description);
    }

    @Override
    public String optimizeQuery(String originalQuery) {
        return localService.optimizeQuery(originalQuery);
    }
}
