package model.contract.filters;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * CoverFilter
 *
 * Zentrale Filterklasse für Vertrags- und Angebotsauswertungen.
 * - API kanonisch für Version-Flag: getWithVersion()/setWithVersion(Boolean)
 * - Kompatibilität: isWithVersion()/setIsWithVersion(boolean)
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

    // Modus / Kontext
    private String mode;

    // Kündigungsfristverkürzung
    private LocalDate kuendigVerkDatum;
    private String kuendigVerkInitiator;

    // Storno / Beendigung (multi)
    private List<String> stornoGrundIds;

    // Dropdown-Gruppierung – multi
    private List<String> groupBy;

    // Version-Filter:
    // - 'withVersion' = kanonisch (Boolean, tri-state)
    // - 'isWithVersion' = Altkompatibilität (primitive boolean)
    private Boolean withVersion;      // kanonisch
    private boolean isWithVersion;    // kompat

    private String searchTerm;

    public CoverFilter() {}

    public CoverFilter(String fromDate, String toDate,
                       String status, String broker,
                       String textSearch,
                       boolean isWithVersion) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.status = status;
        this.broker = broker;
        this.textSearch = textSearch;
        setIsWithVersion(isWithVersion);
    }

    // -------------------- Getter/Setter --------------------

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public void setSearchTerm(String term) {
        this.searchTerm = (term == null || term.isBlank()) ? null : term;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

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

    public List<String> getStornoGrundIds() { return stornoGrundIds; }
    public void setStornoGrundIds(List<String> stornoGrundIds) { this.stornoGrundIds = stornoGrundIds; }

    public List<String> getGroupBy() { return groupBy; }
    public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }

    // ---- Version-Flag (API kanonisch pour le Repository) ----
    public void setWithVersion(Boolean v) {
        this.withVersion = v;
        this.isWithVersion = (v != null && v);
    }
    public Boolean getWithVersion() {
        // si non renseigné, retomber sur l’ancien booléen
        if (withVersion != null) return withVersion;
        return isWithVersion ? Boolean.TRUE : Boolean.FALSE;
    }

    // ---- Compatibilité (ce que le contrôleur appelait déjà) ----
    public void setIsWithVersion(boolean isWithVersion) {
        this.isWithVersion = isWithVersion;
        this.withVersion = isWithVersion;
    }
    public boolean isWithVersion() { return isWithVersion; }

    private String safeStr(String s) { return s == null ? "" : s; }

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
                ", withVersion=" + getWithVersion() +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                safeStr(fromDate), safeStr(toDate),
                abDate, bisDate,
                safeStr(status), safeStr(broker), safeStr(textSearch),
                safeStr(contractStatus), contractStatusList, bearbeitungsstandIds,
                safeStr(mode),
                kuendigVerkDatum, safeStr(kuendigVerkInitiator),
                stornoGrundIds, groupBy,
                getWithVersion()
        );
    }
}
