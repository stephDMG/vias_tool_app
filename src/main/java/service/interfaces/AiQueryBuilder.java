package service.interfaces;

/**
 * Ein Interface, das einen "Experten" für eine bestimmte Domäne (z.B. Cover, Schaden) definiert.
 * Jeder Experte muss in der Lage sein zu entscheiden, ob er eine Anfrage bearbeiten kann,
 * und die entsprechende SQL-Abfrage zu generieren.
 */
public interface AiQueryBuilder {

    /**
     * Prüft, ob dieser Experte die gegebene Anfrage bearbeiten kann.
     * @param description Die vollständige, normalisierte Anfrage des Benutzers.
     * @return true, wenn der Experte zuständig ist, sonst false.
     */
    boolean canHandle(String description);

    /**
     * Generiert die SQL-Abfrage für die gegebene Anfrage.
     * Diese Methode sollte nur aufgerufen werden, wenn canHandle() true zurückgibt.
     * @param description Die vollständige, normalisierte Anfrage des Benutzers.
     * @return Die generierte SQL-Abfrage als String.
     */
    String generateQuery(String description);
}
