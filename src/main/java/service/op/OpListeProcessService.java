package service.op;

import formatter.OpListeFormatter;
import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.DatabaseService;
import service.interfaces.FileService;
import service.interfaces.ProgressReporter;
import util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;


public class OpListeProcessService {

    private static final Logger log = LoggerFactory.getLogger(OpListeProcessService.class);

    private final FileService fileService;
    private final HartrodtRepository hartrodtRepository;
    private final OpRepository opRepository;
    private final OpListeFormatter formatter;

    private static final String EXPORT_BASE_PATH =
            "X:/FREIE ZONE/Blume, Sabine/Hartrodt/Master - FOS/w076 1412 ws - Transport/";

    public OpListeProcessService(DatabaseService databaseService, FileService fileService) {
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.formatter = new OpListeFormatter();
        this.hartrodtRepository = new HartrodtRepository(Objects.requireNonNull(databaseService, "databaseService"));
        this.opRepository = new OpRepository(databaseService, this.formatter);
    }

    public boolean monthlyExportFolderExists() {
        String currentMonth = new SimpleDateFormat("yyyy-MM").format(new Date());
        Path monthlyExportPath = Paths.get(EXPORT_BASE_PATH, "OP_List_" + currentMonth);
        log.info("Prüfe Monats-Export-Ordner: {}", monthlyExportPath);
        return Files.exists(monthlyExportPath);
    }

    /**
     * Führt den Exportprozess für die OP-Liste aus.
     *
     * @param language  Die Sprache für den Export (z.B. "de" für Deutsch).
     * @param filter    Der Filter für die zu exportierenden Daten.
     * @param format    Das gewünschte Exportformat (z.B. CSV, XLSX).
     * @param reporter  Der Reporter für Fortschritts-Updates.
     * @throws Exception Wenn ein Fehler während des Exports auftritt.
     */
    public void executeOpListExport(String language, String filter, ExportFormat format, ProgressReporter reporter) throws Exception {
        reporter.updateMessage("▶ Starte Exportprozess…");
        log.info("Starte Exportprozess...");
        reporter.updateProgress(0, 100);

        // --- Step 1: Lade Hartrodt-Policen ---
        reporter.updateMessage("1/5: Lade Policen (Hartrodt) nach Land…");
        Map<String, Map<String, List<Hartrodt>>> byLandByPolicy = hartrodtRepository.getGroupedByLandAndPolicy();
        if (byLandByPolicy.isEmpty()) {
            reporter.updateMessage("❌ Step 1: Keine Hartrodt-Policen gefunden.");
            log.warn("Keine Hartrodt-Policen gefunden.");
            return;
        }
        int totalPolicies = byLandByPolicy.values().stream().mapToInt(m -> m.keySet().size()).sum();
        reporter.updateMessage("✅ Step 1: " + totalPolicies + " Policen gefunden.");
        reporter.updateProgress(10, 100);

        // --- Step 2: Hauptliste laden & cachen ---
        List<RowData> mainFormattedList;
        if (opRepository.isCacheEmpty()) {
            reporter.updateMessage("2/5: Lade Hauptliste von Datenbank...");
            mainFormattedList = opRepository.loadAndCacheMainList(); // Charge le cache si vide
        } else {
            reporter.updateMessage("2/5: Verwende Hauptliste aus dem Cache.");
            mainFormattedList = opRepository.getMainCache(); // Récupère le cache existant
        }

        if (mainFormattedList.isEmpty()) {
            reporter.updateMessage("❌ Step 2: Hauptliste ist leer. Prozess beendet.");
            log.warn("Hauptliste ist leer.");
            return;
        }

        reporter.updateMessage("✅ Step 2: Hauptliste geladen (" + mainFormattedList.size() + " Zeilen).");
        reporter.updateProgress(20, 100);

        // --- Vorbereitung für Export ---
        String currentMonthFolder = "OP_List_" + new SimpleDateFormat("yyyy-MM").format(new Date());
        Path basePath = Paths.get(EXPORT_BASE_PATH);
        FileUtil.ensureDirectoryExists(basePath.toString());
        Path monthlyExportPath = basePath.resolve(currentMonthFolder);
        FileUtil.ensureDirectoryExists(monthlyExportPath.toString());
        log.info("Export-Ordner: {}", monthlyExportPath);



        int exportedCount = 0;
        int totalExportedFiles = 0;

        // --- Step 3-4-5: Schleife über Policen, filtern, anreichern, exportieren ---
        reporter.updateMessage("3/5: Beginne mit dem Exportprozess pro Police…");

        for (Map.Entry<String, Map<String, List<Hartrodt>>> landEntry : byLandByPolicy.entrySet()) {
            String land = landEntry.getKey();
            String safeLand = FileUtil.sanitizeFileName(land);
            Path landPath = monthlyExportPath.resolve(safeLand);
            //FileUtil.ensureDirectoryExists(landPath.toString());

            for (Map.Entry<String, List<Hartrodt>> policyEntry : landEntry.getValue().entrySet()) {
                String policyNr = policyEntry.getKey();
                String exampleOrt = policyEntry.getValue().get(0).getOrt();
                String exampleName = policyEntry.getValue().get(0).getName();

                reporter.updateMessage(String.format("Filtere Daten für Policy %s (%s)…", policyNr, land));

                List<RowData> filtered = opRepository.findByPolicyFromCache(policyNr);

                if (filtered.isEmpty()) {
                    reporter.updateMessage(String.format("⚠ Keine Daten für Policy %s (%s).", policyNr, land));
                    continue;
                }

                // Anreichern mit Zusatzfeldern
                for (RowData r : filtered) {
                    r.put("Land", (land != null && !land.isBlank()) ? land : "UNBEKANNT");

                    String cleanedName = OpListeFormatter.getCleanedName(exampleName);
                    r.put("Firma/Name", (cleanedName != null && !cleanedName.isBlank()) ? cleanedName : "UNBEKANNT");
                    r.put("Versicherungsnehmer", cleanedName);
                    r.put("Ort", (exampleOrt != null) ? exampleOrt : "");
                }

                List<RowData> formattedForExport = formatter.formatForExport(filtered, language);

                // --- Step 5: Exportieren ---
                String safePolicy = FileUtil.sanitizeFileName(policyNr);
                assert exampleOrt != null;
                String safeOrt = FileUtil.sanitizeFileName(exampleOrt);
                String fileName = String.format("%s_%s_%s.%s", safePolicy, safeLand, safeOrt, format.getExtension());
                //String fileName = String.format("%s_%s.%s", safePolicy, safeLand, format.getExtension());
                Path filePath = landPath.resolve(fileName);

                FileUtil.ensureDirectoryExists(filePath.toString());

                reporter.updateMessage(String.format("Exportiere %s zu %s", policyNr, filePath));
                List<String> headersToExport = OpListeFormatter.getHeadersForExport(filter, language);
                fileService.writeFileWithHeaders(formattedForExport, headersToExport, filePath.toString(), format);

                exportedCount++;
                reporter.updateProgress(20 + (exportedCount * 80L / totalPolicies), 100);
            }
            totalExportedFiles += landEntry.getValue().size();
        }

        reporter.updateMessage("🎉 Export abgeschlossen.");
        reporter.updateProgress(100, 100);
        log.info("Export-Prozess beendet: {} von {} Dateien.", exportedCount, totalExportedFiles);
    }
}