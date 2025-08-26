package config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    // --- GEÄNDERT: Die Properties werden jetzt durch eine Methode geladen ---
    private static final Properties BUNDLE = loadProperties();

    // Die Felder bleiben gleich, werden aber jetzt aus dem Properties-Objekt initialisiert
    public static final String URL;
    public static final String USER;
    public static final String PASSWORD;
    public static final String DRIVER;

    private DatabaseConfig() {
        // Privater Konstruktor bleibt
    }

    /**
     * NEU: Diese private Hilfsmethode lädt die Konfiguration.
     * Sie versucht zuerst, eine externe Datei zu laden. Wenn das nicht klappt,
     * nimmt sie die interne Datei als Fallback.
     */
    private static Properties loadProperties() {
        Properties properties = new Properties();
        File externalConfigFile = new File("config/db.properties");

        // Versuch 1: Lade externe Konfigurationsdatei
        if (externalConfigFile.exists()) {
            try (InputStream input = new FileInputStream(externalConfigFile)) {
                properties.load(input);
                log.info("✅ Externe Konfigurationsdatei erfolgreich geladen von: {}", externalConfigFile.getAbsolutePath());
                return properties;
            } catch (IOException e) {
                log.warn("Konnte externe Konfigurationsdatei nicht laden, obwohl sie existiert. Fehler: {}", e.getMessage());
            }
        }

        // Versuch 2 (Fallback): Lade interne Konfigurationsdatei aus der JAR
        log.info("Keine externe Konfiguration gefunden. Suche nach interner 'db.properties'...");
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (input == null) {
                // Dies ist ein kritischer Fehler. Die Anwendung kann nicht ohne DB-Config starten.
                throw new RuntimeException("FATAL: Konnte weder eine externe noch eine interne 'db.properties' finden. Anwendung kann nicht starten.");
            }
            properties.load(input);
            log.info("✅ Interne Konfigurationsdatei (Fallback) erfolgreich geladen.");
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("FATAL: Fehler beim Lesen der internen 'db.properties'.", e);
        }
    }

    // Die getConnection Methode bleibt unverändert
    public static Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName(DRIVER);
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // --- GEÄNDERT: Der static-Block initialisiert die Felder jetzt aus dem geladenen BUNDLE ---
    static {
        URL = BUNDLE.getProperty("db.url");
        USER = BUNDLE.getProperty("db.user");
        PASSWORD = BUNDLE.getProperty("db.password");
        DRIVER = BUNDLE.getProperty("db.driver");

        if (URL == null || USER == null || PASSWORD == null || DRIVER == null) {
            throw new RuntimeException("Einige Datenbank-Eigenschaften (url, user, password, driver) fehlen in der Konfigurationsdatei!");
        }
    }
}