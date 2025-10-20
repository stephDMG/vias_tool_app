package service.pdfextraction;

import file.extrator.DataExtractor;
import file.extrator.protocol.ProtocolAware;
import file.extrator.protocol.ProtocolExporter;
import file.extrator.protocol.ProtocolReport;
import file.extrator.schadenregelierung.SchadenregulierungExtractor;
import file.extrator.versicherung.VersicherungsExtractor;
import model.RowData;
import model.SchadenregulierungData;
import model.VersicherungsData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.FileService;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extraktions- und Export-Service, der mehrere Dokumenttypen verarbeiten kann.
 * Er fungiert als Dispatcher, um den korrekten Extraktionsprozess
 * basierend auf der bereitgestellten PDF-Datei auszuwählen.
 *
 * @author Stephane Dongmo
 * @since 17/07/2025 (Aktualisiert am 30/09/2025)
 */
public class ExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionService.class);

    private final FileService fileService;
    private final ValidationService validationService;
    private final List<DataExtractor<?>> availableExtractors;


    private final ProtocolExporter protocolExporter;



    /**
     * Hauptprozess für Extraktion und Export.
     * Findet den passenden Extraktor, führt den entsprechenden Workflow aus und
     * exportiert die resultierenden Daten.
     * @return True, wenn ein Export durchgeführt wurde, sonst False.
     */
    public boolean extractAndExport(String pdfPath, String outputPath, ExportFormat format) {
        logger.info("🚀 Extraktionsprozess gestartet für: {} → {}", pdfPath, outputPath);

        try {
            // 1. Passenden Extraktor für die Datei finden
            DataExtractor<?> extractor = findExtractorFor(pdfPath);
            if (extractor == null) {
                throw new Exception("Kein passender Extraktor für die Datei gefunden: " + pdfPath);
            }
            logger.info("Extraktor '{}' ausgewählt.", extractor.getSupportedDocumentType());

            // 2. Daten extrahieren. Das Ergebnis ist eine generische Liste.
            List<?> extractedData = extractor.extractData(pdfPath);

            if (extractedData == null || extractedData.isEmpty()) {
                logger.warn("⚠️ Keine exportierbaren Daten in der Datei gefunden. Export für '{}' wird übersprungen.", pdfPath);
                maybeExportProtocol(extractor, outputPath, format);
                return false;
            }

            // 3. Dispatching basierend auf dem Typ der extrahierten Daten
            Object firstItem = extractedData.get(0);

            if (firstItem instanceof VersicherungsData) {
                // WORKFLOW: Für Versicherungsbestätigungen (mit DB-Validierung)
                logger.info("Führe den intelligenten Validierungs-Workflow aus...");
                List<VersicherungsData> dataToValidate = (List<VersicherungsData>) extractedData;
                List<VersicherungsData> validatedData = validateVersicherungsData(dataToValidate);
                exportVersicherungsData(validatedData, outputPath, format);
                maybeExportProtocol(extractor, outputPath, format);

            } else if (firstItem instanceof SchadenregulierungData) {
                // WORKFLOW: Für Schadenregulierungen (Direktexport)
                logger.info("Führe den direkten Export-Workflow für Schadenregulierungen aus...");
                List<SchadenregulierungData> dataToExport = (List<SchadenregulierungData>) extractedData;
                exportSchadenregulierungData(dataToExport, outputPath, format);
                maybeExportProtocol(extractor, outputPath, format);

            } else {
                throw new IllegalStateException("Unbekannter Datentyp kann nicht exportiert werden: " + firstItem.getClass().getName());
            }

            logger.info("✅ Extraktion und Export erfolgreich abgeschlossen: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("❌ Fehler während des Extraktionsprozesses.", e);
            throw new RuntimeException("Extraktion fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    // --- SPEZIFISCHER WORKFLOW FÜR 'VERSICHERUNG' ---
    private List<VersicherungsData> validateVersicherungsData(List<VersicherungsData> data) throws Exception {
        List<ValidationService.SmartValidationResult> validationResults = validationService.validateSmart(data);
        logSmartValidationResults(validationResults);

        List<VersicherungsData> validatedData = validationResults.stream()
                .filter(ValidationService.SmartValidationResult::isValidationSuccess)
                .map(ValidationService.SmartValidationResult::getValidatedData)
                .collect(Collectors.toList());

        if (validatedData.isEmpty()) {
            String error = validationResults.stream().map(ValidationService.SmartValidationResult::getErrorMessage)
                    .findFirst().orElse("Unbekannter Validierungsfehler.");
            throw new Exception("Validierung fehlgeschlagen: " + error);
        }
        return validatedData;
    }

    private void exportVersicherungsData(List<VersicherungsData> data, String outputPath, ExportFormat format) {
        List<RowData> rows = data.stream().map(VersicherungsData::toRowData).collect(Collectors.toList());
        List<String> headers = VersicherungsData.getExportHeaders();
        fileService.writeFileWithHeaders(rows, headers, outputPath, format);
        logger.info("📝 Export für Versicherungsdaten abgeschlossen: {} Zeilen.", rows.size());
    }


    // --- NEUER WORKFLOW FÜR 'SCHADENREGULIERUNG' ---
    private void exportSchadenregulierungData(List<SchadenregulierungData> data, String outputPath, ExportFormat format) {
        List<RowData> rows = data.stream().map(SchadenregulierungData::toRowData).collect(Collectors.toList());
        List<String> headers = SchadenregulierungData.getExportHeaders();
        fileService.writeFileWithHeaders(rows, headers, outputPath, format);
        logger.info("📝 Export für Schadenregulierungen abgeschlossen: {} Zeilen.", rows.size());
    }


    // --- HILFSMETHODEN ---

    /**
     * Findet den ersten verfügbaren Extraktor, der die Datei verarbeiten kann.
     */
    private DataExtractor<?> findExtractorFor(String filePath) {
        File file = new File(filePath);
        return availableExtractors.stream()
                .filter(extractor -> extractor.canExtract(file))
                .findFirst()
                .orElse(null);
    }

    /**
     * Protokolliert die Ergebnisse des intelligenten Validierungsprozesses.
     */
    private void logSmartValidationResults(List<ValidationService.SmartValidationResult> results) {
        // (Code unverändert gegenüber Ihrem Original)
        long successCount = results.stream().filter(ValidationService.SmartValidationResult::isValidationSuccess).count();
        logger.info("--- Intelligente Validierungsergebnisse ({} erfolgreich) ---", successCount);
        for (ValidationService.SmartValidationResult result : results) {
            if (!result.isValidationSuccess()) {
                logger.warn("❌ VSN {} - Validierung fehlgeschlagen: {}", result.getVsn(), result.getErrorMessage());
            }
        }
        logger.info("---------------------------------------------");
    }

    private String buildProtocolOutputPath(String dataOutputPath, boolean preferXlsx) {
        try {
            Path p = Paths.get(dataOutputPath);
            String file = p.getFileName().toString();
            int dot = file.lastIndexOf('.');
            String base = (dot > 0) ? file.substring(0, dot) : file;
            return p.getParent().resolve(base + (preferXlsx ? ".protocol.xlsx" : ".protocol.csv")).toString();
        } catch (Exception e) {
            return dataOutputPath + (preferXlsx ? ".protocol.xlsx" : ".protocol.csv");
        }
    }

    public ExtractionService(FileService fileService) {
        this.fileService = fileService;
        this.validationService = new ValidationService();
        this.availableExtractors = List.of(
                new VersicherungsExtractor(),
                new SchadenregulierungExtractor()
        );
        this.protocolExporter = new ProtocolExporter(fileService);
    }

    private void maybeExportProtocol(Object extractor, String dataOutputPath, ExportFormat format) {
        if (!(extractor instanceof ProtocolAware pa)) return;
        ProtocolReport report = pa.getProtocolReport();
        if (report == null) return;

        logger.info("🧾 Protokoll: total={}, warn={}, corr={}, missing={}, symbol={}",
                report.getTotalCount(),
                report.getWarnCount(),
                report.getCorrectionCount(),
                report.getMissingCount(),
                report.getSymbolCount());

        // si tout est OK → pas de fichier, on garde seulement les logs
        if (!report.hasWarningsOrCorrectionsOrMissing()) return;

        boolean preferXlsx = (format == ExportFormat.XLSX);
        String protocolPath = buildProtocolOutputPath(dataOutputPath, preferXlsx);

        // ⛳️ Skip si déjà généré par l’autre export (XLSX/CSV)
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(protocolPath);
            if (java.nio.file.Files.exists(p)) {
                logger.info("ℹ️ Protokoll-Datei existiert bereits, wird nicht erneut erzeugt: {}", protocolPath);
                return;
            }
        } catch (Exception ignore) { /* best-effort */ }

        try {
            protocolExporter.export(report, protocolPath, preferXlsx ? ExportFormat.XLSX : ExportFormat.CSV);
            logger.info("📄 Protokoll-Datei geschrieben: {}", protocolPath);
        } catch (Exception ex) {
            logger.warn("⚠️ Konnte Protokoll nicht schreiben: {}", ex.getMessage(), ex);
        }
    }
}