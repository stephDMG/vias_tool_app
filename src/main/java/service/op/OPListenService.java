package service.op;

import formatter.op.OpListeFormatter;
import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.DatabaseService;
import service.interfaces.FileService;
import service.interfaces.ProgressReporter;
import service.op.kunde.IKundeStrategy;
import service.op.kunde.Kunde;
import service.op.kunde.KundeRepository;
import service.op.kunde.factory.KundeStrategyFactory;
import util.FileUtil;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestriert die Erstellung der OP-Listen nach Kundenstrategie (Strategy-Pattern).
 * <p>
 * Diese Klasse ersetzt den alten monolithischen OpListeProcessService und übernimmt:
 * <ul>
 *   <li>Auswahl der Kundenstrategie (Hartrodt, Gateway, Saco, FiveStar, ...)</li>
 *   <li>Laden der Policen gruppiert nach Land/VSN</li>
 *   <li>Laden/Cache der OP-Hauptliste (OpRepository)</li>
 *   <li>Filtern, Anreichern, Formatieren und Export pro Police</li>
 *   <li>Fortschritts- und Statusmeldungen via {@link ProgressReporter}</li>
 * </ul>
 * </p>
 */
public class OPListenService {

    private static final Logger log = LoggerFactory.getLogger(OPListenService.class);

    private final DatabaseService databaseService;
    private final FileService fileService;
    private final KundeRepository kundeRepository;
    private final KundeStrategyFactory strategyFactory;
    private final OpListeFormatter formatter;
    private final OpRepository opRepository;

    /**
     * @param databaseService DB-Service (idealerweise bereits dekoriert/cached)
     * @param fileService     Export/Datei-Service
     */
    public OPListenService(DatabaseService databaseService, FileService fileService) {
        this.databaseService = Objects.requireNonNull(databaseService);
        this.fileService = Objects.requireNonNull(fileService);
        this.formatter = new OpListeFormatter();
        this.kundeRepository = new KundeRepository(databaseService);
        this.strategyFactory = new KundeStrategyFactory();
        this.opRepository = new OpRepository(databaseService, this.formatter);
    }

    /**
     * Haupt-Entry-Point aus dem GUI/Task.
     *
     * @param kundeName Kundenwahl aus der GUI (z. B. "Hartrodt")
     * @param language  "DE" oder "EN"
     * @param format    Exportformat (XLSX/PDF)
     * @param reporter  Fortschritts-/Status-Callback (GUI)
     */
    public ExecutionResult erstelleOPListeFuerKunde(String kundeName,
                                                    String language,
                                                    ExportFormat format,
                                                    ProgressReporter reporter) {
        long started = System.currentTimeMillis();
        try {
            if (kundeName == null || kundeName.isBlank()) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE, "Kundenname darf nicht leer sein.");
            }

