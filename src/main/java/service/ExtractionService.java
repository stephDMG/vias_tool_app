package service;


import file.extrator.DataExtractor;
import file.extrator.ValidationService;
import file.extrator.VersicherungsExtractor;
import model.RowData;
import model.VersicherungsData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.FileService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Intelligenter Extraktions-Service.
 * Verwendet die neue intelligente Validierung mit DB als Quelle der Wahrheit.
 *
 * @author Stephane Dongmo
 * @since 17/07/2025
 */
public class ExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionService.class);

    private final FileService fileService;
    private final ValidationService validationService;
    private final DataExtractor extractor;

    public ExtractionService(FileService fileService) {
        this.fileService = fileService;
        this.validationService = new ValidationService();
        this.extractor = new VersicherungsExtractor();
    }

    /**
     * Intelligenter Extraktions- und Export-Prozess.
     * Extrahiert VSN → Holt DB-Daten → Validiert intelligent → Exportiert DB-Daten.
     *
     * @param pdfPath    Pfad zur PDF-Datei
     * @param outputPath Zielpfad für Export
     * @param format     Export-Format
     * @throws RuntimeException wenn die Extraktion oder Validierung fehlschlägt.
     */
    public void extractAndExport(String pdfPath, String outputPath, ExportFormat format) {
        logger.info("🚀 Intelligente Extraktion gestartet: {} → {}", pdfPath, outputPath);

        try {
            // 1. VSN + PDF-Text extrahieren
            List<VersicherungsData> extractedData = extractor.extractData(pdfPath);

            if (extractedData.isEmpty()) {
                // GEÄNDERT: Wirft eine Exception, wenn keine VSN gefunden wird.
                throw new Exception("Keine VSN aus PDF extrahiert: " + pdfPath);
            }

            logger.info("📊 {} VSN extrahiert", extractedData.size());

            // 2. Intelligente Validierung (holt DB-Daten)
            List<ValidationService.SmartValidationResult> validationResults =
                    validationService.validateSmart(extractedData);

            // 3. Erfolgreiche Validierungen sammeln
            List<VersicherungsData> validatedData = validationResults.stream()
                    .filter(ValidationService.SmartValidationResult::isValidationSuccess)
                    .map(ValidationService.SmartValidationResult::getValidatedData)
                    .collect(Collectors.toList());

            if (validatedData.isEmpty()) {
                // GEÄNDERT: Wirft eine Exception, wenn die Validierung fehlschlägt.
                logger.warn("⚠️ Keine Daten erfolgreich validiert");

                // Versucht, die spezifische Fehlermeldung aus dem Validierungsergebnis zu erhalten.
                String detailedError = validationResults.stream()
                        .map(ValidationService.SmartValidationResult::getErrorMessage)
                        .filter(msg -> msg != null && !msg.isEmpty())
                        .findFirst()
                        .orElse("Unbekannter Validierungsfehler.");

                throw new Exception("Validierung fehlgeschlagen: " + detailedError);
            }

            // 4. Export der DB-Daten
            exportData(validatedData, outputPath, format);

            // 5. Ergebnisse loggen
            logSmartValidationResults(validationResults);

            logger.info("✅ Intelligente Extraktion abgeschlossen: {}", outputPath);

        } catch (Exception e) {
            logger.error("❌ Fehler bei intelligenter Extraktion", e);
            // Die Exception wird an den Aufrufer (den UI-Task) weitergegeben, damit die UI den Fehler anzeigen kann.
            throw new RuntimeException("Extraktion fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /**
     * Exportiert die validierten Datenbank-Daten.
     */
    private void exportData(List<VersicherungsData> data, String outputPath, ExportFormat format) {
        // Konvertierung über toRowData() (unverändert)
        List<RowData> rows = data.stream()
                .map(VersicherungsData::toRowData)
                .collect(Collectors.toList());

        // Headers (unverändert)
        List<String> headers = VersicherungsData.getExportHeaders();

        // Export über FileService (unverändert)
        fileService.writeFileWithHeaders(rows, headers, outputPath, format);

        logger.info("📝 Export abgeschlossen: {} Zeilen → {}", rows.size(), outputPath);
    }

    private void logSmartValidationResults(List<ValidationService.SmartValidationResult> results) {
        logger.info("--- Intelligente Validierungsergebnisse ---");
        for (ValidationService.SmartValidationResult result : results) {
            if (result.isValidationSuccess()) {
                logger.info("✅ VSN {} intelligent validiert - DB-Daten verwendet", result.getVsn());
            } else {
                logger.warn("❌ VSN {} - Validierung fehlgeschlagen: {}", result.getVsn(), result.getErrorMessage());
            }
        }
        logger.info("---------------------------------------------");
    }
}
