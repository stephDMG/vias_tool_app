package gui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.contract.CoverService;
import service.rbac.LoginService;
import service.theme.ThemeService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringJoiner;


/**
 * Haupt-Controller des Hauptfensters (MainWindow.fxml).
 * <p>
 * Initialisiert alle Views + lädt CoverService & Dictionaries vor,
 * damit Kernfragen im CoverDashboard schnell reagieren.
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private static final String BASE =
            "\\\\Debresrv10\\csdatenwelt$\\EDV\\Client_WIN_11\\Software\\VIAS_TOOL_PUBLIC\\jpackage_output\\";
    private static final String VERSION_FILE_PATH = BASE + "version.txt";
    private static final String CHANGELOG_FILE_PATH = BASE + "changelog.txt";

    /**
     * Aktuelle App-Version
     */
    private String CURRENT_VERSION = "N/A";

    // Views
    private Parent opListView;
    private Parent extractionView, dataView, dbExportView, aiAssistantView, pivotView,
            dashboardView, showEnrichmentView, settingsView, coverDashboardView, auditView;

    // CoverService global
    private CoverService coverService;

    @FXML
    private BorderPane mainBorderPane;
    @FXML
    private MenuItem statusItem;
    @FXML
    private TextFlow userGreeting;
    @FXML
    private MenuItem coverDashboardItem;

    /**
     * Formatiert einen String, sodass der erste Buchstabe jedes Wortes großgeschrieben wird.
     *
     * @param input Eingabetext (kann null oder leer sein)
     * @return formatierter String mit großgeschriebenen Wortanfängen
     */
    public static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split("\\s+");
        StringJoiner result = new StringJoiner(" ");
        for (String w : words) result.add(w.substring(0, 1).toUpperCase() + w.substring(1));
        return result.toString();
    }

    /**
     * Extrahiert eine semantische Versionsnummer (MAJOR.MINOR.PATCH) aus einem String.
     * Entfernt BOM/Whitespace und gibt nur die Version zurück oder null, wenn keine gefunden wurde.
     *
     * @param s Roh-String (z. B. aus Datei gelesen)
     * @return bereinigte Version oder null
     */
    private static String cleanVersion(String s) {
        if (s == null) return null;
        s = s.replace("\uFEFF", "").trim();
        var m = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(s);
        return m.find() ? m.group(0) : null;
    }

    /**
     * Initialisiert das Hauptfenster: lädt Views vor, richtet RBAC/Theme ein,
     * lädt die App-Version und bereitet den CoverService vor.
     *
     * @param location  URL der FXML-Ressource
     * @param resources Ressourcenbündel der FXML
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("🎯 MainController wird initialisiert...");
        loadAppVersion();

        // RBAC für Menüs
        LoginService loginService = new LoginService();
        String username = loginService.getCurrentWindowsUsername();
        //AccessControlService accessControl = new AccessControlService();
        //statusItem.setVisible(accessControl.hasPermission(username, "op-list") || accessControl.hasPermission(username, "all"));
        // coverDashboardItem.setVisible(accessControl.hasPermission(username, "view") || accessControl.hasPermission(username, "all"));

        // Begrüßung
        String user = username.replace('.', ' ');
        Text part1 = new Text("Moin ");
        Text handIcon = new Text("👋");
        handIcon.setStyle("-fx-fill: orange;");
        Text part2 = new Text(" " + capitalizeFirstLetter(user) + "!");
        userGreeting.getChildren().addAll(part1, handIcon, part2);

        try {
            extractionView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/ExtractionView.fxml")));
            dataView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/DataView.fxml")));
            dbExportView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/DbExportView.fxml")));
            aiAssistantView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/AiAssistantView.fxml")));
            pivotView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/PivotView.fxml")));
            dashboardView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/DashboardView.fxml")));
            showEnrichmentView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/EnrichmentView.fxml")));
            opListView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/OpListView.fxml")));
            settingsView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/SettingsView.fxml")));
            coverDashboardView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/CoverDashboardView.fxml")));
            auditView = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/AuditView.fxml")));
        } catch (IOException e) {
            logger.error("❌ Fehler beim Vorladen der Ansichten", e);
        }

        // Theme laden
        ThemeService themeService = new ThemeService();
        themeService.applyTheme(themeService.loadTheme());

        // CoverService vorbereiten
        try {
            coverService = ServiceFactory.getContractService();
            String currentUser = loginService.getCurrentWindowsUsername();
            coverService.preloadDicts(currentUser);
            logger.info("✅ CoverService + Dictionaries erfolgreich vorgeladen.");
        } catch (Throwable t) {
            logger.warn("⚠️ CoverService konnte nicht geladen werden: {}", t.getMessage());
        }

        showDashboardView();
        checkForUpdates();
    }

    /**
     * Lädt /version.properties und speichert CURRENT_VERSION
     */
    private void loadAppVersion() {
        try (InputStream input = getClass().getResourceAsStream("/version.properties")) {
            if (input == null) {
                logger.error("version.properties nicht in der JAR gefunden!");
                return;
            }
            Properties props = new Properties();
            props.load(input);
            this.CURRENT_VERSION = cleanVersion(props.getProperty("app.version", "N/A"));
            logger.info("Anwendungsversion erfolgreich geladen: {}", CURRENT_VERSION);
        } catch (IOException ex) {
            logger.error("Fehler beim Lesen der version.properties", ex);
        }
    }

    // ------------------- View Switcher -------------------

    /**
     * Zeigt die Ansicht zur Datenextraktion.
     */
    @FXML
    private void showExtractionView() {
        mainBorderPane.setCenter(extractionView);
    }

    /**
     * Zeigt die Datenansicht.
     */
    @FXML
    private void showDataView() {
        mainBorderPane.setCenter(dataView);
    }

    /**
     * Zeigt die Datenbank-Export-Ansicht.
     */
    @FXML
    private void showDbExportView() {
        mainBorderPane.setCenter(dbExportView);
    }

    /**
     * Zeigt die KI-Assistent-Ansicht.
     */
    @FXML
    private void showAiAssistantView() {
        mainBorderPane.setCenter(aiAssistantView);
    }

    /**
     * Zeigt die Pivot-Ansicht.
     */
    @FXML
    private void showPivotView() {
        mainBorderPane.setCenter(pivotView);
    }

    /**
     * Zeigt das Dashboard.
     */
    @FXML
    private void showDashboardView() {
        mainBorderPane.setCenter(dashboardView);
    }

    /**
     * Zeigt die Anreicherungs-Ansicht.
     */
    @FXML
    private void showEnrichmentView() {
        mainBorderPane.setCenter(showEnrichmentView);
    }

    /**
     * Zeigt die OP-Liste.
     */
    @FXML
    private void showOpListView() {
        mainBorderPane.setCenter(opListView);
    }

    /**
     * Zeigt die Einstellungen.
     */
    @FXML
    private void showSettingsView() {
        mainBorderPane.setCenter(settingsView);
    }

    /**
     * Zeigt das Cover-Dashboard.
     */
    @FXML
    private void showCoverDashboardView() {
        mainBorderPane.setCenter(coverDashboardView);
    }


    @FXML
    private void showAuditView() {
        mainBorderPane.setCenter(auditView);
    }

    /**
     * Beendet die Anwendung.
     */
    @FXML
    private void closeApplication() {
        Platform.exit();
    }

    // ------------------- Update Check -------------------

    /**
     * Prüft asynchron, ob eine neuere Version verfügbar ist, und zeigt ggf. einen Dialog an.
     */
    private void checkForUpdates() {
        Task<UpdateInfo> versionCheckTask = new Task<>() {
            @Override
            protected UpdateInfo call() {
                try {
                    logger.info("Prüfe auf neue Version unter: {}", VERSION_FILE_PATH);
                    Path versionPath = Paths.get(VERSION_FILE_PATH);
                    if (!Files.exists(versionPath)) {
                        logger.warn("Version-Datei existiert nicht: {}", VERSION_FILE_PATH);
                        return null;
                    }
                    String onlineVersionRaw = Files.readString(versionPath, StandardCharsets.UTF_8);
                    String onlineVersion = cleanVersion(onlineVersionRaw);

                    String changelogContent = "Keine Änderungsbeschreibung verfügbar.";
                    Path changelogPath = Paths.get(CHANGELOG_FILE_PATH);
                    if (Files.exists(changelogPath)) {
                        try {
                            changelogContent = Files.readString(changelogPath, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            logger.warn("Fehler beim Lesen der changelog.txt: {}", e.getMessage());
                        }
                    }
                    return new UpdateInfo(onlineVersion, changelogContent);
                } catch (IOException e) {
                    logger.error("Fehler beim Lesen der Update-Dateien: {}", e.getMessage());
                    return null;
                }
            }

            @Override
            protected void succeeded() {
                UpdateInfo info = getValue();
                if (info != null && isNewer(info.version, CURRENT_VERSION)) {
                    logger.info("Neue Version gefunden! Online: {}, Aktuell: {}", info.version, CURRENT_VERSION);
                    Platform.runLater(() -> showUpdateDialog(info));
                } else {
                    logger.info("Keine neue Version verfügbar. Online: {}, Aktuell: {}",
                            info != null ? info.version : "N/A", CURRENT_VERSION);
                }
            }
        };

        Thread updateThread = new Thread(versionCheckTask, "update-check-thread");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    /**
     * Vergleicht zwei Versionsstrings im Format MAJOR.MINOR.PATCH.
     *
     * @param onlineVersion  Version aus externer Quelle
     * @param currentVersion lokale aktuelle App-Version
     * @return true, falls onlineVersion > currentVersion
     */
    private boolean isNewer(String onlineVersion, String currentVersion) {
        String o = cleanVersion(onlineVersion);
        String c = cleanVersion(currentVersion);
        if (o == null || c == null) return false;

        String[] os = o.split("\\.");
        String[] cs = c.split("\\.");
        for (int i = 0; i < Math.max(os.length, cs.length); i++) {
            int oi = i < os.length ? Integer.parseInt(os[i]) : 0;
            int ci = i < cs.length ? Integer.parseInt(cs[i]) : 0;
            if (oi != ci) return oi > ci;
        }
        return false;
    }


    /**
     * Zeigt einen Hinweisdialog für ein verfügbares Update an.
     * Führt die Update-Logik aus, wenn der Benutzer zustimmt.
     *
     * @param info Informationen zur verfügbaren Version inkl. Changelog
     */
    private void showUpdateDialog(UpdateInfo info) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update verfügbar");
        alert.setHeaderText("Neue Version: " + info.version);
        alert.setContentText("Möchten Sie jetzt aktualisieren?");

        // Définir les types de boutons pour la gestion des événements
        alert.getButtonTypes().setAll(
                new ButtonType("Jetzt aktualisieren", ButtonBar.ButtonData.YES), // Type YES
                new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE)     // Type NO
        );

        alert.showAndWait().ifPresent(response -> {
            // Gérer la réponse "Jetzt aktualisieren"
            if (response.getButtonData() == ButtonBar.ButtonData.YES) {
                logger.info("Benutzer hat Update zugestimmt. Starte Download/Installation.");

                // Le nom du fichier MSI à télécharger depuis le chemin UNC
                String installerFileName = String.format("VIAS Export Tool-%s.msi", info.version);
                String remoteInstallerPath = Paths.get(BASE, installerFileName).toString(); // BASE est le chemin UNC

                try {
                    // Lancer le processus de mise à jour externe
                    startUpdateProcess(remoteInstallerPath);
                } catch (Exception e) {
                    logger.error("❌ Kritischer Fehler beim Starten des Update-Prozesses.", e);
                    // Afficher une alerte en cas d'échec critique
                    Platform.runLater(() -> {
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("Update-Fehler");
                        errorAlert.setHeaderText("Konnte Installer nicht starten.");
                        errorAlert.setContentText("Prüfen Sie, ob der UNC-Pfad erreichbar ist: " + remoteInstallerPath);
                        errorAlert.showAndWait();
                    });
                }
            }
        });
    }

    /**
     * Führt den Update-Prozess robust aus, ohne Cmdline-Parsing-Probleme:
     * - Kopiert das MSI vom UNC in %LOCALAPPDATA%\VIAS\ updates\<version>\
     * - Erzeugt eine updater.ps1 (saubere Argument-Quoting)
     * - Startet PowerShell mit -File (kein -Command-Quoting-Chaos)
     * - msiexec läuft mit /passive (sichtbar) + /norestart + /l*v (Logging)
     * - Danach Relaunch der App; aktuelle App beendet sich sofort
     *
     * @param remoteInstallerPath UNC-Pfad zum MSI (z. B. \\server\share\VIAS Export Tool-1.2.15.msi)
     * @throws IOException bei Kopierfehlern
     */
    private void startUpdateProcess(String remoteInstallerPath) throws IOException {
        // --- 1) Dateinamen & Version ermitteln ---------------------------------
        Path remote = Paths.get(remoteInstallerPath);
        String msiName = remote.getFileName().toString();

        // Version aus dem Dateinamen ziehen (Fallback "current")
        String version = "current";
        var m = java.util.regex.Pattern.compile("(\\d+\\.\\d+\\.\\d+)").matcher(msiName);
        if (m.find()) version = m.group(1);

        // --- 2) Staging-Ziel unter LOCALAPPDATA vorbereiten ---------------------
        Path localBase = Paths.get(System.getenv("LOCALAPPDATA"), "VIAS", "updates", version);
        Files.createDirectories(localBase);
        Path localMsi = localBase.resolve(msiName);
        Path msiLog = localBase.resolve("install-" + version + ".log");
        Path updaterPs = localBase.resolve("updater.ps1");

        logger.info("Update: kopiere MSI {} -> {}", remote, localMsi);
        Files.copy(remote, localMsi, StandardCopyOption.REPLACE_EXISTING);

        // --- 3) MOTW entfernen (best effort) -----------------------------------
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", "Unblock-File -Path \"" + localMsi.toString().replace("\"", "\\\"") + "\"")
                    .inheritIO()
                    .start()
                    .waitFor();
        } catch (Exception i) {
            logger.warn("Unblock-File nicht ausgeführt (ok): {}", i.toString());
        }

        // --- 4) PS1-Skript erzeugen ---
        String appExe = Paths.get(System.getenv("LOCALAPPDATA"), "Programs", "VIAS Export Tool", "VIAS Export Tool.exe").toString();

        String ps1 = String.join("\r\n",
                "$ErrorActionPreference = 'Stop'",
                "$msi = '" + localMsi.toString().replace("'", "''") + "'",
                "$log = '" + msiLog.toString().replace("'", "''") + "'",
                "$app = '" + appExe.replace("'", "''") + "'",

                // AJOUT : Attendre que le processus "VIAS Export Tool" soit vraiment mort
                "Write-Host 'Warte auf Beendigung der App...'",
                "$proc = Get-Process 'VIAS Export Tool' -ErrorAction SilentlyContinue",
                "if ($proc) { $proc | Wait-Process -Timeout 10 }", // Attendre max 10s

                // Installation
                "Start-Process -FilePath 'msiexec.exe' -ArgumentList \"/i `\"$msi`\" MSIINSTALLPERUSER=1 /passive /norestart /l*v `\"$log`\"\" -Wait",
                "Start-Sleep -Seconds 1",
                "if (Test-Path $app) { Start-Process -FilePath $app }"
        );

        Files.write(updaterPs, ps1.getBytes(StandardCharsets.UTF_8));
        logger.info("Updater-Skript erzeugt: {}", updaterPs);

        // --- 5) PowerShell mit -File starten (keine -Command-Fallen) -----------
        new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", updaterPs.toString())
                .inheritIO()
                .start();

        // --- 6) Aktuelle App beenden (Dateisperren lösen) ----------------------
        Platform.exit();
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            System.exit(0);
        }, "forced-exit").start();
    }


    /**
     * Container für Versionsinformationen aus der Online-Quelle.
     */
    private static class UpdateInfo {
        /**
         * Versionsnummer der verfügbaren Anwendung
         */
        final String version;
        /**
         * Changelog-Text für die neue Version
         */
        final String changelog;

        /**
         * Erstellt eine neue UpdateInfo-Instanz.
         *
         * @param version   Versionsnummer
         * @param changelog Änderungsbeschreibung
         */
        UpdateInfo(String version, String changelog) {
            this.version = version;
            this.changelog = changelog;
        }


        /**
         * Beendet die Anwendung komplett.
         */
        @FXML
        private void closeApplication() {
            // 1. Fermeture propre de JavaFX
            Platform.exit();

            // 2. Force brute pour tuer tous les threads restants (DB, Timer, etc.)
            // Le délai permet à JavaFX de finir ses animations de fermeture si nécessaire
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
                System.exit(0);
            }).start();
        }
    }
}
