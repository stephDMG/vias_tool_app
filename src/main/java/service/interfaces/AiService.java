package service.interfaces;

/**
 * Service für KI-Funktionen (Future).
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public interface AiService {

    /**
     * Generiert SQL-Query aus Beschreibung.
     */
    String generateQuery(String description);

    /**
     * Optimiert bestehende Query.
     */
    String optimizeQuery(String originalQuery);
}