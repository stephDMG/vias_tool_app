package file.handler;

import model.enums.ExportFormat;

import java.util.HashMap;
import java.util.Map;

/**
 * Fabrik (Factory) zur Bereitstellung von {@link FileHandler}-Instanzen.
 * Diese Klasse ermöglicht den einfachen Zugriff auf den passenden FileHandler
 * basierend auf dem gewünschten Exportformat oder dem Dateipfad.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class FileHandlerFactory {

    /**
     * Eine statische Map, die {@link ExportFormat}-Typen den entsprechenden
     * {@link FileHandler}-Implementierungen zuordnet.
     * Dies ermöglicht eine schnelle Abfrage des passenden Handlers.
     */
    private static final Map<ExportFormat, FileHandler> handlers = new HashMap<>();

    static {
        handlers.put(ExportFormat.CSV, new CsvFileHandler());
        handlers.put(ExportFormat.XLSX, new XlsxFileHandler());
        handlers.put(ExportFormat.TXT, new TxtFileHandler());
        handlers.put(ExportFormat.PDF, new PdfFileHandler());
    }

    /**
     * Gibt den passenden {@link FileHandler} für das angegebene {@link ExportFormat} zurück.
     *
     * @param format Das gewünschte {@link ExportFormat}, für das ein Handler benötigt wird.
     * @return Die {@link FileHandler}-Instanz, die dem angegebenen Format entspricht.
     */
    public static FileHandler getHandler(ExportFormat format) {
        return handlers.get(format);
    }

    /**
     * Gibt den passenden {@link FileHandler} basierend auf dem Dateipfad zurück.
     * Die Methode durchsucht alle registrierten Handler und wählt den ersten aus,
     * der den gegebenen Dateipfad verarbeiten kann (bestimmt durch {@link FileHandler#canHandle(String)}).
     *
     * @param filePath Der Dateipfad, für den ein Handler benötigt wird.
     * @return Die {@link FileHandler}-Instanz, die den Dateipfad verarbeiten kann.
     * @throws RuntimeException Wenn kein geeigneter Handler für den Dateipfad gefunden wird.
     */
    public static FileHandler getHandler(String filePath) {
        return handlers.values().stream()
                .filter(h -> h.canHandle(filePath))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Kein Handler für: " + filePath));
    }
}