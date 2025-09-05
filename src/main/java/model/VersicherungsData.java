package model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * DTO (Data Transfer Object) für die aus PDFs extrahierten Versicherungsdaten.
 * Repräsentiert die Daten einer Versicherungsbestätigung.
 *
 * @author Stephane Dongmo
 * @since 16/07/2025
 */
public class VersicherungsData {

    private String versicherungsscheinNr;    //LU_VSN

    private String versicherungsart;        //LU_RIS
    private String PoliceInfo;              //LU_KLINFO FROM KLAUSELN

    // Adressdaten des Versicherungsnehmers (LU_MASKEP)
    private String firmaName;               //LU_NAM
    private String strasse;                 //LU_ADRESSE
    private String strasseNr;               //LU_ADRESSE_NR
    private String plz;                     //LU_PLZ
    private String ort;                     //LU_ORT
    private String land;                    //LU_LANDNAME FROM VIASS005

    //Maxima Leistungen
    private String maxJahresleistung;       //LU_VHV_SUM_216
    private String maxSchadenleistung;      //LU_VHV_SUM_205
    private String waehrung;                //LU_Waehrung

    private String gesellschaft;             //LU_GES
    private String gesName;                 //LU_VUN FROM GESELLSCHAFT
    private String gesOrt;                  //LU_VUO FROM GESELLSCHAFT

    /**
     * Temporärer PDF-Text für intelligente Validierung.
     * Wird nicht exportiert.
     */
    private transient String pdfText;

    /**
     * Standardkonstruktor für VersicherungsData.
     * Initialisiert ein neues, leeres VersicherungsData-Objekt.
     */
    public VersicherungsData() {
    }

    /**
     * Gibt eine statische Liste von Headern in der gewünschten Reihenfolge für den Export zurück.
     * Diese Header entsprechen den Alias-Namen, die in der {@link #toRowData()} Methode verwendet werden.
     *
     * @return Eine {@link List} von Strings, die die Export-Header in der korrekten Reihenfolge darstellen.
     */
    public static List<String> getExportHeaders() {
        return Arrays.asList(
                "Versicherungsschein-Nr",

                "Versicherungsart",
                "PoliceInfo",

                "Firma/Name",
                "Strasse",
                "Nr",
                "PLZ",
                "Ort",
                "Land",

                "Max Jahresleistung",
                "Max Schadenleistung",
                "Währung",

                "GesName",
                "GesOrt"
        );
    }

    /**
     * Konvertiert die Versicherungsdaten in ein {@link RowData}-Objekt für den Export
     * mit den bestehenden Schreibern.
     * Die Felder werden auf die entsprechenden Alias-Namen abgebildet, die
     * für den Export relevant sind (entsprechend der Felder LU_MASKEP + LU_ALLE
     * in VIAS).
     *
     * @return Ein {@link RowData}-Objekt, das die Versicherungsdaten enthält,
     * formatiert für den Export.
     */
    public RowData toRowData() {
        RowData row = new RowData();

        String vsnFormatted = this.versicherungsscheinNr != null ?
                this.versicherungsscheinNr : "";

        // Verwendung der ALIAS-Namen anstelle der technischen Feldnamen für den Export.
        row.put("Versicherungsschein-Nr", vsnFormatted);

        row.put("Versicherungsart", this.versicherungsart != null ? this.versicherungsart : "");
        row.put("PoliceInfo", this.PoliceInfo != null ? this.PoliceInfo : "");

        row.put("Firma/Name", this.firmaName != null ? this.firmaName : "");
        row.put("Strasse", this.strasse != null ? this.strasse : "");
        row.put("Nr", this.strasseNr != null ? this.strasseNr : "");
        row.put("PLZ", this.plz != null ? this.plz : "");
        row.put("Ort", this.ort != null ? this.ort : "");
        row.put("Land", this.land != null ? this.land : "");

        row.put("Max Jahresleistung", this.maxJahresleistung != null ? this.maxJahresleistung : "");
        row.put("Max Schadenleistung", this.maxSchadenleistung != null ? this.maxSchadenleistung : "");
        row.put("Währung", this.waehrung != null ? this.waehrung : "");

        row.put("GesName", this.gesName != null ? this.gesName : "");
        row.put("GesOrt", this.gesOrt != null ? this.gesOrt : "");

        return row;
    }

