// src/main/java/service/audit/task/AuditTask.java

package service.audit.task;

import javafx.concurrent.Task;
import model.op.kunde.ExecutionResult;
import service.audit.AuditService;
import service.interfaces.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX-Task zur asynchronen Durchführung des Audits.
 * Implementiert ProgressReporter zur Aktualisierung der GUI über den AuditService.
 */
public class AuditTask extends Task<ExecutionResult> implements ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(AuditTask.class);

    private final AuditService service;
    private final AuditService.AuditType auditType;

    // Anmerkung: excelFilePath wird hier nicht mehr benötigt, da die Pfad-Logik im AuditService liegt.

    // KORRIGIERTER KONSTRUKTOR
    public AuditTask(AuditService service, AuditService.AuditType auditType) {
        this.service = service;
        // Zeile "this.excelFilePath = excelFilePath;" wurde entfernt
        this.auditType = auditType;
    }

    @Override
    protected ExecutionResult call() throws Exception {
        try {
            updateMessage("Starte Audit für Typ: " + auditType.getOrdnerName() + "...");

            // KORRIGIERTER AUFRUF (Dies ist die fehlerhafte Zeile 43)
            // AuditService erwartet: (AuditType, ProgressReporter)
            ExecutionResult result = service.startAudit(auditType, this);

            // Abschlussmeldung in den Task spiegeln
            updateMessage(result.getMessage());
            if (result.isSuccess()) {
                updateProgress(100, 100);
            }
            return result;

        } catch (Exception ex) {
            log.error("Schwerer Fehler im AuditTask für Typ: {}", auditType, ex);
            String errorMessage = "Ein kritischer Fehler ist aufgetreten: " + ex.getMessage();
            updateMessage(errorMessage);
            return new ExecutionResult(ExecutionResult.Status.FAILURE, errorMessage);
        }
    }

    // --- ProgressReporter-Weiterleitungen auf den JavaFX-Task ---

    @Override
    public void updateMessage(String message) {
        super.updateMessage(message);
    }

    @Override
    public void updateProgress(long workDone, long max) {
        super.updateProgress(workDone, max);
    }
}