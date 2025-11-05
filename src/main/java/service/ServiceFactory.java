package service;

import formatter.contract.CoverFormatter;
import formatter.op.OpListeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.audit.AuditService;
import service.audit.repository.AuditRepository;
import service.audit.repository.AuditRepositoryImpl;
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
import service.op.repository.OpRepository;
import service.rbac.AccessControlService;
import service.rbac.LoginService;

/**
 * Zentrale Factory-Klasse für Service-Singletons.
 * 
 * <p>Diese Klasse stellt alle wichtigen Services der Anwendung als Singletons bereit und 
 * verwaltet deren Lebenszyklus. Sie implementiert das Singleton-Pattern mit Thread-Safety 
 * durch doppelte Überprüfung (Double-Check Locking).</p>
 * 
 * <h2>Bereitgestellte Services:</h2>
 * <ul>
 *   <li>{@link DatabaseService} - Datenbankzugriff mit Caching</li>
 *   <li>{@link FileService} - Dateiverwaltung und -zugriff</li>
 *   <li>{@link OPListenService} - Verwaltung von OP-Listen</li>
 *   <li>{@link AiService} - KI-gestützte Funktionen (lokal/hybrid/automatisch)</li>
 *   <li>{@link CoverService} - Verwaltung von Versicherungsverträgen mit RBAC</li>
 *   <li>{@link AuditService} - Audit-Protokollierung und -Verwaltung</li>
 * </ul>
 * 
 * <h2>AI-Modi:</h2>
 * <p>Die Factory unterstützt verschiedene KI-Modi, die über {@link #setAiMode(AiMode)} 
 * umgeschaltet werden können:</p>
 * <ul>
 *   <li>{@link AiMode#LOCAL} - Nur lokale Pattern-basierte KI</li>
 *   <li>{@link AiMode#HYBRID} - Kombination aus HuggingFace und lokal</li>
 *   <li>{@link AiMode#AUTO} - Automatische Erkennung des besten verfügbaren Services</li>
 * </ul>
 * 
 * <h2>Beispiel:</h2>
 * <pre>{@code
 * // Service abrufen
 * DatabaseService dbService = ServiceFactory.getDatabaseService();
 * CoverService coverService = ServiceFactory.getCoverService();
 * 
 * // AI-Modus ändern
 * ServiceFactory.setAiMode(AiMode.HYBRID);
 * AiService aiService = ServiceFactory.getAiService();
 * }</pre>
 * 
 * @author Stephane Dongmo
 * @version 2.0
 * @since 1.0
 * @see DatabaseService
 * @see FileService
 * @see CoverService
 * @see AiService
 */
