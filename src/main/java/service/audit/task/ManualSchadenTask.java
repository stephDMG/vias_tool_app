package service.audit.task;

import javafx.concurrent.Task;
import model.op.kunde.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.audit.AuditService;
import service.interfaces.ProgressReporter;

import java.util.List;

public class ManualSchadenTask extends Task<ExecutionResult> implements ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(ManualSchadenTask.class);

    private final AuditService service;
    private final List<String> luSnrList;

    public ManualSchadenTask(AuditService service, List<String> luSnrList) {
        this.service = service;
        this.luSnrList = luSnrList;
    }

    @Override
    protected ExecutionResult call() {
        try {
            updateMessage("Manueller Schaden-Audit wird gestartet...");
            ExecutionResult result = service.startManualSchadenAudit(luSnrList, this);
            updateMessage(result.getMessage());
            if (result.isSuccess()) {
                updateProgress(100, 100);
            }
            return result;
        } catch (Exception ex) {
            log.error("Schwerer Fehler im ManualSchadenTask", ex);
            String errorMessage = "Ein kritischer Fehler ist aufgetreten: " + ex.getMessage();
            updateMessage(errorMessage);
            return new ExecutionResult(ExecutionResult.Status.FAILURE, errorMessage);
        }
    }

    // ProgressReporter → an Task weiterreichen
    @Override
    public void updateMessage(String message) {
        super.updateMessage(message);
    }

    @Override
    public void updateProgress(long workDone, long max) {
        super.updateProgress(workDone, max);
    }
}
