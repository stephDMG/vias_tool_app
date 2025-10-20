// src/main/java/service/audit/task/ManualVertragTask.java
package service.audit.task;

import javafx.concurrent.Task;
import model.op.kunde.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.audit.AuditService;
import service.interfaces.ProgressReporter;

import java.util.List;

/**
 * JavaFX-Task für den manuellen Verträge-Audit.
 * Ruft AuditService.startManualVertragAudit(...) auf und spiegelt Progress/Messages in die UI.
 */
public class ManualVertragTask extends Task<ExecutionResult> implements ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(ManualVertragTask.class);

    private final AuditService service;
    private final List<String> policeNrs;

    public ManualVertragTask(AuditService service, List<String> policeNrs) {
        this.service = service;
        this.policeNrs = policeNrs;
    }

    @Override
    protected ExecutionResult call() {
        try {
            updateMessage("Manueller Verträge-Audit wird gestartet...");
            ExecutionResult result = service.startManualVertragAudit(policeNrs, this);
            updateMessage(result.getMessage());
            if (result.isSuccess()) {
                updateProgress(100, 100);
            }
            return result;
        } catch (Exception ex) {
            log.error("Schwerer Fehler im ManualVertragTask", ex);
            String errorMessage = "Ein kritischer Fehler ist aufgetreten: " + ex.getMessage();
            updateMessage(errorMessage);
            return new ExecutionResult(ExecutionResult.Status.FAILURE, errorMessage);
        }
    }

    // ProgressReporter → an Task weiterreichen
    @Override public void updateMessage(String message) { super.updateMessage(message); }
    @Override public void updateProgress(long workDone, long max) { super.updateProgress(workDone, max); }
}