    // --- Getter und Setter ---

    /**
     * Gibt die Versicherungsnummer zurück.
     *
     * @return Die Versicherungsnummer.
     */
    public String getVersicherungsscheinNr() {
        return versicherungsscheinNr;
    }

    /**
     * Setzt die Versicherungsnummer.
     *
     * @param versicherungsscheinNr Die zu setzende Versicherungsnummer.
     */
    public void setVersicherungsscheinNr(String versicherungsscheinNr) {
        this.versicherungsscheinNr = versicherungsscheinNr;
    }

    /**
     * Gibt den Namen des Versicherungsnehmers zurück.
     *
     * @return Der Name des Versicherungsnehmers.
     */
    public String getFirmaName() {
        return firmaName;
    }

    /**
     * Setzt den Namen des Versicherungsnehmers.
     *
     * @param firmaName Der zu setzende Name des Versicherungsnehmers.
     */
    public void setFirmaName(String firmaName) {
        this.firmaName = firmaName;
    }

    /**
     * Gibt die Straße zurück.
     *
     * @return Die Straße.
     */
    public String getStrasse() {
        return strasse;
    }

    /**
     * Setzt die Straße.
     *
     * @param strasse Die zu setzende Straße.
     */
    public void setStrasse(String strasse) {
        this.strasse = strasse;
    }

    /**
     * Gibt die Hausnummer zurück.
     *
     * @return Die Hausnummer.
     */
    public String getStrasseNr() {
        return strasseNr;
    }

    /**
     * Setzt die Hausnummer.
     *
     * @param strasseNr Die zu setzende Hausnummer.
     */
    public void setStrasseNr(String strasseNr) {
        this.strasseNr = strasseNr;
    }

    /**
     * Gibt die Postleitzahl (PLZ) zurück.
     *
     * @return Die Postleitzahl.
     */
    public String getPlz() {
        return plz;
    }

    /**
     * Setzt die Postleitzahl (PLZ).
     *
     * @param plz Die zu setzende Postleitzahl.
     */
    public void setPlz(String plz) {
        this.plz = plz;
    }

    /**
     * Gibt den Ort zurück.
     *
     * @return Der Ort.
     */
    public String getOrt() {
        return ort;
    }

    /**
     * Setzt den Ort.
     *
     * @param ort Der zu setzende Ort.
     */
    public void setOrt(String ort) {
        this.ort = ort;
    }

    /**
     * Gibt das Land zurück.
     *
     * @return Das Land.
     */
    public String getLand() {
        return land;
    }

    /**
     * Setzt das Land.
     *
     * @param land Das zu setzende Land.
     */
    public void setLand(String land) {
        this.land = land;
    }

    /**
     * Gibt die Versicherungsart zurück.
     *
     * @return Die Versicherungsart.
     */
    public String getVersicherungsart() {
        return versicherungsart;
    }

    /**
     * Setzt die Versicherungsart.
     *
     * @param versicherungsart Die zu setzende Versicherungsart.
     */
    public void setVersicherungsart(String versicherungsart) {
        this.versicherungsart = versicherungsart;
    }

    /**
     * Gibt die Police-Information zurück.
     *
     * @return Die Police-Information.
     */
    public String getPoliceInfo() {
        return PoliceInfo;
    }

    /**
     * Setzt die Police-Information.
     *
     * @param PoliceInfo Die zu setzende Police-Information.
     */
    public void setPoliceInfo(String PoliceInfo) {
        this.PoliceInfo = PoliceInfo;
    }

    /**
     * Gibt die maximale Jahresleistung zurück.
     *
     * @return Die maximale Jahresleistung.
     */
    public String getMaxJahresleistung() {
        return maxJahresleistung;
    }

    /**
     * Setzt die maximale Jahresleistung.
     *
     * @param maxJahresleistung Die zu setzende maximale Jahresleistung.
     */
    public void setMaxJahresleistung(String maxJahresleistung) {
        this.maxJahresleistung = maxJahresleistung;
    }