            IKundeStrategy strategy = strategyFactory.getStrategy(kundeName);
            if (strategy == null) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE, "Keine Strategie für Kunden \"" + kundeName + "\" gefunden.");
            }

            reporter.updateMessage("▶ Starte OP-Listen-Erstellung für Kunde: " + kundeName);
            log.info("▶ Starte OP-Listen-Erstellung für Kunde: {}", kundeName);

            // 1) Gruppen laden (Land → VSN → Liste<Kunde>)
            Map<String, Map<String, List<Kunde>>> grouped = strategy.loadGroups(kundeRepository);
            int policyCount = grouped.values().stream().mapToInt(Map::size).sum();
            if (policyCount == 0) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE, "Keine Policen für Kunde " + kundeName + " gefunden.");
            }
            reporter.updateMessage("✅ " + policyCount + " Policen für Kunde " + kundeName + " gefunden.");

            // 2) Hauptliste laden/aus Cache
            if (opRepository.isCacheEmpty()) {
                reporter.updateMessage("Lade OP-Hauptliste aus der Datenbank…");
                log.info("Lade OP-Hauptliste aus der DB…");
                opRepository.loadAndCacheMainList();
            } else {
                reporter.updateMessage("Verwende OP-Hauptliste aus Cache.");
                log.info("Verwende OP-Hauptliste aus Cache.");
            }

            // 3) Zielbasis
            String month = new java.text.SimpleDateFormat("yyyy-MM").format(new Date());
            String kunde = kundeName;

            final String folderName;

            if ("Hartrodt".equalsIgnoreCase(kunde)) {
                folderName = "OP_List_" + month;
            } else {
                folderName = "OP_List_" + kunde + "_" + month;
            }
            Path monthlyExportPath = java.nio.file.Paths
                    .get(strategy.getSavePath())
                    .resolve(folderName);

            util.FileUtil.ensureDirectoryExists(monthlyExportPath.toString());
            reporter.updateMessage("Export-Ordner: " + monthlyExportPath);

            // 4) Schleife & Export
            int exported = 0;
            int processed = 0;

            for (Map.Entry<String, Map<String, List<Kunde>>> landEntry : grouped.entrySet()) {
                final String land = landEntry.getKey();
                final String safeLand = FileUtil.sanitizeFileName(land);
                final Path landPath = monthlyExportPath.resolve(safeLand);
                // Land-Verzeichnis wird implizit beim ersten FileUtil.ensureDirectoryExists(file) angelegt

                for (Map.Entry<String, List<Kunde>> policyEntry : landEntry.getValue().entrySet()) {
                    processed++;
                    String vsn = policyEntry.getKey();
                    Kunde sample = policyEntry.getValue().get(0);
                    String ort = sample.getOrt();
                    String name = sample.getName();

                    reporter.updateMessage("Filtere Daten für Police " + vsn + " (" + land + ")…");
                    List<RowData> rows = opRepository.findByPolicyFromCache(vsn);
                    if (rows == null || rows.isEmpty()) {
                        reporter.updateMessage("⚠ Keine Daten für Police " + vsn + " (" + land + ").");
                        continue;
                    }

                    // Anreichern
                    for (RowData r : rows) {
                        r.put("Land", (land != null && !land.isBlank()) ? land : "UNBEKANNT");
                        String cleaned = OpListeFormatter.getCleanedName(name);
                        r.put("Firma/Name", (cleaned != null && !cleaned.isBlank()) ? cleaned : "UNBEKANNT");
                        r.put("Versicherungsnehmer", cleaned);
                        r.put("Ort", (ort != null) ? ort : "");
                    }

                    // Verdichten für Kundenexport
                    List<RowData> exportList = formatter.formatForExport(rows, language);

                    // Dateiname & Zielpfad
                    String fileName = strategy.buildFileName(vsn, land, ort, format.getExtension());
                    Path target = landPath.resolve(fileName);
                    FileUtil.ensureDirectoryExists(target.toString());

                    // Header & Export
                    List<String> headers = OpListeFormatter.getHeadersForExport("Kunde", language);
                    fileService.writeFileWithHeaders(exportList, headers, target.toString(), format);

                    exported++;
                    long pct = 10 + (processed * 80L / Math.max(policyCount, 1));
                    reporter.updateProgress(pct, 100);
                    reporter.updateMessage("Exportiert: " + fileName);
                }
            }

            long took = System.currentTimeMillis() - started;
            String msg = String.format("🎉 Export abgeschlossen. %d Dateien erzeugt. Dauer: %.1f s", exported, took / 1000.0);
            reporter.updateMessage(msg);
            reporter.updateProgress(100, 100);
            log.info(msg);

            return new ExecutionResult(ExecutionResult.Status.SUCCESS, msg, monthlyExportPath.toString(), exported);
        } catch (Exception ex) {
            log.error("Fehler im OPListenService", ex);
            return new ExecutionResult(ExecutionResult.Status.FAILURE, "Kritischer Fehler: " + ex.getMessage());
        }
    }
}
