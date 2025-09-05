package service.impl;

import file.handler.FileHandler;
import file.handler.FileHandlerFactory;
import file.handler.PdfFileHandler;
import file.handler.XlsxFileHandler;
import file.pivot.PivotProcessor;
import model.PivotConfig;
import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.FileService;
import util.FileUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation des FileService - Erweitert um Pivot und Logging.
 * Schreibt und liest Dateien via FileHandler, protokolliert mit SLF4J.
 *
 * @author Stephane
 * @since 15/07/2025
 */
public class FileServiceImpl implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    private final PivotProcessor pivotProcessor;

    /**
     * Konstruktor ohne Abhängigkeiten.
     */
    public FileServiceImpl() {
        this.pivotProcessor = new PivotProcessor();
    }

    @Override
    public List<RowData> readFile(String filePath) {
        FileHandler handler = FileHandlerFactory.getHandler(filePath);
        List<RowData> data = handler.read(filePath);
        logger.info("📖 Datei gelesen: {} ({} Zeilen)", filePath, data.size());
        return data;
    }

    @Override
    public void writeFile(List<RowData> data, String outputPath, ExportFormat format) {
        FileHandler handler = FileHandlerFactory.getHandler(format);
        List<String> headers = extractHeaders(data);
        handler.write(data, headers, outputPath);
        logger.info("✍️ Datei geschrieben: {} (Format: {})", outputPath, format);
    }


    @Override
    public void writeFileWithHeaders(List<RowData> data, List<String> headers, String outputPath, ExportFormat format) {
        FileHandler handler = FileHandlerFactory.getHandler(format);

        if (handler == null) {
            throw new IllegalArgumentException("Unsupported export format: " + format);
        }

        if (headers.stream().anyMatch(h -> h.contains("Rg-NR")) || headers.stream().anyMatch(h -> h.contains("Invoice No."))) {
            if (handler instanceof XlsxFileHandler) {
                ((XlsxFileHandler) handler).writeOpList(data, headers, outputPath);
            } else if (handler instanceof PdfFileHandler) {
                ((PdfFileHandler) handler).writeOpList(data, headers, outputPath);
            }
        } else {
            handler.write(data, headers, outputPath);
        }

        logger.info("📝 Datei mit Headern geschrieben: {} ({} Header)", outputPath, headers.size());
    }

    @Override
    public void writeFileWithPivot(List<RowData> data, PivotConfig config, String outputPath, ExportFormat format) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Daten dürfen nicht leer sein");
        }
        if (config == null) {
            throw new IllegalArgumentException("PivotConfig darf nicht null sein");
        }
        if (!pivotProcessor.canPivot(data, config)) {
            throw new IllegalArgumentException("Pivot-Transformation nicht möglich");
        }

        logger.info("🔄 Pivot-Transformation gestartet: {}", outputPath);
        List<RowData> pivotData = pivotProcessor.transform(data, config);
        List<String> pivotHeaders = pivotProcessor.generateHeaders(data, config);

        writeFileWithHeaders(pivotData, pivotHeaders, outputPath, format);
        logger.info("✅ Pivot-Export abgeschlossen: {} ({} Zeilen)", outputPath, pivotData.size());
    }

    @Override
    public boolean isValidFile(String filePath) {
        boolean valid = FileUtil.isValidFile(filePath);
        logger.debug("🔍 Validiere Datei {}: {}", filePath, valid);
        return valid;
    }

    @Override
    public ExportFormat detectFormat(String filePath) {
        ExportFormat fmt = FileUtil.detectFormat(filePath);
        logger.debug("🔎 Erkanntes Format für {}: {}", filePath, fmt);
        return fmt;
    }

    /**
     * Extrahiert Header aus der ersten Zeile der Daten.
     */
    private List<String> extractHeaders(List<RowData> data) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(data.get(0).getValues().keySet());
    }
}
