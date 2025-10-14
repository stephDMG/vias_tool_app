// src/main/java/gui/controller/AuditViewController.java

package gui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import model.op.kunde.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.audit.AuditService;
import service.audit.AuditService.AuditType;
import service.audit.task.AuditTask;

import java.net.URL;
import java.util.ResourceBundle;

import static gui.controller.dialog.Dialog.showErrorDialog;
import static gui.controller.dialog.Dialog.showSuccessDialog;

/**
 * Controller für den Audit-Prozess.
 * Startet den AuditTask basierend auf dem ausgewählten Audit-Typ (Vertrag, Schaden, Beide).
 * Die Pfad-Logik liegt nun komplett in der AuditService-Ebene.
 */
public class AuditViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(AuditViewController.class);

    // --- FXML Elemente (Angepasst an 3 Buttons) ---
    @FXML private Button startVertragButton;
    @FXML private Button startSchadenButton;
    @FXML private Button startBeideButton;
    @FXML private Button stopButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private ListView<String> stepsListView;

    // --- Services und Task ---
    private final AuditService auditService = ServiceFactory.getAuditService();
    private final ObservableList<String> steps = FXCollections.observableArrayList();
    private AuditTask auditTask;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        stepsListView.setItems(steps);
        setReadyState();
    }

    // --- NEUE UI-Events für die 3 Buttons ---

    /**
     * Startet den AuditTask nur für Verträge (liest Vertragsliste.xlsx).
     */
    @FXML
    private void startVertragAudit() {
        startAuditProcess(AuditType.VERTRAG);
    }

    /**
     * Startet den AuditTask nur für Schaden (liest Schadenliste.xlsx).
     */
    @FXML
    private void startSchadenAudit() {
        startAuditProcess(AuditType.SCHADEN);
    }

    /**
     * Startet den AuditTask für beide Listen (liest beide Listen nacheinander).
     */
    @FXML
    private void startBeideAudit() {
        startAuditProcess(AuditType.BEIDE);
    }

    // --- Zentraler Prozess-Start, der den AuditType empfängt ---

    private void startAuditProcess(AuditType selectedType) {

        // *WICHTIG*: Die Pfad-Validierung sollte jetzt im AuditTask/AuditService erfolgen.
        // Der Controller validiert nur den Zustand der UI.

        // 1. Vorbereitung des Tasks
        steps.clear();
        steps.add("▶ Starte Audit: " + selectedType.getOrdnerName());

        // AuditTask mit AuditType initialisieren. Die Pfad-Logik liegt jetzt im Task/Service.
        // HINWEIS: AuditTask muss den Konstruktor public AuditTask(AuditService service, AuditType type) haben.
        auditTask = new AuditTask(auditService, selectedType);

        // 2. Bindungen an den Task (unverändert)
        progressBar.progressProperty().bind(auditTask.progressProperty());
        statusLabel.textProperty().bind(auditTask.messageProperty());

        auditTask.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal) && !newVal.equals("Done")) {
                steps.add(newVal);
                stepsListView.scrollTo(steps.size() - 1);
            }
        });

        // 3. Task-Callback für Erfolg/Misserfolg (unverändert)
        auditTask.setOnSucceeded(e -> handleTaskCompletion(auditTask.getValue()));
        auditTask.setOnFailed(e -> handleTaskFailure(auditTask.getException()));
        auditTask.setOnCancelled(e -> handleTaskFailure(null));

        // 4. Task starten
        setRunningState();
        new Thread(auditTask, "audit-task").start();
    }

    /**
     * Stoppt den laufenden AuditTask.
     */
    @FXML
    private void stopAuditProcess() {
        if (auditTask != null && auditTask.isRunning()) {
            steps.add("⏹ Stop angefordert…");
            log.warn("AuditTask manuell gestoppt.");
            auditTask.cancel(true);
        } else {
            steps.add("ℹ️ Kein laufender Prozess zum Stoppen.");
        }
    }

    // --- Hilfsmethoden (Bleiben im Wesentlichen gleich) ---

    // Im AuditViewController.java

    private void handleTaskCompletion(ExecutionResult result) {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        if (result.isSuccess()) {
            // --- KORRIGIERTER AUFRUF ---
            String title = "Audit Erfolgreich";
            String messageContent = String.format(
                    "✅ %d Dokumente erfolgreich kopiert.\nZielpfad: %s",
                    result.getRowCount(),
                    result.getOutputPath()
            );

            // Ruft die Methode mit nur ZWEI Argumenten auf: (Titel, Nachricht)
            showSuccessDialog(title, messageContent);

            // --- ENDE KORRIGIERTER AUFRUF ---

            steps.add(String.format("✅ Erfolg: %d Dokumente kopiert.", result.getRowCount()));
            setReadyState();
        } else {
            handleTaskFailure(new RuntimeException(result.getMessage()));
        }
    }

    private void handleTaskFailure(Throwable ex) {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        String msg = (ex != null ? ex.getMessage() : "Prozess manuell abgebrochen.");
        steps.add("❌ Fehler/Abbruch: " + msg);
        showErrorDialog("Audit Fehler", msg);
        setReadyState();
    }

    /**
     * Setzt die Buttons in den Start-Bereit-Zustand.
     */
    private void setReadyState() {
        progressBar.setVisible(false);
        progressBar.setProgress(0.0);
        // Aktiviert alle 3 Start-Buttons
        startVertragButton.setDisable(false);
        startSchadenButton.setDisable(false);
        startBeideButton.setDisable(false);
        stopButton.setDisable(true);
    }

    /**
     * Setzt die Buttons in den Prozess-Läuft-Zustand.
     */
    private void setRunningState() {
        progressBar.setVisible(true);
        // Deaktiviert alle 3 Start-Buttons
        startVertragButton.setDisable(true);
        startSchadenButton.setDisable(true);
        startBeideButton.setDisable(true);
        stopButton.setDisable(false);
    }
}