package formatter.contract;

import formatter.ColumnValueFormatter;
import model.RowData;
import model.contract.CoverRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CoverFormatter
 * <p>
 * Diese Klasse ist für die Aufbereitung (Mapping & Formatierung)
 * von Daten im COVER-Kontext verantwortlich.
 * <p>
 * Ziele:
 * - DB-Zeilen (RowData) in domänenspezifische Objekte (CoverRecord) überführen.
 * - Werte für UI-Tabellen und Exporte (CSV/XLSX/PDF) konsistent formatieren.
 * - Datum-/Geld-/Textformatierung zentral bündeln.
 * <p>
 * Wichtige Hinweise:
 * - ColumnValueFormatter besitzt eine statische Methode {@code format(RowData, String)}.
 * Aufruf daher über den Klassennamen (nicht über eine Instanz).
 * - RowData stellt KEINE get(column)-Methode bereit; Rohwerte liest man über
 * {@code row.getValues().get(column)}.
 */
public class CoverFormatter {

    // ------------------------------------------------------------
    // TODO: DB-Spaltennamen hier projektspezifisch anpassen
    // ------------------------------------------------------------
    private static final String COL_CONTRACT_NUMBER = "VSN";     // z. B. Versicherungsschein-Nr
    private static final String COL_INSURED_NAME = "LU_NAM";  // Versicherungsnehmer (Name/Firma)
    private static final String COL_BROKER_NAME = "MAKLERV"; // Makler (Label via Dict)
    private static final String COL_STATUS = "LU_STA";  // Status (Label via Dict)
    private static final String COL_START_DATE = "LU_BEG";  // Vertragsbeginn
    private static final String COL_END_DATE = "LU_ABL";  // Vertragsende


    // Export-/UI-Header (Reihenfolge bestimmt Spaltenreihenfolge)
    private static final List<String> DEFAULT_HEADERS = List.of(
            "Vertrag/VSN",
            "Versicherungsnehmer",
            "Makler",
            "Status",
            "Beginn",
            "Ende"
    );

    /**
     * Standard-Konstruktor (keine Abhängigkeiten nötig).
     */
    public CoverFormatter() {
    }

    /**
     * Optional: Liefert den Rohwert einer Spalte aus RowData.
     * Nützlich, falls der ungeformatete Wert benötigt wird.
     *
     * @param row    Zeile.
     * @param column Spaltenname.
     * @return Rohwert oder leerer String bei null.
     */
    public static String raw(RowData row, String column) {
        String v = row.getValues().get(column); // RowData stellt getValues() bereit.
        return v == null ? "" : v;
    }

    /**
     * Mappt eine einzelne DB-Zeile (RowData) auf ein CoverRecord-Objekt.
     * <p>
     * Nutzung der statischen Format-Funktion:
     * - {@link ColumnValueFormatter#format(RowData, String)}
     *
     * @param row DB-Ergebniszeile.
     * @return CoverRecord für UI/Service-Schicht (formatierte Strings).
     */
    public CoverRecord toCoverRecord(RowData row) {
        String vsn = ColumnValueFormatter.format(row, COL_CONTRACT_NUMBER);
        String name = ColumnValueFormatter.format(row, COL_INSURED_NAME);
        String mak = ColumnValueFormatter.format(row, COL_BROKER_NAME);
        String sta = ColumnValueFormatter.format(row, COL_STATUS);
        String beg = ColumnValueFormatter.format(row, COL_START_DATE);
        String end = ColumnValueFormatter.format(row, COL_END_DATE);

        CoverRecord record = new CoverRecord();
        record.setContractNumber(vsn);
        record.setInsuredName(name);
        record.setBrokerName(mak);
        record.setStatus(sta);
        record.setStartDate(beg);
        record.setEndDate(end);
        return record;
    }

    /**
     * Liefert die Standard-Header (Titel) für UI-Tabellen/Exporte.
     *
     * @return Liste der Spaltentitel.
     */
    public List<String> headers() {
        return DEFAULT_HEADERS;
    }

    /**
     * Wandelt einen CoverRecord in eine Map "Header → Wert" um.
     * Geeignet für generische TableViews/Exporter.
     *
     * @param record Domänenobjekt.
     * @return geordnete Map (LinkedHashMap) mit Headern und Werten.
     */
    public Map<String, String> toUiRow(CoverRecord record) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("Vertrag/VSN", safe(record.getContractNumber()));
        out.put("Versicherungsnehmer", safe(record.getInsuredName()));
        out.put("Makler", safe(record.getBrokerName()));
        out.put("Status", safe(record.getStatus()));
        out.put("Beginn", safe(record.getStartDate()));
        out.put("Ende", safe(record.getEndDate()));
        return out;
    }

    /**
     * Erzeugt ein String-Array in der Reihenfolge der {@link #headers()}.
     *
     * @param record Domänenobjekt.
     * @return Werte-Array passend zu {@link #headers()}.
     */
    public String[] toExportRow(CoverRecord record) {
        return new String[]{
                safe(record.getContractNumber()),
                safe(record.getInsuredName()),
                safe(record.getBrokerName()),
                safe(record.getStatus()),
                safe(record.getStartDate()),
                safe(record.getEndDate())
        };
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
