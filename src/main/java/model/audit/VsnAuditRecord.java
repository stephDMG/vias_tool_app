package model.audit;

import java.time.LocalDateTime;

/**
 * Audit-Eintrag für Dokumente im Kontext eines Schadenvorgangs.
 * Erfordert die VSN-Nummer (Schaden-Nummer) als Primärschlüssel für die Zuordnung.
 */
public class VsnAuditRecord extends AuditDocumentRecord {

    private final String vsnNummer;

    /**
     * Konstruktor für einen VSN (Schaden) Audit Record.
     *
     * @param vsnNummer Die VSN-Nummer des Schadens.
     * @param nachname Nachname des Sachbearbeiters.
     * @param vorname Vorname des Sachbearbeiters.
     * @param beschreibung Beschreibung des Vorgangs (z.B. "01) Korrespondenz").
     * @param parameter Der vollständige VIAS-Pfad-String.
     * @param extension Die Dateierweiterung.
     * @param betreff Der Betreff des Dokuments.
     * @param bezugsdatum Das Bezugsdatum.
     * @param uhrzeit Die Erstellungszeit.
     */
    public VsnAuditRecord(String vsnNummer, String nachname, String vorname, String beschreibung,
                          String parameter, String extension, String betreff,
                          LocalDateTime bezugsdatum, LocalDateTime uhrzeit, String dateiName) {

        super(nachname, vorname, beschreibung, parameter, extension, betreff, bezugsdatum, uhrzeit, dateiName);
        this.vsnNummer = vsnNummer;
    }

    /**
     * Liefert die VSN-Nummer (Schaden-Nummer), die als Audit-Schlüssel dient.
     * @return Die VSN-Nummer.
     */
    public String getVsnNummer() {
        return vsnNummer;
    }
}