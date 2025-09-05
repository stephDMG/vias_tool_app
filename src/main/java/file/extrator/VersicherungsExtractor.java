package file.extrator;


import file.reader.PdfReader;
import model.RowData;
import model.VersicherungsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligenter Extraktor für Versicherungsbestätigungen.
 * Extrahiert nur die VSN und den kompletten PDF-Text.
 * Die eigentliche Datenextraktion erfolgt über die Datenbank.
 *
 * @author Stephane Dongmo
 * @since 17/07/2025
 */
public class VersicherungsExtractor implements DataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(VersicherungsExtractor.class);

    // Nur noch DIESE Patterns benötigt!
    private static final Pattern VSN_FILENAME = Pattern.compile("(\\d+)_.*\\.pdf", Pattern.CASE_INSENSITIVE);
    private static final Pattern VSN_CONTENT = Pattern.compile("Versicherungsschein Nr\\.?:\\s*(\\S+)");

    private final PdfReader pdfReader;

    public VersicherungsExtractor() {
        this.pdfReader = new PdfReader();
    }

    @Override
    public List<VersicherungsData> extractData(String filePath) {
        logger.info("🔍 Smarte Extraktion starten: {}", filePath);
        List<VersicherungsData> results = new ArrayList<>();

        try {
            // 1. Nur VSN extrahieren
            String vsn = extractVSN(filePath);
            if (vsn == null) {
                logger.warn("❌ Keine VSN gefunden in: {}", filePath);
                return results;
            }

            // 2. Kompletten PDF-Text extrahieren
            String fullText = extractFullPdfText(filePath);

            // 3. Objekt mit VSN und Text für Validierung erstellen
            VersicherungsData extractedData = new VersicherungsData();
            // Setze nur die VSN und den kompletten Text
            extractedData.setVersicherungsscheinNr(vsn);
            // Speichere PDF-Text temporär (wird in ValidationService verwendet)
            extractedData.setPdfText(fullText);

            results.add(extractedData);
            logger.info("✅ VSN extrahiert: {} - Text-Länge: {} Zeichen", vsn, fullText.length());

        } catch (Exception e) {
            logger.error("❌ Fehler bei intelligenter Extraktion: {}", filePath, e);
        }

        return results;
    }

    @Override
    public boolean canExtract(File file) {
        if (!file.exists() || !file.canRead()) {
            return false;
        }

        String name = file.getName().toLowerCase();
        return name.contains("versicherungsbestätigung") ||
                name.contains("versicherung") ||
                VSN_FILENAME.matcher(file.getName()).matches();
    }

    @Override
    public String getSupportedDocumentType() {
        return "Versicherungsbestätigung (Intelligent)";
    }

    // ==================== PRIVATE HILFSMETHODEN ====================

    /**
     * Extrahiert die VSN aus Dateiname oder PDF-Inhalt.
     *
     * @param filePath Pfad zur PDF-Datei
     * @return VSN oder null wenn nicht gefunden
     */
    private String extractVSN(String filePath) {
        // Zuerst aus Dateiname versuchen
        String vsn = extractVSNFromFilename(filePath);
        if (vsn != null) {
            logger.debug("📄 VSN aus Dateiname extrahiert: {}", vsn);
            return vsn;
        }

        // Falls nicht im Dateiname, aus PDF-Inhalt extrahieren
        try {
            List<RowData> pdfContent = pdfReader.read(filePath);
            String fullText = extractFullText(pdfContent);
            vsn = extractVSNFromContent(fullText);
            if (vsn != null) {
                logger.debug("📝 VSN aus PDF-Inhalt extrahiert: {}", vsn);
            }
            return vsn;
        } catch (Exception e) {
            logger.error("❌ Fehler beim Lesen der PDF für VSN-Extraktion: {}", filePath, e);
            return null;
        }
    }

    /**
     * Extrahiert den kompletten Text aus der PDF-Datei.
     *
     * @param filePath Pfad zur PDF-Datei
     * @return Kompletter PDF-Text
     */
    private String extractFullPdfText(String filePath) {
        try {
            List<RowData> pdfContent = pdfReader.read(filePath);
            return extractFullText(pdfContent);
        } catch (Exception e) {
            logger.error("❌ Fehler beim Extrahieren des PDF-Textes: {}", filePath, e);
            return "";
        }
    }

    /**
     * VSN aus Dateiname extrahieren.
     */
    private String extractVSNFromFilename(String filePath) {
        String fileName = new File(filePath).getName();
        Matcher matcher = VSN_FILENAME.matcher(fileName);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * VSN aus PDF-Inhalt extrahieren.
     */
    private String extractVSNFromContent(String text) {
        Matcher matcher = VSN_CONTENT.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Kompletten Text aus PDF-RowData zusammensetzen.
     */
    private String extractFullText(List<RowData> pdfContent) {
        StringBuilder fullText = new StringBuilder();
        for (RowData row : pdfContent) {
            String content = row.getValues().get("Content");
            if (content != null) {
                fullText.append(content).append("\n");
            }
        }
        return fullText.toString();
    }
}