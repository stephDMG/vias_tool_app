package service.op;

import javafx.concurrent.Task;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.ProgressReporter;


public class OpListeTask extends Task<Void> implements ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(OpListeTask.class);

    private final OpListeProcessService service;
    private final String language;
    private final String filter;
    private final ExportFormat format;

    public OpListeTask(OpListeProcessService service, String language, String filter, ExportFormat format) {
        this.service = service;
        this.language = language;
        this.filter = filter;
        this.format = format;
    }

    @Override
    protected Void call() throws Exception {
        try {
            // Passe l'instance de la tâche elle-même, qui agit comme un ProgressReporter
            service.executeOpListExport(language, filter, format, this);
            return null;
        } catch (Exception ex) {
            updateMessage("❌ Schwerer Fehler im Prozess: " + ex.getMessage());
            log.error("Fehler im OpListeTask", ex);
            throw ex;
        }
    }

    // Les méthodes de l'interface sont implémentées ici.
    @Override
    public void updateMessage(String message) {
        super.updateMessage(message);
    }

    @Override
    public void updateProgress(long workDone, long max) {
        super.updateProgress(workDone, max);
    }
}