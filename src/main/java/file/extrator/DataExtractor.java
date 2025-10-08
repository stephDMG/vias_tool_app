package file.extrator;

import model.VersicherungsData;

import java.io.File;
import java.util.List;

/**
 * Schnittstelle für die Extraktion von Daten aus verschiedenen Dateiformaten.
 *
 * @author Stephane Dongmo
 * @since 16/07/2025
 */
public interface DataExtractor<T> {

    /**
     * Extrahiere Daten aus der angegebenen Datei.
     */
    List<T> extractData(String filePath);

    /**
     * Prüft, ob die Datei extrahiert werden kann.
     */
    boolean canExtract(File file);

    /**
     * Gibt den unterstützten Dokumenttyp zurück.
     */
    String getSupportedDocumentType();
}
