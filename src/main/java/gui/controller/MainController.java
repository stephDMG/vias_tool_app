package gui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AccessControlService;
import service.LoginService;
import service.UpdateService;
import service.theme.ThemeService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static gui.controller.dialog.Dialog.showErrorDialog;

/**
 * Haupt-Controller des Hauptfensters (MainWindow.fxml).
 * <p>
 * Verantwortlich für:
 * - Initialisierung der UI (Begrüßung, Rechteprüfung, Vorladen der Teil-Views)
 * - Navigation/Ansichtswechsel (Extraction, Data, DbExport, AI, Pivot, Dashboard, Enrichment, OP-Liste)
 * - Updateprüfung inkl. Download, Integritätscheck und Start des Installers
 */
public class MainController implements Initializable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MainController.class);

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String BASE =
            "\\\\Debresrv10\\csdatenwelt$\\EDV\\Client_WIN_11\\Software\\VIAS_TOOL_PUBLIC\\jpackage_output\\";

    private static final String VERSION_FILE_PATH = BASE + "version.txt";
    private static final String CHANGELOG_FILE_PATH = BASE + "changelog.txt";

    private String CURRENT_VERSION = "N/A";
    private Parent opListView;

    @FXML
    private BorderPane mainBorderPane;
    private Parent extractionView, dataView, dbExportView, aiAssistantView, pivotView, dashboardView, showEnrichmentView, settingsView;

    @FXML
    private MenuItem statusItem;
    @FXML
    private TextFlow userGreeting;

    public static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String[] words = input.split("\\s+");
        StringJoiner result = new StringJoiner(" ");
        for (String word : words) {
            result.add(word.substring(0, 1).toUpperCase() + word.substring(1));
        }
        return result.toString();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        logger.info("🎯 MainController wird initialisiert...");
        loadAppVersion();
        LoginService loginService = new LoginService();
        String username = loginService.getCurrentWindowsUsername();
        AccessControlService accessControl = new AccessControlService();
        statusItem.setVisible(accessControl.hasPermission(username, "op-list") || accessControl.hasPermission(username, "all"));

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

        } catch (IOException e) {
            logger.error("❌ Fehler beim Vorladen der Ansichten", e);
            e.printStackTrace();
        }

        ThemeService themeService = new ThemeService();
        themeService.applyTheme(themeService.loadTheme());

        showDashboardView();
        checkForUpdates();
    }

    private void loadAppVersion() {
        try (InputStream input = getClass().getResourceAsStream("/version.properties")) {
            if (input == null) {
                logger.error("version.properties nicht in der JAR gefunden!");
                return;
            }
            Properties props = new Properties();
            props.load(input);
            this.CURRENT_VERSION = props.getProperty("app.version", "N/A");
            logger.info("Anwendungsversion erfolgreich geladen: {}", CURRENT_VERSION);
        } catch (IOException ex) {
            logger.error("Fehler beim Lesen der version.properties", ex);
        }
    }

    private void checkForUpdates() {
        Task<UpdateInfo> versionCheckTask = new Task<>() {
            @Override
            protected UpdateInfo call() {
                try {
                    logger.info("Prüfe auf neue Version unter: {}", VERSION_FILE_PATH);

                    // Version prüfen
                    Path versionPath = Paths.get(VERSION_FILE_PATH);
                    if (!Files.exists(versionPath)) {
                        logger.warn("Version-Datei existiert nicht: {}", VERSION_FILE_PATH);
                        return null;
                    }
                    String onlineVersion = Files.readString(versionPath, StandardCharsets.UTF_8).trim();

                    // Changelog laden
                    String changelogContent = "Keine Änderungsbeschreibung verfügbar.";
                    Path changelogPath = Paths.get(CHANGELOG_FILE_PATH);
                    if (Files.exists(changelogPath)) {
                        try {
                            changelogContent = Files.readString(changelogPath, StandardCharsets.UTF_8);
                            logger.info("Changelog erfolgreich geladen ({} Zeichen)", changelogContent.length());
                        } catch (IOException e) {
                            logger.warn("Fehler beim Lesen der changelog.txt: {}", e.getMessage());
                        }
                    } else {
                        logger.warn("Changelog-Datei existiert nicht: {}", CHANGELOG_FILE_PATH);
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

            @Override
            protected void failed() {
                logger.warn("Update-Prüfung fehlgeschlagen: {}", getException().getMessage());
            }
        };

        Thread updateThread = new Thread(versionCheckTask, "update-check-thread");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private boolean isNewer(String onlineVersion, String currentVersion) {
        if (onlineVersion == null || currentVersion == null) return false;

        String o = onlineVersion.trim();
        String c = currentVersion.trim();

        // supprime suffixes (-SNAPSHOT, -rc, etc.)
        o = o.replaceAll("[^0-9.].*$", "");
        c = c.replaceAll("[^0-9.].*$", "");

        String[] os = o.split("\\.");
        String[] cs = c.split("\\.");
        for (int i = 0; i < Math.max(os.length, cs.length); i++) {
            int oi = (i < os.length && os[i].matches("\\d+")) ? Integer.parseInt(os[i]) : 0;
            int ci = (i < cs.length && cs[i].matches("\\d+")) ? Integer.parseInt(cs[i]) : 0;
            if (oi != ci) return oi > ci;
        }
        return false;
    }

    // Fügen Sie diese Methode zu Ihrem MainController hinzu
    private void showUpdateDialog(UpdateInfo info) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update verfügbar");
        alert.setHeaderText("Eine neue Version (" + info.version + ") des VIAS Export Tools ist verfügbar!");
        alert.setContentText("Möchten Sie das Update jetzt herunterladen und installieren? Die Anwendung wird dazu beendet.");

        ButtonType updateButton = new ButtonType("Jetzt aktualisieren");
        ButtonType laterButton = new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(updateButton, laterButton);

        VBox dialogPaneContent = new VBox();
        Label label = new Label("Änderungen in den neuen Versionen:");
        TextArea textArea = new TextArea(info.changelog);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        dialogPaneContent.getChildren().addAll(label, textArea);
        alert.getDialogPane().setExpandableContent(dialogPaneContent);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == updateButton) {
            startUpdateProcess(info);
        }
    }

    private void startUpdateProcess(UpdateInfo info) {
        // 0) Doit correspondre EXACTEMENT à --name dans jpackage (release.ps1)
        final String productName = "VIAS Export Tool";

        // 1) Base dir = dossier de version.txt (UNC \\DEBRESRV10\CSDATENWELT$ ...)
        Path versionFilePath = Paths.get(VERSION_FILE_PATH);
        Path baseDir = versionFilePath.getParent();

        // 2) Nom attendu
        String expectedFileName = productName + "-" + info.version + ".msi";
        Path msiPath = baseDir.resolve(expectedFileName);
        Path checksumPath = baseDir.resolve(expectedFileName + ".sha256");

        logger.info("Update: expected MSI: {}", msiPath);

        // 3) Fallback: si le nom exact n'est pas trouvé, scanner le dossier
        if (!Files.exists(msiPath)) {
            logger.warn("Expected MSI not found, scanning for fallback in {}", baseDir);
            try {
                Optional<Path> candidate = Files.list(baseDir)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".msi"))
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.contains("-" + info.version) &&
                                    n.toLowerCase().startsWith(productName.toLowerCase());
                        })
                        .findFirst();
                if (candidate.isPresent()) {
                    msiPath = candidate.get();
                    checksumPath = baseDir.resolve(msiPath.getFileName().toString() + ".sha256");
                    logger.warn("Using fallback MSI: {}", msiPath);
                }
            } catch (IOException e) {
                logger.warn("Error while scanning for MSI fallback: {}", e.getMessage());
            }
        }

        // 4) Vérifs
        if (!Files.exists(msiPath)) {
            showErrorDialog("Update-Fehler",
                    "Installer-Datei nicht gefunden:\n" + msiPath +
                            "\n\nErwarteter Name: " + expectedFileName + "\nPfad: " + baseDir);
            return;
        }
        if (!Files.isReadable(msiPath)) {
            showErrorDialog("Update-Fehler",
                    "Kein Lesezugriff auf den Installer:\n" + msiPath);
            return;
        }

        if (!Files.exists(checksumPath)) {
            logger.warn("Checksum file not found: {}", checksumPath);
        }

        final Path msiPathFinal = msiPath;
        final Path checksumPathFinal = checksumPath;

        // 5) UI de progression
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Update wird ausgeführt");
        ProgressBar progressBar = new ProgressBar(0);
        Label statusLabel = new Label("Initialisierung...");
        VBox vbox = new VBox(20, new Label("Bitte warten, das Update wird vorbereitet..."), progressBar, statusLabel);
        vbox.setPadding(new Insets(20));
        progressDialog.getDialogPane().setContent(vbox);
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // Tâche de téléchargement/copie
        UpdateService updateService = new UpdateService();
        Task<File> downloadTask = updateService.createDownloadTask(msiPathFinal.toString());

        progressBar.progressProperty().bind(downloadTask.progressProperty());
        statusLabel.textProperty().bind(downloadTask.messageProperty());
        progressDialog.setOnCloseRequest(e -> downloadTask.cancel(true));

        downloadTask.setOnSucceeded(event -> {
            progressDialog.close();
            File downloadedMsi = downloadTask.getValue();

            try {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Überprüfe Datei-Integrität...");

                if (Files.exists(checksumPathFinal)) {
                    String expectedChecksum = updateService.downloadExpectedChecksum(checksumPathFinal.toString());
                    if (!updateService.verifyChecksum(downloadedMsi, expectedChecksum)) {
                        logger.error("Checksum mismatch. Aborting.");
                        showErrorDialog("Update-Fehler", "Die heruntergeladene Datei ist beschädigt.");
                        return;
                    }
                    logger.info("Checksum OK.");
                } else {
                    logger.warn("Checksum fehlt – fahre ohne Integritätsprüfung fort.");
                }

                runInstallerAndExit(downloadedMsi);
            } catch (Exception e) {
                logger.error("Fehler bei der Integritätsprüfung.", e);
                showErrorDialog("Update-Fehler", "Die Datei-Integrität konnte nicht überprüft werden:\n" + e.getMessage());
            }
        });

        downloadTask.setOnFailed(event -> {
            progressDialog.close();
            showErrorDialog("Update-Fehler", "Der Download ist fehlgeschlagen:\n" + downloadTask.getException().getMessage());
        });

        new Thread(downloadTask, "app-update-thread").start();
        progressDialog.showAndWait();
    }

    // Fügen Sie diese neue Methode zu Ihrem MainController hinzu
    private void runInstallerAndExit(File msiFile) {
        logger.info("Starte Installer: {}", msiFile.getAbsolutePath());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", "start", "\"VIAS Update\"",
                    System.getenv("WINDIR") + "\\System32\\msiexec.exe",
                    "/i", "\"" + msiFile.getAbsolutePath() + "\"",
                    "/passive", "/norestart"
            );
            pb.start();
        } catch (IOException e) {
            logger.error("msiexec konnte nicht gestartet werden. Fallback auf Desktop.open()", e);
            try {
                // Fallback, falls der erste Versuch scheitert
                java.awt.Desktop.getDesktop().open(msiFile);
            } catch (IOException ex) {
                logger.error("Fallback mit Desktop.open() ist ebenfalls fehlgeschlagen.", ex);
                showErrorDialog("Update-Fehler", "Der Installer konnte nicht gestartet werden.");
            }
        } finally {
            // Wichtig: Die Anwendung beenden, nachdem der Installer-Prozess gestartet wurde.
            logger.info("Schließe die Anwendung, um das Update abzuschließen.");
            Platform.exit();
        }
    }

    // --- Methoden zum Wechseln der Ansichten ---
    @FXML
    private void showExtractionView() {
        mainBorderPane.setCenter(extractionView);
    }

    @FXML
    private void showDataView() {
        mainBorderPane.setCenter(dataView);
    }

    @FXML
    private void showDbExportView() {
        mainBorderPane.setCenter(dbExportView);
    }

    @FXML
    private void showAiAssistantView() {
        mainBorderPane.setCenter(aiAssistantView);
    }

    @FXML
    private void showPivotView() {
        mainBorderPane.setCenter(pivotView);
    }

    @FXML
    private void showDashboardView() {
        mainBorderPane.setCenter(dashboardView);
    }

    @FXML
    private void showEnrichmentView() {
        mainBorderPane.setCenter(showEnrichmentView);
    }

    @FXML
    private void showOpListView() {
        mainBorderPane.setCenter(opListView);
    }

    @FXML
    private void showSettingsView() {
        mainBorderPane.setCenter(settingsView);
    }

    @FXML
    private void closeApplication() {
        Platform.exit();
    }

    private static class UpdateInfo {
        final String version;
        final String changelog;

        UpdateInfo(String version, String changelog) {
            this.version = version;
            this.changelog = changelog;
        }
    }
}