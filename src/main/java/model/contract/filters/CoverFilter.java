package model.contract.filters;

import java.time.LocalDate;
import java.util.List;

/**
 * CoverFilter
 *
 * Zentrale Filterklasse für Vertrags- und Angebotsauswertungen.
 * Enthält Standardfilter (Datum, Status, Makler, Volltext)
 * sowie erweiterte Filter (Bearbeitungsstand, Vertragsstatus, Gruppierung, Stornogrund usw.).
 */
public class CoverFilter {

    // Zeitliche Filter
    private String fromDate;
    private String toDate;
    private LocalDate abDate;
    private LocalDate bisDate;

    // Allgemeine Filter
    private String status;
    private String broker;
    private String textSearch;

    // Vertragsstatus (einzeln oder multi)
    private String contractStatus;
    private List<String> contractStatusList;

    // Bearbeitungsstand (multi-selektion)
    private List<String> bearbeitungsstandIds;

    // Modus / Kontext (z. B. "Angebote", "Verträge")
    private String mode;

    // Kündigungsfristverkürzung
    private LocalDate kuendigVerkDatum;
    private String kuendigVerkInitiator;

    // Storno / Beendigung (MODIFIZIERT: Multi-Selektion)
    private List<String> stornoGrundIds;

    // Dropdown-Gruppierung (Makler, Gesellschaft, Sparte, etc.) – multi-selektion
    private List<String> groupBy;


    // Versicherungsnehmer (optional für Suchfilter)
    private String insuredName;
    private String insuredCity;
    private String insuredNation;

    public CoverFilter() {}

    public CoverFilter(String fromDate, String toDate,
                       String status, String broker,
                       String textSearch) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.status = status;
        this.broker = broker;
        this.textSearch = textSearch;
    }

    // -------------------- Getter/Setter --------------------

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

    public LocalDate getAbDate() { return abDate; }
    public void setAbDate(LocalDate abDate) { this.abDate = abDate; }

    public LocalDate getBisDate() { return bisDate; }
    public void setBisDate(LocalDate bisDate) { this.bisDate = bisDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }

    public String getTextSearch() { return textSearch; }
    public void setTextSearch(String textSearch) { this.textSearch = textSearch; }

    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }

    public List<String> getContractStatusList() { return contractStatusList; }
    public void setContractStatusList(List<String> contractStatusList) { this.contractStatusList = contractStatusList; }

    public List<String> getBearbeitungsstandIds() { return bearbeitungsstandIds; }
    public void setBearbeitungsstandIds(List<String> bearbeitungsstandIds) { this.bearbeitungsstandIds = bearbeitungsstandIds; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDate getKuendigVerkDatum() { return kuendigVerkDatum; }
    public void setKuendigVerkDatum(LocalDate kuendigVerkDatum) { this.kuendigVerkDatum = kuendigVerkDatum; }

    public String getKuendigVerkInitiator() { return kuendigVerkInitiator; }
    public void setKuendigVerkInitiator(String kuendigVerkInitiator) { this.kuendigVerkInitiator = kuendigVerkInitiator; }

    // MODIFIZIERT: List<String> für multi-Selektion der Stornogründe
    public List<String> getStornoGrundIds() { return stornoGrundIds; }
    public void setStornoGrundIds(List<String> stornoGrundIds) { this.stornoGrundIds = stornoGrundIds; }

    // Dropdown-Gruppierung (Makler, Gesellschaft, Sparte, etc.) – multi-selektion
    public List<String> getGroupBy() { return groupBy; }
    public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }


    public String getInsuredName() { return insuredName; }
    public void setInsuredName(String insuredName) { this.insuredName = insuredName; }

    public String getInsuredCity() { return insuredCity; }
    public void setInsuredCity(String insuredCity) { this.insuredCity = insuredCity; }

    public String getInsuredNation() { return insuredNation; }
    public void setInsuredNation(String insuredNation) { this.insuredNation = insuredNation; }

    @Override
    public String toString() {
        return "CoverFilter{" +
                "fromDate='" + fromDate + '\'' +
                ", toDate='" + toDate + '\'' +
                ", abDate=" + abDate +
                ", bisDate=" + bisDate +
                ", status='" + status + '\'' +
                ", broker='" + broker + '\'' +
                ", textSearch='" + textSearch + '\'' +
                ", contractStatus='" + contractStatus + '\'' +
                ", contractStatusList=" + contractStatusList +
                ", bearbeitungsstandIds=" + bearbeitungsstandIds +
                ", mode='" + mode + '\'' +
                ", kuendigVerkDatum=" + kuendigVerkDatum +
                ", kuendigVerkInitiator='" + kuendigVerkInitiator + '\'' +
                ", stornoGrundIds=" + stornoGrundIds +
                ", groupBy=" + groupBy +
                ", insuredName='" + insuredName + '\'' +
                ", insuredCity='" + insuredCity + '\'' +
                ", insuredNation='" + insuredName + '\'' + // Korrektur: sollte insuredNation sein
                '}';
    }
}