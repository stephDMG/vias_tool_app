package model.op.kunde;

import service.op.repository.KundeRepository;

import java.util.List;
import java.util.Map;

/**
 * Definiert die gemeinsame Schnittstelle für alle Kunden-spezifischen Strategien.
 * Jede Implementierung dieser Schnittstelle kapselt die Logik, die für einen
 * bestimmten Kunden einzigartig ist, wie z. B. den Speicherpfad und
 * spezifische Datenverarbeitungsschritte.
 */
public interface IKundeStrategy {

    /**
     * Liefert den Namen des Kunden, der in der Datenbankabfrage (LIKE-Klausel)
     * verwendet wird.
     *
     * @return Der für die SQL-Abfrage zu verwendende Kundenname.
     */
    String getKundeNameForQuery();

    /**
     * Gibt den spezifischen Speicherpfad für die generierte OP-Liste des Kunden zurück.
     *
     * @return Der vollständige Pfad zum Speicherort.
     */
    String getSavePath();

    /**
     * Lädt die Kundendaten gruppiert nach Land und Policen.
     */
    Map<String, Map<String, List<Kunde>>> loadGroups(KundeRepository repository) throws Exception;


    /**
     * Erzeugt einen spezifischen Dateinamen für den Export.
     *
     * @param policeNr  Policen-Nr
     * @param land      Landname
     * @param ort       Ortname
     * @param extension Dateiendung (z.B. "xlsx" oder "pdf")
     * @return Vollständiger Dateiname
     */
    String buildFileName(String policeNr, String land, String ort, String extension);
}