package model.contract;

/**
 * CoverRecord
 * <p>
 * Repräsentiert eine Zeile in der COVER-Listenansicht.
 * Zusätzlich zu den "neutralen" Gettern (contractNumber, insuredName, ...)
 * stellt diese Klasse Alias-Getter bereit, die in UI/Queries erwartet werden
 * (z. B. getVsn(), getVersicherungsnehmer(), getMakler(), getBeginnDatum(), ...).
 * <p>
 * Dadurch bleibt der Controller/Formatter flexibel, ohne dass überall
 * Umbenennungen nötig sind.
 */
public class CoverRecord {

    private String contractNumber; // VSN / Versicherungsschein-Nr
    private String insuredName;    // Versicherungsnehmer
    private String brokerName;     // Makler
    private String status;         // Vertragsstand/Status (Text oder Code je nach Mapping)
    private String startDate;      // Beginn (YYYY-MM-DD)
    private String endDate;        // Ablauf (YYYY-MM-DD)

    // Versicherungsnehmer Details
    private String insuredFirstname;
    private String insuredCity;
    private String insuredNation;

    // Gruppierungen
    private String company;           // Gesellschaft
    private String coverType;         // Cover Art
    private String insuranceType;     // Versicherungsart
    private String insuranceBranch;   // Versicherungssparte
    private String participationForm; // Beteiligungsform
    private String employee;          // Sachbearbeiter

    // Vertragsstatus / Bearbeitungsstand
    private String contractStatus;    // OPZ
    private String processStatus;     // Bearbeitungsstand

    // Kündigungsfristverkürzung
    private String cancellationShortenDate;
    private String cancellationInitiator;

    // Storno / Beendigungsgrund
    private String stornoGrund;


    /**
     * Standard-Konstruktor.
     */
    public CoverRecord() {
    }

    /**
     * Voll-Konstruktor.
     */
    public CoverRecord(String contractNumber, String insuredName,
                       String brokerName, String status,
                       String startDate, String endDate) {
        this.contractNumber = contractNumber;
        this.insuredName = insuredName;
        this.brokerName = brokerName;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // ---------------- Basis-Getter/Setter (neutral) ----------------

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public String getInsuredName() {
        return insuredName;
    }

    public void setInsuredName(String insuredName) {
        this.insuredName = insuredName;
    }

    public String getBrokerName() {
        return brokerName;
    }

    public void setBrokerName(String brokerName) {
        this.brokerName = brokerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    // ---------------- Alias-Getter (kompatibel zu UI/VIAS) ----------------
    // VSN / Versicherungsschein-Nr
    public String getVsn() {
        return contractNumber;
    }

    public String getVersicherungsscheinNr() {
        return contractNumber;
    }

    // Versicherungsnehmer / LU_NAM
    public String getVersicherungsnehmer() {
        return insuredName;
    }

    public String getLuNam() {
        return insuredName;
    }

    public String getName() {
        return insuredName;
    } // fallback

    // Makler / MAKLERV / VMT (Name)
    public String getMakler() {
        return brokerName;
    }

    public String getMaklerv() {
        return brokerName;
    }

    public String getBroker() {
        return brokerName;
    }

    public String getBrokerDisplayName() {
        return brokerName;
    }

    // Vertragsstand / Status (Text)
    public String getVertragsstandText() {
        return status;
    }

    public String getStatusText() {
        return status;
    }

    public String getLuSta() {
        return status;
    }

    // Beginn / Ablauf (Datums-Aliasse)
    public String getBeginnDatum() {
        return startDate;
    }

    public String getLuBeg() {
        return startDate;
    }

    public String getBeginDate() {
        return startDate;
    }

    public String getAblaufDatum() {
        return endDate;
    }

    public String getLuAbl() {
        return endDate;
    }

    public String getEndDateText() {
        return endDate;
    }
}