public final class ServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(ServiceFactory.class);

    private static final DatabaseService databaseService;
    private static OPListenService opListenServiceInstance;
    private static volatile CoverService COVER_SERVICE;

    private static FileService fileService = new FileServiceImpl();
    private static AiMode currentAiMode = AiMode.LOCAL;
    private static AiService aiServiceInstance = null;

    private static volatile AuditRepository AUDIT_REPOSITORY;
    private static volatile AuditService AUDIT_SERVICE;

    private static volatile OpRepository OP_REPOSITORY;

    static {
        DatabaseService impl = new DatabaseServiceImpl(getFileService());
        databaseService = new CachedDatabaseService(impl);
    }

    /**
     * Privater Konstruktor verhindert Instanziierung.
     * Diese Klasse darf nur statisch verwendet werden.
     */
    private ServiceFactory() {
    }

    /**
     * Liefert den Singleton des {@link OPListenService}.
     * 
     * <p>Dieser Service verwaltet OP-Listen (Offene Posten) und bietet Funktionen 
     * zum Laden, Filtern und Exportieren von OP-Daten.</p>
     * 
     * <p><b>Thread-Safe:</b> Verwendet Double-Check Locking für sichere 
     * Singleton-Initialisierung.</p>
     * 
     * @return die Singleton-Instanz des OPListenService
     * @see OPListenService
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
     * Liefert das Repository für OP-Listen (Offene Posten).
     * 
     * <p>Das {@link OpRepository} bietet Low-Level-Datenzugriff für OP-Listen 
     * und arbeitet direkt mit der Datenbank. Es formatiert die Daten mithilfe 
     * des {@link OpListeFormatter}.</p>
     * 
     * <p><b>Thread-Safe:</b> Singleton mit Double-Check Locking.</p>
     * 
     * @return die Singleton-Instanz des OpRepository
     * @see OpRepository
     * @see OpListeFormatter
     */
    public static OpRepository getOpRepository() {
        if (OP_REPOSITORY == null) {
            synchronized (ServiceFactory.class) {
                if (OP_REPOSITORY == null) {
                    var formatter = new OpListeFormatter();
                    OP_REPOSITORY = new OpRepository(getDatabaseService(), formatter);
                }
            }
        }
        return OP_REPOSITORY;
    }

    /**
     * Liefert den {@link FileService} für Dateiverwaltung.
     * 
     * <p>Dieser Service bietet Funktionen zum Lesen, Schreiben und Verwalten 
     * von Dateien im System. Er wird für Konfigurationsdateien, 
     * Exportdateien und temporäre Daten verwendet.</p>
     * 
     * @return die aktuelle Instanz des FileService
     * @see FileService
     * @see #setFileService(FileService)
     */
    public static FileService getFileService() {
        logger.debug("📂 FileService bereitgestellt");
        return fileService;
    }

    /**
     * Setzt eine neue {@link FileService}-Implementierung.
     * 
     * <p>Diese Methode ermöglicht das Austauschen der FileService-Implementierung, 
     * z.B. für Tests mit Mock-Objekten oder alternative Speicherstrategien.</p>
     * 
     * @param newFile die neue FileService-Implementierung
     * @see FileService
     */
    public static void setFileService(FileService newFile) {
        fileService = newFile;
    }

    /**
     * Liefert den {@link DatabaseService} mit Caching.
     * 
     * <p>Dieser Service stellt Datenbankzugriff mit automatischem Caching bereit. 
     * Die Implementierung verwendet {@link CachedDatabaseService}, der häufig 
     * genutzte Abfragen zwischenspeichert, um die Performance zu verbessern.</p>
     * 
     * @return die Singleton-Instanz des DatabaseService (mit Caching)
     * @see DatabaseService
     * @see CachedDatabaseService
     */
    public static DatabaseService getDatabaseService() {
        logger.debug("📡 DatabaseService bereitgestellt");
        return databaseService;
    }

    /**
     * Liefert den {@link AiService} entsprechend dem aktuellen AI-Modus.
     * 
     * <p>Der zurückgegebene Service hängt vom eingestellten {@link AiMode} ab:</p>
     * <ul>
     *   <li>{@link AiMode#LOCAL} - Lokaler Pattern-basierter Service</li>
     *   <li>{@link AiMode#HYBRID} - Kombination aus HuggingFace und lokal</li>
     *   <li>{@link AiMode#AUTO} - Automatische Erkennung basierend auf Netzwerkverfügbarkeit</li>
     * </ul>
     * 
     * <p>Der Service wird beim ersten Aufruf initialisiert (Lazy Initialization).</p>
     * 
     * @return die AiService-Instanz entsprechend dem aktuellen Modus
     * @see AiService
     * @see AiMode
     * @see #setAiMode(AiMode)
     */
    public static AiService getAiService() {
        if (aiServiceInstance == null) {
            aiServiceInstance = createAiService();
        }
        logger.debug("🤖 AiService bereitgestellt (Mode: {})", currentAiMode);
        return aiServiceInstance;
    }


    // --- NOUVELLES MÉTHODES POUR AUDIT ---

    /**
     * Liefert das Repository für Audit-Zwecke.
     * 
     * <p>Das {@link AuditRepository} bietet Datenzugriff für Audit-Protokolle 
     * und ermöglicht das Speichern und Abrufen von Benutzeraktivitäten, 
     * Systemereignissen und Änderungshistorien.</p>
     * 
     * <p><b>Thread-Safe:</b> Singleton mit Double-Check Locking.</p>
     * 
     * @return die Singleton-Instanz des AuditRepository
     * @see AuditRepository
     * @see AuditRepositoryImpl
     */
    public static AuditRepository getAuditRepository() {
        if (ServiceFactory.AUDIT_REPOSITORY == null) {
            synchronized (ServiceFactory.class) {
                if (ServiceFactory.AUDIT_REPOSITORY == null) {
                    ServiceFactory.AUDIT_REPOSITORY = new AuditRepositoryImpl(ServiceFactory.getDatabaseService());
                }
            }
        }
        return ServiceFactory.AUDIT_REPOSITORY;
    }

    /**
     * Liefert den Singleton des {@link AuditService}.
     * 
     * <p>Dieser Service stellt High-Level-Funktionen für Audit-Protokollierung bereit:</p>
     * <ul>
     *   <li>Protokollierung von Benutzeraktionen</li>
     *   <li>Verfolgung von Datenänderungen</li>
     *   <li>Export von Audit-Berichten</li>
     *   <li>Analyse von Systemereignissen</li>
     * </ul>
     * 
     * <p><b>Thread-Safe:</b> Singleton mit Double-Check Locking.</p>
     * 
     * @return die Singleton-Instanz des AuditService
     * @see AuditService
     * @see AuditRepository
     */
    public static AuditService getAuditService() {
        if (ServiceFactory.AUDIT_SERVICE == null) {
            synchronized (ServiceFactory.class) {
                if (ServiceFactory.AUDIT_SERVICE == null) {
                    // AuditService benötigt den FileService und das AuditRepository
                    ServiceFactory.AUDIT_SERVICE = new AuditService(ServiceFactory.getFileService(), getAuditRepository());
                }
            }
        }
        logger.debug("🛠️ AuditService bereitgestellt");
        return ServiceFactory.AUDIT_SERVICE;
    }



    /**
     * Liefert den {@link CoverService} für Versicherungsvertragsverwaltung.
     * 
     * <p>Dieser Service ist die zentrale Fassade für alle Operationen mit 
     * Versicherungsverträgen (Cover). Er integriert:</p>
     * <ul>
     *   <li><b>RBAC (Role-Based Access Control)</b> - Rollenbasierte Zugriffskontrolle über {@link CoverAccessGuard}</li>
     *   <li><b>Caching</b> - Performance-Optimierung durch {@link CoverCacheService}</li>
     *   <li><b>Datenzugriff</b> - Repository-Pattern über {@link CoverRepository}</li>
     *   <li><b>Detailansichten</b> - Erweiterte Daten über {@link CoverDetailsRepository}</li>
     *   <li><b>Formatierung</b> - Einheitliche Darstellung über {@link CoverFormatter}</li>
     * </ul>
     * 
     * <p>Der Service überprüft automatisch die Berechtigungen des aktuellen Windows-Benutzers 
     * über {@link LoginService} und wendet entsprechende Zugriffsregeln an.</p>
     * 
     * <p><b>Thread-Safe:</b> Singleton mit Double-Check Locking.</p>
     * 
     * @return die Singleton-Instanz des CoverService
     * @see CoverService
     * @see CoverAccessGuard
     * @see CoverCacheService
     * @see #getCoverService()
     */
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

    /**
     * Alias für {@link #getContractService()}.
     * 
     * <p>Liefert denselben {@link CoverService} wie {@code getContractService()}.
     * Dieser Alias wird aus historischen Gründen beibehalten.</p>
     * 
     * @return die Singleton-Instanz des CoverService
     * @see #getContractService()
     */
    public static CoverService getCoverService() {
        return getContractService();
    }


    /**
     * Ändert den AI-Modus für zukünftige {@link AiService}-Instanzen.
     * 
     * <p>Bei Änderung des Modus wird die aktuelle AiService-Instanz verworfen 
     * und beim nächsten Aufruf von {@link #getAiService()} neu erstellt.</p>
     * 
     * <p><b>Verfügbare Modi:</b></p>
     * <ul>
     *   <li>{@link AiMode#LOCAL} - Verwendet nur lokale Pattern-basierte KI</li>
     *   <li>{@link AiMode#HYBRID} - Kombiniert HuggingFace-API mit lokalem Fallback</li>
     *   <li>{@link AiMode#AUTO} - Erkennt automatisch den besten verfügbaren Service</li>
     * </ul>
     * 
     * @param mode der neue AI-Modus
     * @see AiMode
     * @see #getAiService()
     */
    public static void setAiMode(AiMode mode) {
        if (currentAiMode != mode) {
            logger.info("🔄 AI-Mode wechselt von {} zu {}", currentAiMode, mode);
            currentAiMode = mode;
            aiServiceInstance = null; // Reset für Neuerstellung
        }
    }

    /**
     * Erstellt eine {@link AiService}-Instanz basierend auf dem aktuellen Modus.
     * 
     * @return eine neue AiService-Instanz entsprechend {@link #currentAiMode}
     * @see AiMode
     */
    private static AiService createAiService() {
        return switch (currentAiMode) {
            case LOCAL -> new LocalAiServiceImpl();
            case HYBRID -> new HybridAiService();
            case AUTO -> detectBestAiService();
        };
    }

    /**
     * Erkennt automatisch den besten verfügbaren {@link AiService}.
     * 
     * <p>Testet zunächst die Verfügbarkeit von HuggingFace (mit 2 Sekunden Timeout).
     * Bei Erreichbarkeit wird {@link HybridAiService} zurückgegeben, 
     * andernfalls {@link LocalAiServiceImpl}.</p>
     * 
     * <p>Diese Methode wird nur im {@link AiMode#AUTO}-Modus verwendet.</p>
     * 
     * @return HybridAiService wenn HuggingFace erreichbar ist, sonst LocalAiServiceImpl
     */
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
