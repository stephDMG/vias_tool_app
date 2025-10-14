package service.audit.repository;

import model.audit.CoverAuditRecord;
import model.audit.VsnAuditRecord;

import java.util.List;

/**
 * Schnittstelle für den Datenzugriff auf VIAS zur Durchführung von Dokumenten-Audits.
 */
public interface AuditRepository {

    /**
     * Führt eine Abfrage für Dokumente basierend auf einer Liste von Policennummern durch.
     * @param policeNrs Eine Liste von Policennummern, für die Dokumente gesucht werden.
     * @return Eine Liste von CoverAuditRecord-Objekten.
     * @throws Exception bei Datenbankfehlern.
     */
    List<CoverAuditRecord> fetchCoverDocumentsByPolicyNr(List<String> policeNrs) throws Exception;

    /**
     * Führt eine Abfrage für Dokumente basierend auf einer Liste von VSN-Nummern (Schaden) durch.
     * @param vsnNrs Eine Liste von VSN-Nummern (Schaden-Nummern), für die Dokumente gesucht werden.
     * @return Eine Liste von VsnAuditRecord-Objekten.
     * @throws Exception bei Datenbankfehlern.
     */
    List<VsnAuditRecord> fetchSchadenDocumentsByVsnNr(List<String> vsnNrs) throws Exception;
}