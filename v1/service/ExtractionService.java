package service;

import file.extrator.DataExtractor;
import file.extrator.ValidationService;
import model.VersicherungsData;
import model.RowData;
import model.enums.ExportFormat;
import service.interfaces.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Hauptservice für die Extraktion und den Export von PDF-Daten.
 * Orchestriert den gesamten Prozess: Extraktion → Validierung → Export.
 *
 * @author Stephane Dongmo
 * @since 16/07/2025
 */
public class ExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionService.class);

    private final FileService fileService;
    private final ValidationService validationService;
    private final DataExtractor extractor;

    /**
     * Konstruktor für den ExtractionService.
     * Initialisiert den Service mit den notwendigen Abhängigkeiten.
     *
     * @param fileService Der {@link FileService}, der für Dateivorgänge zuständig ist.
     */
    public ExtractionService(FileService fileService) {
        this.fileService = fileService;
        this.validationService = new ValidationService();
        this.extractor = new VersicherungsExtractor();
    }

    /**
     * Führt den vollständigen Prozess der Datenextraktion aus einer PDF-Datei,
     * deren Validierung und des Exports in das angegebene Format durch.
     *
     * @param pdfPath    Der Pfad zur zu extrahierenden PDF-Datei.
     * @param outputPath Der Pfad, unter dem die extrahierten Daten gespeichert werden sollen.
     * @param format     Das {@link ExportFormat}, in dem die Daten exportiert werden sollen.
     * @throws RuntimeException Wenn während des Extraktions- oder Exportprozesses ein unerwarteter Fehler auftritt.
     */
    public void extractAndExport(String pdfPath, String outputPath, ExportFormat format) {
        logger.info("🚀 Vollständige Extraktion gestartet: {} → {}", pdfPath, outputPath);

        try {
            // 1. Datenextraktion aus der PDF-Datei
            List<VersicherungsData> data = extractor.extractData(pdfPath);

            if (data.isEmpty()) {
                logger.warn("⚠️ Keine Daten extrahiert aus: {}", pdfPath);
                return;
            }

            logger.info("📊 {} Daten extrahiert", data.size());

            // 2. Validierung der extrahierten Daten (optional, dient aber der Information)
            List<ValidationService.ValidationResult> validationResults = validationService.validate(data);
            logValidationResults(validationResults); // Ergebnisse der Validierung protokollieren

            // 3. Export der Daten über toRowData() und die bestehenden Writer
            exportData(data, outputPath, format);

            logger.info("✅ Vollständige Extraktion abgeschlossen: {}", outputPath);

        } catch (Exception e) {
            logger.error("❌ Fehler bei der vollständigen Extraktion", e);
            throw new RuntimeException("Extraktion fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /**
     * Führt eine einfache Extraktion und einen Export ohne vorherige Validierung durch.
     *
     * @param pdfPath    Der Pfad zur zu extrahierenden PDF-Datei.
     * @param outputPath Der Pfad, unter dem die extrahierten Daten gespeichert werden sollen.
     * @param format     Das {@link ExportFormat}, in dem die Daten exportiert werden sollen.
     */
    public void extractAndExportSimple(String pdfPath, String outputPath, ExportFormat format) {
        logger.info("🚀 Einfache Extraktion gestartet: {} → {}", pdfPath, outputPath);
        List<VersicherungsData> data = extractor.extractData(pdfPath);
        exportData(data, outputPath, format);
        logger.info("✅ Einfache Extraktion abgeschlossen: {}", outputPath);
    }

    /**
     * Exportiert die Liste der {@link VersicherungsData}-Objekte in die angegebene Datei
     * und das angegebene Format. Die Daten werden zuvor in {@link RowData}-Objekte umgewandelt.
     *
     * @param data       Die Liste der zu exportierenden {@link VersicherungsData}-Objekte.
     * @param outputPath Der Pfad zur Ausgabedatei.
     * @param format     Das {@link ExportFormat} der Ausgabedatei.
     */
    private void exportData(List<VersicherungsData> data, String outputPath, ExportFormat format) {
        // Konvertierung der VersicherungsData-Objekte in RowData-Objekte für den Export
        List<RowData> rows = data.stream()
                .map(VersicherungsData::toRowData)
                .collect(Collectors.toList());

        // Abrufen der Header für den Export aus dem VersicherungsData-Modell
        List<String> headers = VersicherungsData.getExportHeaders();

        // Export der Daten über den bestehenden FileService
        fileService.writeFileWithHeaders(rows, headers, outputPath, format);

        logger.info("📝 Export abgeschlossen: {} Zeilen → {}", rows.size(), outputPath);
    }

    /**
     * Protokolliert die Ergebnisse der Validierung.
     * Zeigt an, ob eine Validierung erfolgreich war, ob die VSN existiert
     * und ob es Abweichungen gab.
     *
     * @param results Eine Liste von {@link ValidationService.ValidationResult}-Objekten.
     */
    private void logValidationResults(List<ValidationService.ValidationResult> results) {
        logger.info("--- Validierungsergebnisse ---");
        for (ValidationService.ValidationResult result : results) {
            if (result.isValidationSuccess()) {
                String vsn = result.getExtractedData().getVersicherungsscheinNr();
                if (result.isVsnExists()) {
                    if (result.isFullMatch()) {
                        logger.info("✅ VSN {} validiert: perfekte Übereinstimmung", vsn);
                    } else {
                        logger.warn("⚠️ VSN {} validiert, aber Unterschiede: {}", vsn, result.getDifferences());
                    }
                } else {
                    logger.warn("❓ VSN {} nicht in der Datenbank gefunden", vsn);
                }
            } else {
                logger.error("❌ Validierungsfehler für Daten: {}", result.getExtractedData());
                // Hier könnten weitere Details zum Validierungsfehler protokolliert werden, falls vorhanden
            }
        }
        logger.info("-----------------------------");
    }
}