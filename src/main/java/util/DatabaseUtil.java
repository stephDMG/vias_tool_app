package util;

import config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.impl.FileServiceImpl;

import java.sql.Connection;

/**
 * Hilfsklasse für Datenbankoperationen.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class DatabaseUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    /**
     * Testet Datenbankverbindung.
     */
    public static boolean testConnection() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            logger.error("❌ Datenbankverbindung fehlgeschlagen: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Escaped SQL-String für sichere Queries.
     */
    public static String escapeSql(String input) {
        if (input == null) return null;
        return input.replace("'", "''");
    }

    /**
     * Erstellt WHERE-Klausel für Makler-ID.
     */
    public static String createMaklerWhereClause(String maklerId) {
        return String.format("LU_VMT = '%s'", escapeSql(maklerId));
    }

    /**
     * Gibt Verbindungsinfo zurück.
     */
    public static String getConnectionInfo() {
        return String.format("DB: %s, User: %s",
                DatabaseConfig.URL, DatabaseConfig.USER);
    }
}