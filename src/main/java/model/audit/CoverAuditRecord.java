package model.audit;

import java.time.LocalDateTime;

/**
 * Audit-Eintrag für Dokumente im Kontext eines Versicherungsvertrags (Cover).
 * Erfordert die Policennummer als Primärschlüssel für die Zuordnung.
 */
public class CoverAuditRecord extends AuditDocumentRecord {

    private final String policeNr;

    /**
     * Konstruktor für einen Cover Audit Record.
     *
     * @param policeNr     Die Policennummer (LU_VSN) des Vertrags.
     * @param nachname     Nachname des Sachbearbeiters.
     * @param vorname      Vorname des Sachbearbeiters.
     * @param beschreibung Beschreibung des Vorgangs (z.B. "01) Schriftwechsel").
     * @param parameter    Der vollständige VIAS-Pfad-String.
     * @param extension    Die Dateierweiterung.
     * @param betreff      Der Betreff des Dokuments.
     * @param bezugsdatum  Das Bezugsdatum.
     * @param uhrzeit      Die Erstellungszeit.
     * @param dateiName    Der Dateiname.
     */
    public CoverAuditRecord(String policeNr, String nachname, String vorname, String beschreibung,
                            String parameter, String extension, String betreff,
                            LocalDateTime bezugsdatum, LocalDateTime uhrzeit, String dateiName) {

        super(nachname, vorname, beschreibung, parameter, extension, betreff, bezugsdatum, uhrzeit, dateiName);
        this.policeNr = policeNr;
    }

    /**
     * Liefert die Policennummer (Vertragsnummer), die als Audit-Schlüssel dient.
     * @return Die Police-Nummer.
     */
    public String getPoliceNr() {
        return policeNr;
    }
}