package model;

import java.util.Arrays;
import java.util.List;

/**
 * DTO (Data Transfer Object) für die aus den Schadenregulierungs-PDFs extrahierten Daten.
 * Repräsentiert die Daten einer einzelnen Tabellenzeile.
 *
 * @author Stephane Dongmo
 * @since 30/09/2025
 */
public class SchadenregulierungData {

    private String police;
    private String vn; // Versicherungsnehmer
    private String schaNr; // Schadensnummer
    private String schDatum; // Schadensdatum
    private String buchungstext;
    private String va;
    private String sa;
    private String anteilProzent;
    private String antKosten; // Anteilige Kosten
    private String antRegul;  // Anteilige Regulierung
    private String prozent100; // 100% Spalte

    /**
     * Gibt eine statische Liste von Headern in der gewünschten Reihenfolge für den Export zurück.
     *
     * @return Eine {@link List} von Strings, die die Export-Header darstellen.
     */
    public static List<String> getExportHeaders() {
        return Arrays.asList(
                "POLICE", "VN", "SCHA-NR.", "SCH-DATUM", "BUCHUNGSTEXT",
                "VA", "SA", "ANTEIL %", "ANT.KOSTEN", "ANT.REGUL.", "100%"
        );
    }

    // --- Getter und Setter ---

    public String getPolice() {
        return police;
    }

    public void setPolice(String police) {
        this.police = police;
    }

    public String getVn() {
        return vn;
    }

    public void setVn(String vn) {
        this.vn = vn;
    }

    public String getSchaNr() {
        return schaNr;
    }

    public void setSchaNr(String schaNr) {
        this.schaNr = schaNr;
    }

    public String getSchDatum() {
        return schDatum;
    }

    public void setSchDatum(String schDatum) {
        this.schDatum = schDatum;
    }

    public String getBuchungstext() {
        return buchungstext;
    }

    public void setBuchungstext(String buchungstext) {
        this.buchungstext = buchungstext;
    }

    public String getVa() {
        return va;
    }

    public void setVa(String va) {
        this.va = va;
    }

    public String getSa() {
        return sa;
    }

    public void setSa(String sa) {
        this.sa = sa;
    }

    public String getAnteilProzent() {
        return anteilProzent;
    }

    public void setAnteilProzent(String anteilProzent) {
        this.anteilProzent = anteilProzent;
    }

    public String getAntKosten() {
        return antKosten;
    }

    public void setAntKosten(String antKosten) {
        this.antKosten = antKosten;
    }

    public String getAntRegul() {
        return antRegul;
    }

    public void setAntRegul(String antRegul) {
        this.antRegul = antRegul;
    }

    public String getProzent100() {
        return prozent100;
    }

    public void setProzent100(String prozent100) {
        this.prozent100 = prozent100;
    }

    /**
     * Konvertiert die Schadenregulierungsdaten in ein {@link RowData}-Objekt für den Export.
     * Die Felder werden auf die entsprechenden Header-Namen abgebildet.
     *
     * @return Ein {@link RowData}-Objekt, das die Daten für den Export enthält.
     */
    public RowData toRowData() {
        RowData row = new RowData();

        List<String> headers = getExportHeaders();

        row.put(headers.get(0), this.police != null ? this.police : "");
        row.put(headers.get(1), this.vn != null ? this.vn : "");
        row.put(headers.get(2), this.schaNr != null ? this.schaNr : "");
        row.put(headers.get(3), this.schDatum != null ? this.schDatum : "");
        row.put(headers.get(4), this.buchungstext != null ? this.buchungstext : "");
        row.put(headers.get(5), this.va != null ? this.va : "");
        row.put(headers.get(6), this.sa != null ? this.sa : "");
        row.put(headers.get(7), this.anteilProzent != null ? this.anteilProzent : "");
        row.put(headers.get(8), this.antKosten != null ? this.antKosten : "");
        row.put(headers.get(9), this.antRegul != null ? this.antRegul : "");
        row.put(headers.get(10), this.prozent100 != null ? this.prozent100 : "");

        return row;
    }


    @Override
    public String toString() {
        return "SchadenregulierungData{" +
                "police='" + police + '\'' +
                ", schaNr='" + schaNr + '\'' +
                ", antRegul='" + antRegul + '\'' +
                '}';
    }
}