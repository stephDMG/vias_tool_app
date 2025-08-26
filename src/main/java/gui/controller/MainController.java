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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.UpdateService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String VERSION_FILE_PATH = "X:\\FREIE ZONE\\Dongmo, Stephane\\Vias-Tool\\jpackage_output\\version.txt";
    private static final String CHANGELOG_FILE_PATH = "X:\\FREIE ZONE\\Dongmo, Stephane\\Vias-Tool\\jpackage_output\\changelog.txt";
    private String CURRENT_VERSION = "N/A";
    private Parent opListView;

    @FXML
    private BorderPane mainBorderPane;
    private Parent extractionView, dataView, dbExportView, aiAssistantView, pivotView, dashboardView, showEnrichmentView;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("🎯 MainController wird initialisiert...");
        loadAppVersion();

        try {
            extractionView = FXMLLoader.load(getClass().getResource("/fxml/ExtractionView.fxml"));
            dataView = FXMLLoader.load(getClass().getResource("/fxml/DataView.fxml"));
            dbExportView = FXMLLoader.load(getClass().getResource("/fxml/DbExportView.fxml"));
            aiAssistantView = FXMLLoader.load(getClass().getResource("/fxml/AiAssistantView.fxml"));
            pivotView = FXMLLoader.load(getClass().getResource("/fxml/PivotView.fxml"));
            dashboardView = FXMLLoader.load(getClass().getResource("/fxml/DashboardView.fxml"));
            showEnrichmentView = FXMLLoader.load(getClass().getResource("/fxml/EnrichmentView.fxml"));
            opListView = FXMLLoader.load(getClass().getResource("/fxml/OpListView.fxml"));
        } catch (IOException e) {
            logger.error("❌ Fehler beim Vorladen der Ansichten", e);
            e.printStackTrace();
        }

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
                    String onlineVersion = Files.readString(Paths.get(VERSION_FILE_PATH), StandardCharsets.UTF_8).trim();

                    String changelogContent = "Konnte keine Änderungsbeschreibung laden.";
                    try {
                        changelogContent = Files.readString(Paths.get(CHANGELOG_FILE_PATH), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        logger.warn("Konnte changelog.txt nicht lesen: {}", e.getMessage());
                    }
                    return new UpdateInfo(onlineVersion, changelogContent);
                } catch (IOException e) {
                    logger.warn("Konnte version.txt nicht lesen: {}", e.getMessage());
                    return null;
                }
            }

            @Override
            protected void succeeded() {
                UpdateInfo info = getValue();
                if (info != null && info.version != null && isNewer(info.version, CURRENT_VERSION)) {
                    logger.info("Neue Version gefunden! Online: {}, Aktuell: {}", info.version, CURRENT_VERSION);
                    // KORRIGIERTER AUFRUF: Übergibt das gesamte 'info'-Objekt.
                    Platform.runLater(() -> showUpdateDialog(info));
                } else {
                    logger.info("Keine neue Version gefunden.");
                }
            }
        };
        new Thread(versionCheckTask).start();
    }

    private boolean isNewer(String onlineVersion, String currentVersion) {
        return onlineVersion.compareTo(currentVersion) > 0;
    }

    // GELÖSCHT: Die alte, einfache showUpdateDialog(String newVersion) Methode wurde entfernt.

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
        String msiPath = baseDir.resolve(msiName).toString();
        String checksumPath = baseDir.resolve(msiName + ".sha256").toString();

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
        Task<File> downloadTask = updateService.createDownloadTask(msiPath);

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
                String expectedChecksum = updateService.downloadExpectedChecksum(checksumPath);
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
            // Robuster Start via cmd /c start... msiexec.exe
            // /passive -> Minimale UI, /norestart -> Verhindert automatischen Neustart
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
    // Fügen Sie diese neue Methode zu Ihrem MainController hinzu
    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // --- Methoden zum Wechseln der Ansichten ---
    @FXML private void showExtractionView() { mainBorderPane.setCenter(extractionView); }
    @FXML private void showDataView() { mainBorderPane.setCenter(dataView); }
    @FXML private void showDbExportView() { mainBorderPane.setCenter(dbExportView); }
    @FXML private void showAiAssistantView() { mainBorderPane.setCenter(aiAssistantView); }
    @FXML private void showPivotView() { mainBorderPane.setCenter(pivotView); }
    @FXML private void showDashboardView() { mainBorderPane.setCenter(dashboardView); }
    @FXML private void showEnrichmentView() { mainBorderPane.setCenter(showEnrichmentView); }
    @FXML  private void showOpListView() { mainBorderPane.setCenter(opListView);}

    @FXML private void closeApplication() {
        Platform.exit();
    }
}