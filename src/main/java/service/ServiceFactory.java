package service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.cache.CachedDatabaseService;
import service.impl.*;
import service.interfaces.AiService;
import service.interfaces.DatabaseService;
import service.interfaces.FileService;
import service.op.OpListeProcessService;


/**
 * Erweiterte Dependency Injection Factory für alle Services.
 * Singletons werden statisch initialisiert mit AI-Mode Support.
 */
/**
 * Zentrale Factory für Service-Singletons (Datenbank, Datei, OP-Liste, AI).
 * Bietet Umschaltung des AI-Modus (LOCAL/HYBRID/AUTO) und Caching für DB.
 */
public final class ServiceFactory {
    private static final Logger logger = LoggerFactory.getLogger(ServiceFactory.class);
    private static final DatabaseService databaseService;
    private static OpListeProcessService opListeServiceInstance;

    private static FileService fileService = new FileServiceImpl();
    static {
        DatabaseService impl = new DatabaseServiceImpl(getFileService());
        databaseService = new CachedDatabaseService(impl);
    }


    //private static AiMode currentAiMode = AiMode.AUTO;
    private static AiMode currentAiMode = AiMode.LOCAL;
    private static AiService aiServiceInstance = null;

    private ServiceFactory() {}

    public static OpListeProcessService getOpListeService() {
        if (opListeServiceInstance == null) {
            opListeServiceInstance = new OpListeProcessService(getDatabaseService(), getFileService());
        }
        return opListeServiceInstance;
    }

    public static FileService getFileService() {
        logger.debug("📂 FileService bereitgestellt");
        return fileService;
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
           // case HUGGING_FACE -> new AiServiceImpl();
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

    public static void setFileService(FileService newFile) { fileService = newFile; }

    public enum AiMode {
        LOCAL("Lokaler Pattern-Service"),
        HYBRID("Hybrid HF+Lokal"),
        AUTO("Automatische Erkennung");

        private final String description;
        AiMode(String description) { this.description = description; }
        public String getDescription() { return description; }
        @Override public String toString() { return description; }
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
