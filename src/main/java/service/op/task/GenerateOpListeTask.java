package service.op.task;

import javafx.concurrent.Task;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.ProgressReporter;
import model.op.kunde.ExecutionResult;
import service.op.OPListenService;

/**
 * JavaFX-Task zur asynchronen Erstellung der OP-Liste über den {@link OPListenService}.
 * <p>
 * Der Task kapselt die Ausführung für einen ausgewählten Kunden, übergibt
 * Sprache und Exportformat an den Service und liefert ein detailliertes
 * {@link ExecutionResult} an die GUI zurück. Gleichzeitig implementiert der
 * Task {@link ProgressReporter}, sodass der Service Fortschritt/Meldungen
 * direkt an die GUI melden kann.
 * </p>
 *
 * <h3>Änderungen (vorher → neu)</h3>
 * <ul>
 *   <li><b>Konstruktor</b>: vorher {@code GenerateOpListeTask(OPListenService, String)}
 *       → <b>neu</b> {@code GenerateOpListeTask(OPListenService, String, String, ExportFormat)}</li>
 *   <li><b>Progress</b>: Implementiert jetzt {@code ProgressReporter}, damit der Service
 *       {@code updateMessage}/{@code updateProgress} auf den Task routen kann.</li>
 *   <li><b>Service-Aufruf</b>: vorher
 *       {@code service.erstelleOPListeFuerKunde(kundeName)}
 *       → <b>neu</b>
 *       {@code service.erstelleOPListeFuerKunde(kundeName, language, format, this)}</li>
 * </ul>
 *
 * @author Stephane
 * @since 2025-09-12
 */
public class GenerateOpListeTask extends Task<ExecutionResult> implements ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(GenerateOpListeTask.class);

    private final OPListenService service;
    private final String kundeName;
    private final String language;          // "DE" oder "EN"
    private final ExportFormat format;      // XLSX oder PDF

    /**
     * Erzeugt einen neuen Task für die OP-Listenerstellung eines Kunden.
     *
     * @param service   Orchestrierender Dienst.
     * @param kundeName Ausgewählter Kunde (z. B. "Hartrodt", "Gateway", ...).
     * @param language  Sprache für den Export ("DE" oder "EN").
     * @param format    Zielformat (z. B. {@link ExportFormat#XLSX}).
     */
    public GenerateOpListeTask(OPListenService service, String kundeName, String language, ExportFormat format) {
        this.service = service;
        this.kundeName = kundeName;
        this.language = language;
        this.format = format;
    }

    @Override
    protected ExecutionResult call() throws Exception {
        try {
            updateMessage("Starte die Erstellung der OP-Liste für " + kundeName + " …");
            // Delegation an den Service; der Service meldet Fortschritt/Meldungen über 'this'
            ExecutionResult result =
                    service.erstelleOPListeFuerKunde(kundeName, language, format, this);

            // Abschlussmeldung in den Task spiegeln
            updateMessage(result.getMessage());
            if (result.isSuccess()) {
                updateProgress(100, 100);
            }
            return result;

        } catch (Exception ex) {
            log.error("Schwerer Fehler im GenerateOpListeTask für Kunde: {}", kundeName, ex);
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
