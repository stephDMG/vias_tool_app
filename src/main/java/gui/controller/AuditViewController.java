// src/main/java/gui/controller/AuditViewController.java
package gui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import model.op.kunde.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.audit.AuditService;
import service.audit.AuditService.AuditType;
import service.audit.task.*;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static gui.controller.dialog.Dialog.showErrorDialog;
import static gui.controller.dialog.Dialog.showSuccessDialog;

/**
 * Controller für den Audit-Prozess.
 * - Standard: Verträge / Schaden / Beide (Auto, aus Excel)
 * - Manuell:  Schaden (LU_SNR oder CS-20YY-xxxxx), Verträge (Police Nr.)
 */
public class AuditViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(AuditViewController.class);
    // --- Services / State ---
    private final AuditService auditService = ServiceFactory.getAuditService();
    private final ObservableList<String> steps = FXCollections.observableArrayList();
    // --- FXML Elemente ---
    @FXML
    private Button startVertragButton;
    @FXML
    private Button startSchadenButton;
    @FXML
    private Button startBeideButton;
    @FXML
    private Button startManualSchadenButton;
    @FXML
    private Button startManualVertragButton;
    @FXML
    private Button stopButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private ListView<String> stepsListView;
    @FXML
    private TextArea manualSchadenInput;
    @FXML
    private TextArea manualVertragInput;
    private Task<ExecutionResult> currentTask;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        stepsListView.setItems(steps);
        setReadyState();
    }

    // =====================================================================
    // Standard-Buttons (Auto aus Excel)
    // =====================================================================

    @FXML
    private void startVertragAudit() {
        steps.clear();
        steps.add("▶ Starte Audit: " + AuditType.VERTRAG.getOrdnerName());
        AuditTask task = new AuditTask(auditService, AuditType.VERTRAG);
        bindAndRunTask(task, "Audit gestartet: Verträge");
    }

    @FXML
    private void startSchadenAudit() {
        steps.clear();
        steps.add("▶ Starte Audit: " + AuditType.SCHADEN.getOrdnerName());
        AuditTask task = new AuditTask(auditService, AuditType.SCHADEN);
        bindAndRunTask(task, "Audit gestartet: Schaden");
    }

    @FXML
    private void startBeideAudit() {
        steps.clear();
        steps.add("▶ Starte Audit: " + AuditType.BEIDE.getOrdnerName());
        AuditTask task = new AuditTask(auditService, AuditType.BEIDE);
        bindAndRunTask(task, "Audit gestartet: BEIDE");
    }

    // =====================================================================
    // Manueller Modus
    // =====================================================================

    /**
     * Schaden (manuell) – akzeptiert LU_SNR oder CS-20YY-xxxxx; Trennzeichen , ; Leerzeichen \n
     */
    @FXML
    private void startSchadenAuditManuell() {
        String raw = (manualSchadenInput != null) ? manualSchadenInput.getText() : null;
        if (raw == null || raw.trim().isEmpty()) {
            statusLabel.setText("Bitte Schaden-Nummern eingeben (z. B. 2408474, 2506266).");
            return;
        }
        List<String> entries = Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (entries.isEmpty()) {
            statusLabel.setText("Keine gültigen Schaden-Nummern erkannt.");
            return;
        }

        steps.clear();
        steps.add("▶ Manueller Schaden-Audit für " + entries.size() + " Nummer(n)...");
        ManualSchadenTask task = new ManualSchadenTask(auditService, entries);
        bindAndRunTask(task, "Manueller Schaden-Audit gestartet");
    }

    /**
     * Verträge (manuell) – Police Nr. 1..N; Trennzeichen , ; Leerzeichen \n
     */
    @FXML
    private void startVertragAuditManuell() {
        String raw = (manualVertragInput != null) ? manualVertragInput.getText() : null;
        if (raw == null || raw.trim().isEmpty()) {
            statusLabel.setText("Bitte Policennummern eingeben (z. B. W1040174, W1040175).");
            return;
        }
        List<String> policeList = Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (policeList.isEmpty()) {
            statusLabel.setText("Keine gültigen Policennummern erkannt.");
            return;
        }

        steps.clear();
        steps.add("▶ Manueller Verträge-Audit für " + policeList.size() + " Nummer(n)...");
        ManualVertragTask task = new ManualVertragTask(auditService, policeList);
        bindAndRunTask(task, "Manueller Verträge-Audit gestartet");
    }

    // =====================================================================
    // Stop / Bindings / Zustände
    // =====================================================================

    @FXML
    private void stopAuditProcess() {
        if (currentTask != null && currentTask.isRunning()) {
            steps.add("⏹ Stop angefordert…");
            log.warn("Aktueller Audit-Task manuell gestoppt.");
            currentTask.cancel(true);
        } else {
            steps.add("ℹ️ Kein laufender Prozess zum Stoppen.");
        }
    }

    private void bindAndRunTask(Task<ExecutionResult> task, String startMsg) {
        progressBar.setVisible(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal) && !newVal.equals("Done")) {
                steps.add(newVal);
                stepsListView.scrollTo(steps.size() - 1);
            }
        });

        task.setOnSucceeded(e -> handleTaskCompletion(task.getValue()));
        task.setOnFailed(e -> handleTaskFailure(task.getException()));
        task.setOnCancelled(e -> handleTaskFailure(null));

        setRunningState();
        currentTask = task;
        new Thread(task, "audit-task").start();

        if (startMsg != null && !startMsg.isBlank()) {
            steps.add(startMsg);
        }
    }

    private void handleTaskCompletion(ExecutionResult result) {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();

        if (result != null && result.isSuccess()) {
            String title = "Audit Erfolgreich";
            String messageContent = String.format(
                    "✅ %d Dokumente erfolgreich kopiert.\nZielpfad: %s",
                    result.getRowCount(),
                    result.getOutputPath()
            );
            showSuccessDialog(title, messageContent);
            steps.add(String.format("✅ Erfolg: %d Dokumente kopiert.", result.getRowCount()));
        } else {
            handleTaskFailure(new RuntimeException(result != null ? result.getMessage() : "Unbekannter Fehler"));
            return;
        }
        setReadyState();
    }

    private void handleTaskFailure(Throwable ex) {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        String msg = (ex != null ? ex.getMessage() : "Prozess abgebrochen.");
        steps.add("❌ Fehler/Abbruch: " + msg);
        showErrorDialog("Audit Fehler", msg);
        setReadyState();
    }

    private void setReadyState() {
        progressBar.setVisible(false);
        progressBar.setProgress(0.0);

        startVertragButton.setDisable(false);
        startSchadenButton.setDisable(false);
        startBeideButton.setDisable(false);
        if (startManualSchadenButton != null) startManualSchadenButton.setDisable(false);
        if (startManualVertragButton != null) startManualVertragButton.setDisable(false);

        stopButton.setDisable(true);
    }

    private void setRunningState() {
        progressBar.setVisible(true);

        startVertragButton.setDisable(true);
        startSchadenButton.setDisable(true);
        startBeideButton.setDisable(true);
        if (startManualSchadenButton != null) startManualSchadenButton.setDisable(true);
        if (startManualVertragButton != null) startManualVertragButton.setDisable(true);

        stopButton.setDisable(false);
    }

    @FXML
    private void checkSbMismatchVertrag() {
        SbMismatchVertragTask t = new SbMismatchVertragTask(auditService);
        bindAndRunTask(t, "SB-Abgleich (Verträge) gestartet.");
    }

    @FXML
    private void checkSbMismatchSchaden() {
        SbMismatchSchadenTask t = new SbMismatchSchadenTask(auditService);
        bindAndRunTask(t, "SB-Abgleich (Schaden) gestartet.");
    }


}
