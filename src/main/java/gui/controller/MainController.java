package gui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
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

import javax.swing.text.Element;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static gui.controller.utils.Dialog.showErrorDialog;

/**
 * Haupt-Controller des Hauptfensters (MainWindow.fxml).
 *
 * Verantwortlich für:
 * - Initialisierung der UI (Begrüßung, Rechteprüfung, Vorladen der Teil-Views)
 * - Navigation/Ansichtswechsel (Extraction, Data, DbExport, AI, Pivot, Dashboard, Enrichment, OP-Liste)
 * - Updateprüfung inkl. Download, Integritätscheck und Start des Installers
 */
public class MainController implements Initializable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MainController.class);

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String VERSION_FILE_PATH = "C:\\VIAS_TOOL_PUBLIC\\jpackage_output\\version.txt";
    private static final String CHANGELOG_FILE_PATH = "C:\\VIAS_TOOL_PUBLIC\\jpackage_output\\changelog.txt";
    private String CURRENT_VERSION = "N/A";
    private Parent opListView;

    @FXML
    private BorderPane mainBorderPane;
    private Parent extractionView, dataView, dbExportView, aiAssistantView, pivotView, dashboardView, showEnrichmentView, settingsView;

    @FXML
    private MenuItem statusItem;
    @FXML
    private TextFlow userGreeting;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        logger.info("🎯 MainController wird initialisiert...");
        loadAppVersion();
        LoginService loginService = new LoginService();
        String username = loginService.getCurrentWindowsUsername();
        AccessControlService accessControl = new AccessControlService();
        statusItem.setVisible(accessControl.hasPermission(username, "op-status") || accessControl.hasPermission(username, "all"));

        String user =  username.replace('.', ' ');
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

    private static class UpdateInfo {
        final String version;
        final String changelog;

        UpdateInfo(String version, String changelog) {
            this.version = version;
            this.changelog = changelog;
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
                if (info != null && info.version != null && isNewer(info.version, CURRENT_VERSION)) {
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
        return onlineVersion.compareTo(currentVersion) > 0;
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


    // Ersetzen Sie Ihre alte startUpdateProcess-Methode durch diese
    private void startUpdateProcess(UpdateInfo info) {
        // 1. Pfade für Installer und Prüfsumme konstruieren
        Path versionFilePath = Paths.get(VERSION_FILE_PATH);
        Path baseDir = versionFilePath.getParent();
        String msiName = "VIAS Export Tool-" + info.version + ".msi";
        Path msiPath = baseDir.resolve(msiName);
        Path checksumPath = baseDir.resolve(msiName + ".sha256");

        // Prüfe ob die Dateien existieren
        if (!Files.exists(msiPath)) {
            showErrorDialog("Update-Fehler",
                    "Installer-Datei nicht gefunden:\n" + msiPath.toString());
            return;
        }

        if (!Files.exists(checksumPath)) {
            logger.warn("Prüfsummen-Datei nicht gefunden: {}", checksumPath);
            // Trotzdem fortfahren, aber ohne Integritätsprüfung
        }


        // 2. UI für den Fortschritt erstellen
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Update wird ausgeführt");
        ProgressBar progressBar = new ProgressBar(0);
        Label statusLabel = new Label("Initialisierung...");
        VBox vbox = new VBox(20, new Label("Bitte warten, das Update wird vorbereitet..."), progressBar, statusLabel);
        vbox.setPadding(new Insets(20));
        progressDialog.getDialogPane().setContent(vbox);
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // 3. Update-Task erstellen
        UpdateService updateService = new UpdateService();
        Task<File> downloadTask = updateService.createDownloadTask(msiPath.toString());

        // 4. UI an den Task binden
        progressBar.progressProperty().bind(downloadTask.progressProperty());
        statusLabel.textProperty().bind(downloadTask.messageProperty());
        progressDialog.setOnCloseRequest(e -> downloadTask.cancel(true));

        downloadTask.setOnSucceeded(event -> {
            progressDialog.close();
            File downloadedMsi = downloadTask.getValue();

            // 5. Integritätsprüfung durchführen
            try {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Überprüfe Datei-Integrität...");
                logger.info("Lade erwartete Prüfsumme von: {}", checksumPath);
                String expectedChecksum = updateService.downloadExpectedChecksum(checksumPath.toString());
                if (updateService.verifyChecksum(downloadedMsi, expectedChecksum)) {
                    logger.info("✅ Prüfsumme korrekt. Starte Installer.");
                    runInstallerAndExit(downloadedMsi);
                } else {
                    logger.error("❌ Prüfsumme stimmt nicht überein! Update wird abgebrochen.");
                    showErrorDialog("Update-Fehler", "Die heruntergeladene Datei ist beschädigt. Bitte versuchen Sie es später erneut.");
                }
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
    @FXML private void showExtractionView() { mainBorderPane.setCenter(extractionView); }
    @FXML private void showDataView() { mainBorderPane.setCenter(dataView); }
    @FXML private void showDbExportView() { mainBorderPane.setCenter(dbExportView); }
    @FXML private void showAiAssistantView() { mainBorderPane.setCenter(aiAssistantView); }
    @FXML private void showPivotView() { mainBorderPane.setCenter(pivotView); }
    @FXML private void showDashboardView() { mainBorderPane.setCenter(dashboardView); }
    @FXML private void showEnrichmentView() { mainBorderPane.setCenter(showEnrichmentView); }
    @FXML private void showOpListView() { mainBorderPane.setCenter(opListView);}
    @FXML private void showSettingsView() { mainBorderPane.setCenter(settingsView); }

    @FXML private void closeApplication() {
        Platform.exit();
    }
}