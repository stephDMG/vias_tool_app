package config.op;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class OpListDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(OpListDatabaseConfig.class);
    private static final String DB_PATH;
    private static final String URL;

    static {
        Properties props = loadProperties();
        DB_PATH = props.getProperty("oplist.db.path");
        if (DB_PATH == null || DB_PATH.isBlank()) {
            throw new RuntimeException("oplist.db.path fehlt in oplist_db.properties!");
        }
        URL = "jdbc:sqlite:" + DB_PATH;
        log.info("OpList-DB Pfad: {}", DB_PATH);
    }

    private OpListDatabaseConfig() {
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        File external = new File("config/oplist_db.properties");

        if (external.exists()) {
            try (InputStream in = new FileInputStream(external)) {
                props.load(in);
                log.info("Externe oplist_db.properties geladen: {}", external.getAbsolutePath());
                return props;
            } catch (IOException e) {
                log.warn("Fehler beim Laden der externen Config: {}", e.getMessage());
            }
        }

        try (InputStream in = OpListDatabaseConfig.class.getClassLoader().getResourceAsStream("oplist_db.properties")) {
            if (in == null) {
                throw new RuntimeException("oplist_db.properties nicht gefunden!");
            }
            props.load(in);
            log.info("Interne oplist_db.properties geladen (Fallback)");
            return props;
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Laden von oplist_db.properties", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        File dbFile = new File(DB_PATH);
        boolean isNew = !dbFile.exists();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS email_mappings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kunde_name TEXT NOT NULL,
                    police_nr TEXT NOT NULL,
                    versicherungsnehmer TEXT,
                    to_email TEXT,
                    cc_email TEXT,
                    language TEXT DEFAULT 'DE',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(kunde_name, police_nr)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS email_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    police_nr TEXT NOT NULL,
                    kunde_name TEXT NOT NULL,
                    recipient TEXT NOT NULL,
                    cc_recipient TEXT,
                    subject TEXT,
                    attachment_type TEXT,
                    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    sent_by TEXT,
                    status TEXT DEFAULT 'SENT'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS email_backup (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kunde_name TEXT NOT NULL,
                    police_nr TEXT NOT NULL,
                    versicherungsnehmer TEXT,
                    to_email TEXT,
                    cc_email TEXT,
                    language TEXT,
                    archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    reason TEXT
                )
            """);

            if (isNew) {
                log.info("SQLite-Datenbank neu erstellt: {}", DB_PATH);
            } else {
                log.info("SQLite-Datenbank verbunden: {}", DB_PATH);
            }

        } catch (SQLException e) {
            log.error("Fehler bei DB-Initialisierung: {}", e.getMessage());
            throw new RuntimeException("OpList-DB Initialisierung fehlgeschlagen", e);
        }
    }
}