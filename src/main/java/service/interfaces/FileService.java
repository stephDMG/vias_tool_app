package service.interfaces;

import model.RowData;
import model.PivotConfig;
import model.enums.ExportFormat;
import java.util.List;

/**
 * Service für Dateioperationen - Erweitert um Pivot.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public interface FileService {

    /**
     * Liest Datei und gibt RowData zurück.
     */
    List<RowData> readFile(String filePath);

    /**
     * Schreibt Daten in Datei.
     */
    void writeFile(List<RowData> data, String outputPath, ExportFormat format);

    /**
     * Schreibt Daten mit spezifischen Headern.
     */
    void writeFileWithHeaders(List<RowData> data, List<String> headers, String outputPath, ExportFormat format);


    /**
     * Schreibt Daten mit Pivot-Transformation.
     */
    void writeFileWithPivot(List<RowData> data, PivotConfig config, String outputPath, ExportFormat format);

    /**
     * Prüft ob Datei lesbar ist.
     */
    boolean isValidFile(String filePath);

    /**
     * Erkennt Format einer Datei.
     */
    ExportFormat detectFormat(String filePath);
}