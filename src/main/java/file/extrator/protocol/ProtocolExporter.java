package file.extrator.protocol;

import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.FileService;

import java.util.List;
import java.util.Objects;


public class ProtocolExporter {

    private static final Logger log = LoggerFactory.getLogger(ProtocolExporter.class);

    private final FileService fileService;

    public ProtocolExporter(FileService fileService) {
        this.fileService = Objects.requireNonNull(fileService, "fileService");
    }


    public void exportIfNeeded(ProtocolReport report, String outputPath, ExportFormat format, boolean force) {
        if (report == null) {
            log.warn("ProtocolExporter: Bericht null → nichts tun");
            return;
        }
        if (!force && !report.hasWarningsOrCorrectionsOrMissing()) {
            log.info("ProtocolExporter: kein Problem erkannt → kein Export {}", outputPath);
            return;
        }
        export(report, outputPath, format);
    }


    public void export(ProtocolReport report, String outputPath, ExportFormat format) {
        final int HEADER_OFFSET = 1; // 1 ligne d’entête dans le CSV/XLSX
        List<RowData> rows = report.toRowDataList(HEADER_OFFSET);
        List<String> headers = ProtocolReport.defaultHeaders();
        fileService.writeFileWithHeaders(rows, headers, outputPath, format);
        log.info("✅ Exportiertes Protokoll: {} ({} Input, format={})", outputPath, rows.size(), format);
    }

}
