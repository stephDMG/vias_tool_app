package console;

import config.DatabaseConfig;

import java.sql.Connection;

/**
 * Kleines Konsolenprogramm, um die Erreichbarkeit der in DatabaseConfig hinterlegten
 * Datenbank zu prüfen. Gibt einen klaren Statuscode und eine Meldung aus.
 *
 * Aufruf:
 *   mvn exec:java -Dexec.mainClass=console.DbHealthCheck
 *
 * Exit-Codes:
 *   0 = Verbindung erfolgreich
 *   1 = Verbindung fehlgeschlagen
 */
public class DbHealthCheck {
    public static void main(String[] args) {
        System.out.println("[DB CHECK] Prüfe Datenbankverbindung...");
        System.out.println("[DB CHECK] URL= " + DatabaseConfig.URL);
        try {
            // Der Treiber wird in DatabaseConfig.getConnection() via Class.forName geladen,
            // hier nur explizit zur Klarheit.
            Class.forName(DatabaseConfig.DRIVER);
            try (Connection conn = DatabaseConfig.getConnection()) {
                boolean valid = conn != null && !conn.isClosed();
                if (valid) {
                    System.out.println("✅ Verbindung erfolgreich hergestellt.");
                    System.exit(0);
                } else {
                    System.err.println("❌ Verbindung konnte nicht validiert werden (geschlossen oder null).");
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Datenbankverbindung fehlgeschlagen: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
