package file.extrator.versicherung;

import file.extrator.DataExtractor;
import file.extrator.protocol.ProtocolAware;
import file.extrator.protocol.ProtocolReport;
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

public class VersicherungsExtractor implements DataExtractor<VersicherungsData>, ProtocolAware {

    private static final Logger logger = LoggerFactory.getLogger(VersicherungsExtractor.class);

    private static final Pattern VSN_FILENAME = Pattern.compile("(\\d+)_.*\\.pdf", Pattern.CASE_INSENSITIVE);
    private static final Pattern VSN_CONTENT = Pattern.compile("Versicherungsschein Nr\\.?:\\s*(\\S+)");

    private final PdfReader pdfReader;

    // --- NEW: rapport de protocole, un par appel extractData() ---
    private ProtocolReport protocol;

    public VersicherungsExtractor() {
        this.pdfReader = new PdfReader();
    }

    @Override
    public List<VersicherungsData> extractData(String filePath) {
        // Démarrer le protocole pour CE fichier et CE type d’extracteur
        final String extractorType = getSupportedDocumentType(); // ou: getClass().getSimpleName()
        protocol = ProtocolReport.start(filePath, extractorType);

        logger.info("🔍 Smarte Extraktion starten: {}", filePath);
        List<VersicherungsData> results = new ArrayList<>();

        try {
            // 1) VSN
            String vsn = extractVSN(filePath);
            if (vsn == null) {
                logger.warn("❌ Keine VSN gefunden in: {}", filePath);
                protocol.missing("VSN_MISSING", "Keine VSN im Dateinamen oder PDF-Inhalt gefunden.", null,
                        java.util.Map.of("file", new File(filePath).getName()));
                return results;
            } else {
                protocol.info("VSN_FOUND", "VSN gefunden.", null,
                        java.util.Map.of("vsn", vsn));
            }

            // 2) Volltext PDF
            String fullText = extractFullPdfText(filePath);
            protocol.info("PDF_TEXT_LEN", "PDF-Text extrahiert.",
                    null, java.util.Map.of("length", String.valueOf(fullText == null ? 0 : fullText.length())));

            // 3) Datensatz aufbauen (nur VSN + Text)
            VersicherungsData extractedData = new VersicherungsData();
            extractedData.setVersicherungsscheinNr(vsn);
            extractedData.setPdfText(fullText);

            results.add(extractedData);
            logger.info("✅ VSN extrahiert: {} - Text-Länge: {} Zeichen", vsn, fullText.length());

        } catch (Exception e) {
            logger.error("❌ Fehler bei intelligenter Extraktion: {}", filePath, e);
            protocol.warn("EXTRACT_EXCEPTION", "Fehler bei der Extraktion.",
                    null, java.util.Map.of("exception", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
        } finally {
            // Finir le protocole pour ce fichier
            protocol.finish();
            // Ici, tu peux décider d’exporter ou pas (selon stats)
            // if (protocol.hasWarningsOrCorrectionsOrMissing()) {
            //     ProtocolExporter.export(protocol, outputDir, preferXlsx);
            // }
            logger.info("🧾 Protokoll: total={}, warn={}, corr={}, missing={}, symbol={}",
                    protocol.getTotalCount(), protocol.getWarnCount(), protocol.getCorrectionCount(),
                    protocol.getMissingCount(), protocol.getSymbolCount());
        }

        return results;
    }

    @Override
    public boolean canExtract(File file) {
        if (!file.exists() || !file.canRead()) return false;
        String name = file.getName().toLowerCase();
        return name.contains("versicherungsbestätigung")
                || name.contains("versicherung")
                || VSN_FILENAME.matcher(file.getName()).matches();
    }

    @Override
    public String getSupportedDocumentType() {
        return "Versicherungsbestätigung (Intelligent)";
    }

    // ==================== PRIVATE HILFSMETHODEN ====================

    private String extractVSN(String filePath) {
        // Dateiname
        String vsn = extractVSNFromFilename(filePath);
        if (vsn != null) {
            protocol.info("VSN_FROM_FILENAME", "VSN aus Dateiname extrahiert.",
                    null, java.util.Map.of("vsn", vsn));
            return vsn;
        }

        // Inhalt
        try {
            List<RowData> pdfContent = pdfReader.read(filePath);
            String fullText = extractFullText(pdfContent);
            vsn = extractVSNFromContent(fullText);
            if (vsn != null) {
                protocol.info("VSN_FROM_CONTENT", "VSN aus PDF-Inhalt extrahiert.",
                        null, java.util.Map.of("vsn", vsn));
            }
            return vsn;
        } catch (Exception e) {
            protocol.warn("VSN_READ_ERROR", "Fehler beim Lesen der PDF (VSN-Extraktion).",
                    null, java.util.Map.of("exception", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
            return null;
        }
    }

    private String extractFullPdfText(String filePath) {
        try {
            List<RowData> pdfContent = pdfReader.read(filePath);
            return extractFullText(pdfContent);
        } catch (Exception e) {
            logger.error("❌ Fehler beim Extrahieren des PDF-Textes: {}", filePath, e);
            protocol.warn("PDF_TEXT_ERROR", "Fehler beim Extrahieren des PDF-Textes.",
                    null, java.util.Map.of("exception", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
            return "";
        }
    }

    private String extractVSNFromFilename(String filePath) {
        String fileName = new File(filePath).getName();
        Matcher matcher = VSN_FILENAME.matcher(fileName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractVSNFromContent(String text) {
        Matcher matcher = VSN_CONTENT.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractFullText(List<RowData> pdfContent) {
        StringBuilder fullText = new StringBuilder();
        for (RowData row : pdfContent) {
            String content = row.getValues().get("Content");
            if (content != null) fullText.append(content).append("\n");
        }
        return fullText.toString();
    }

    @Override
    public ProtocolReport getProtocolReport() {
        return protocol;
    }
}
