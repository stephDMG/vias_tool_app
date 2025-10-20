package service.audit.task;

import javafx.concurrent.Task;
import model.op.kunde.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.audit.AuditService;
import service.interfaces.ProgressReporter;

public class SbMismatchSchadenTask extends Task<ExecutionResult> implements ProgressReporter {
    private static final Logger log = LoggerFactory.getLogger(SbMismatchSchadenTask.class);
    private final AuditService service;

    public SbMismatchSchadenTask(AuditService service) {
        this.service = service;
    }

    @Override
    protected ExecutionResult call() {
        try {
            updateMessage("Prüfe SB-Abweichungen (Schaden)...");
            ExecutionResult result = service.checkSbMismatchSchaden(this);
            updateMessage(result.getMessage());
            if (result.isSuccess()) updateProgress(100, 100);
            return result;
        } catch (Exception ex) {
            log.error("Fehler im SbMismatchSchadenTask", ex);
            String err = "Ein kritischer Fehler ist aufgetreten: " + ex.getMessage();
            updateMessage(err);
            return new ExecutionResult(ExecutionResult.Status.FAILURE, err);
        }
    }

    @Override public void updateMessage(String message) { super.updateMessage(message); }
    @Override public void updateProgress(long workDone, long max) { super.updateProgress(workDone, max); }
}