    /**
     * Gibt die maximale Schadenleistung zurück.
     *
     * @return Die maximale Schadenleistung.
     */
    public String getMaxSchadenleistung() {
        return maxSchadenleistung;
    }

    /**
     * Setzt die maximale Schadenleistung.
     *
     * @param maxSchadenleistung Die zu setzende maximale Schadenleistung.
     */
    public void setMaxSchadenleistung(String maxSchadenleistung) {
        this.maxSchadenleistung = maxSchadenleistung;
    }

    /**
     * Gibt die Währung zurück.
     *
     * @return Die Währung.
     */
    public String getWaehrung() {
        return waehrung;
    }

    /**
     * Setzt die Währung.
     *
     * @param waehrung Die zu setzende Währung.
     */
    public void setWaehrung(String waehrung) {
        this.waehrung = waehrung;
    }

    /**
     * Gibt die Gesellschaft zurück.
     *
     * @return Die Gesellschaft.
     */
    public String getGesellschaft() {
        return gesellschaft;
    }

    /**
     * Setzt die Gesellschaft.
     *
     * @param gesellschaft Die zu setzende Gesellschaft.
     */
    public void setGesellschaft(String gesellschaft) {
        this.gesellschaft = gesellschaft;
    }

    /**
     * Gibt den Namen der Gesellschaft zurück.
     *
     * @return Der Name der Gesellschaft.
     */
    public String getGesName() {
        return gesName;
    }

    /**
     * Setzt den Namen der Gesellschaft.
     *
     * @param gesName Der zu setzende Name der Gesellschaft.
     */
    public void setGesName(String gesName) {
        this.gesName = gesName;
    }

    /**
     * Gibt den Ort der Gesellschaft zurück.
     *
     * @return Der Ort der Gesellschaft.
     */
    public String getGesOrt() {
        return gesOrt;
    }

    /**
     * Setzt den Ort der Gesellschaft.
     *
     * @param gesOrt Der zu setzende Ort der Gesellschaft.
     */
    public void setGesOrt(String gesOrt) {
        this.gesOrt = gesOrt;
    }

    /**
     * Gibt den extrahierten PDF-Text zurück.
     * Dieser Text wird temporär für Validierungszwecke verwendet und nicht in den Export aufgenommen.
     *
     * @return Der extrahierte PDF-Text.
     */
    public String getPdfText() {
        return pdfText;
    }

    /**
     * Setzt den extrahierten PDF-Text.
     * Dieser Text wird temporär für Validierungszwecke verwendet und nicht in den Export aufgenommen.
     *
     * @param pdfText Der zu setzende PDF-Text.
     */
    public void setPdfText(String pdfText) {
        this.pdfText = pdfText;
    }


    // --- Override-Methoden (equals, hashCode, toString) ---

    /**
     * Vergleicht dieses {@link VersicherungsData}-Objekt mit einem anderen Objekt.
     * Zwei {@link VersicherungsData}-Objekte gelten als gleich, wenn ihre
     * Versicherungsnummern ({@code versicherungsscheinNr}) gleich sind.
     *
     * @param o Das Objekt, mit dem verglichen werden soll.
     * @return {@code true}, wenn die Objekte gleich sind, sonst {@code false}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersicherungsData that = (VersicherungsData) o;
        return Objects.equals(versicherungsscheinNr, that.versicherungsscheinNr);
    }

    /**
     * Generiert einen Hash-Code für dieses {@link VersicherungsData}-Objekt.
     * Der Hash-Code basiert auf der Versicherungsnummer ({@code versicherungsscheinNr}).
     *
     * @return Der Hash-Code des Objekts.
     */
    @Override
    public int hashCode() {
        return Objects.hash(versicherungsscheinNr);
    }

    /**
     * Gibt eine String-Repräsentation dieses {@link VersicherungsData}-Objekts zurück.
     * Die Ausgabe enthält die Versicherungsnummer, den Namen des Versicherungsnehmers
     * und den Ort.
     *
     * @return Eine formatierte String-Darstellung des Objekts.
     */
    @Override
    public String toString() {
        return String.format("VersicherungsData{VSN='%s', Firma='%s', GesName='%s'}",
                versicherungsscheinNr, firmaName, gesName);
    }
}