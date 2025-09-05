package file.handler;

import model.RowData;
import model.enums.ExportFormat;

import java.util.List;

/**
 * Interface für File Handler.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public interface FileHandler {

    List<RowData> read(String filePath);

    void write(List<RowData> data, List<String> headers, String outputPath);

    boolean canHandle(String filePath);

    ExportFormat getFormat();
}