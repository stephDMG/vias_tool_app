package formatter;

import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  Die Klasse OpListeFormatter dient zum Formatieren der OP-Liste aus dem Excel-Dokument.
 *
 *  <p><strong>Funktionalität:</strong></p>
 *  <ul>
 *      <li>Formatieren der Rohdaten in ein benutzerfreundliches Format.</li>
 *  </ul>
 */
public class OpListeFormatter {
    private static final Logger logger = LoggerFactory.getLogger(OpListeFormatter.class);

    private static final DecimalFormat DF_MONEY   = new DecimalFormat("#,##0.00");
    private static final DecimalFormat DF_PERCENT = new DecimalFormat("#,##0.##");

    private static final Map<String, String> GENERAL_HEADERS = Map.ofEntries(
            Map.entry("A.LU_VMT", "Makler"),
            Map.entry("A.LU_RNR", "Rg-NR"),
            Map.entry("A.LU_VSN", "Policen-Nr"),
            Map.entry("LA.LU_VSN_Makler", "VSN Makler"),
            Map.entry("A.LU_ZJ", "Zeichnungsjahr"),
            Map.entry("LMP.LU_NAM", "VN"),
            Map.entry("A.LU_RDT", "Rg-Datum"),
            Map.entry("A.LU_BDT", "Bu-Datum"),
            Map.entry("A.LU_FLG", "Fälligkeit"),
            Map.entry("A.LU_VSTLD", "LänderKZ"),
            Map.entry("A.LU_SD_WART", "VA"),
            Map.entry("A.LU_Waehrung", "Währung"),
            Map.entry("LU_NET_100", "100% - Netto Prämie"),
            Map.entry("LU_VST", "Steuersatz"),
            Map.entry("LU_VSTBetrag", "Steuerbetrag"),
            Map.entry("LU_Praemie", "100% - Brutto Prämie"),
            Map.entry("LU_OBT", "Anteil in %"),
            Map.entry("LU_NET", "Anteil als Betrag (Netto)"),
            Map.entry("LU_WProvision", "Courtagebetrag"),
            Map.entry("LU_Restbetrag", "SALDO"),
            Map.entry("A.LU_INK", "INK"),
            Map.entry("LU_MAHN_Bemerkung", "Bemerkung"),
            Map.entry("STAT_CODE1", "Statistik code 1"),
            Map.entry("STAT_CODE2", "Statistik code 2"),
            Map.entry("STAT_CODE3", "Statistik code 3"),
            Map.entry("STAT_CODE4", "Statistik code 4"),
            Map.entry("STAT_CODE5", "Statistik code 5"),
            Map.entry("STAT_CODE6", "Statistik code 6"),
            Map.entry("A.LU_ABW", "Abweichung")
    );

    private static final List<String> KUNDE_HEADERS_GERMAN = List.of(
            "Rg-NR", "Policen-Nr", "Zeichnungsjahr", "Versicherungsnehmer", "Rg-Datum", "Fälligkeit", "Währung",
            "Abrechnungsbetrag", "Zahlbetrag/Teilzahlungen", "SALDO"
    );
    private static final List<String> KUNDE_HEADERS_ENGLISH = List.of(
            "Invoice No.", "Policy No.", "Year", "Policy holder", "Invoice date", "Due date", "Currency",
            "Settlement amount", "Payment amount/Partial payment", "Balance"
    );

    public static String getCleanedName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.replace("(", " ")
                .replace(")", " ")
                .replace("+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }


    public List<RowData> format(List<RowData> rawData) {
        List<RowData> formattedList = new ArrayList<>();
        if (rawData == null || rawData.isEmpty()) return formattedList;

        for (RowData rawRow : rawData) {
            Map<String, String> values = rawRow.getValues();
            RowData newRow = new RowData();

            try {

                newRow.put("Makler", get(values, "A.LU_VMT", "LU_VMT"));
                newRow.put("Rg-NR", resolveRgNr(values));
                newRow.put("Policen-Nr", get(values, "A.LU_VSN", "LU_VSN", "Police Nr.", "Police Nr"));
                newRow.put("VSN Makler", get(values, "LA.LU_VSN_Makler", "LU_VSN_Makler"));
                newRow.put("Zeichnungsjahr", get(values, "A.LU_ZJ", "LU_ZJ"));
                newRow.put("VN", get(values, "LMP.LU_NAM", "LU_NAM").replace("(", "").replace(")", "").replace("+", "&"));

                String rdt = get(values, "A.LU_RDT", "LU_RDT");
                String bdt = get(values, "A.LU_BDT", "LU_BDT");
                newRow.put("Rg-Datum", !rdt.isEmpty() ? rdt : bdt);

                newRow.put("Fälligkeit", get(values, "A.LU_FLG", "LU_FLG"));
                newRow.put("LänderKZ", get(values, "A.LU_VSTLD", "LU_VSTLD"));
                newRow.put("VA", get(values, "A.LU_SD_WART", "LU_SD_WART"));
                newRow.put("Währung", get(values, "A.LU_Waehrung", "LU_Waehrung"));

                double obt = parseDouble(get(values, "LU_OBT"));
                double vstBetrag = parseDouble(get(values, "LU_VSTBetrag"));
                double praemie = parseDouble(get(values, "LU_Praemie"));
                double spakz = parseDouble(get(values, "LU_SPAKZ"));
                double restbetrag = parseDouble(get(values, "LU_Restbetrag"));
                double wProvision = parseDouble(get(values, "LU_WProvision"));
                double net100 = parseDouble(get(values, "LU_NET_100"));
                double luVst = parseDouble(get(values, "LU_VST"));
                double luNet = parseDouble(get(values, "LU_NET"));

                newRow.put("100% - Netto Prämie", DF_MONEY.format(net100));
                newRow.put("Steuersatz", DF_PERCENT.format(luVst));
                newRow.put("Steuerbetrag", (obt > 0 && obt < 100 && vstBetrag != 0) ? DF_MONEY.format(vstBetrag / (obt / 100.0)) : DF_MONEY.format(vstBetrag));
                newRow.put("100% - Brutto Prämie", (obt > 0 && obt < 100 && praemie != 0) ? DF_MONEY.format(praemie / (obt / 100.0)) : DF_MONEY.format(praemie));
                newRow.put("Anteil in %", DF_PERCENT.format(obt));
                newRow.put("Anteil als Betrag (Netto)", DF_MONEY.format(luNet));
                newRow.put("Anteil als Betrag (Brutto)", DF_MONEY.format(praemie));
                newRow.put("Courtage in %", (spakz == 999.00) ? "0" : DF_PERCENT.format(spakz * 0.1));
                newRow.put("Courtagebetrag", DF_MONEY.format(wProvision));
                newRow.put("Abrechnungsbetrag", DF_MONEY.format(praemie));
                newRow.put("Zahlbetrag/Teilzahlungen", String.valueOf(parseDouble(String.valueOf((praemie - restbetrag)))));
                newRow.put("SALDO", DF_MONEY.format(restbetrag));
                newRow.put("Zahlerwartung", DF_MONEY.format(restbetrag - wProvision));

                newRow.put("INK", get(values, "A.LU_INK", "LU_INK"));
                newRow.put("Mahnstufe", get(values, "LU_MA2", "A.LU_MA2").isEmpty() ? (get(values, "LU_MA1", "A.LU_MA1").isEmpty() ? "" : "ZE") : "QM");
                newRow.put("Bemerkung", get(values, "LU_MAHN_Bemerkung", "A.LU_MAHN_Bemerkung").replace("\r\n", ""));
                newRow.put("Statistik Codes", concatStatCodes(
                        get(values, "STAT_CODE1"), get(values, "STAT_CODE2"), get(values, "STAT_CODE3"),
                        get(values, "STAT_CODE4"), get(values, "STAT_CODE5"), get(values, "STAT_CODE6")).toUpperCase());

                newRow.put("A.LU_VMT", get(values, "A.LU_VMT", "LU_VMT"));
                newRow.put("A.LU_RNR", get(values, "A.LU_RNR", "LU_RNR"));
                newRow.put("A.LU_VSN", get(values, "A.LU_VSN", "LU_VSN"));
                newRow.put("LA.LU_VSN_Makler", get(values, "LA.LU_VSN_Makler", "LU_VSN_Makler"));
                newRow.put("A.LU_ZJ", get(values, "A.LU_ZJ", "LU_ZJ"));
                newRow.put("LMP.LU_NAM", get(values, "LMP.LU_NAM", "LU_NAM"));
                newRow.put("A.LU_RDT", get(values, "A.LU_RDT", "LU_RDT"));
                newRow.put("A.LU_BDT", get(values, "A.LU_BDT", "LU_BDT"));
                newRow.put("A.LU_FLG", get(values, "A.LU_FLG", "LU_FLG"));
                newRow.put("A.LU_VSTLD", get(values, "A.LU_VSTLD", "LU_VSTLD"));
                newRow.put("A.LU_SD_WART", get(values, "A.LU_SD_WART", "LU_SD_WART"));
                newRow.put("A.LU_Waehrung", get(values, "A.LU_Waehrung", "LU_Waehrung"));
                newRow.put("LU_NET_100", get(values, "LU_NET_100"));
                newRow.put("LU_VST", get(values, "LU_VST"));
                newRow.put("LU_VSTBetrag", get(values, "LU_VSTBetrag"));
                newRow.put("LU_Praemie", get(values, "LU_Praemie"));
                newRow.put("LU_OBT", get(values, "LU_OBT"));
                newRow.put("LU_NET", get(values, "LU_NET"));
                newRow.put("LU_WProvision", get(values, "LU_WProvision"));
                newRow.put("LU_Restbetrag", get(values, "LU_Restbetrag"));
                newRow.put("A.LU_INK", get(values, "A.LU_INK", "LU_INK"));
                newRow.put("LU_MAHN_Bemerkung", get(values, "LU_MAHN_Bemerkung", "A.LU_MAHN_Bemerkung"));
                newRow.put("STAT_CODE1", get(values, "STAT_CODE1"));
                newRow.put("STAT_CODE2", get(values, "STAT_CODE2"));
                newRow.put("STAT_CODE3", get(values, "STAT_CODE3"));
                newRow.put("STAT_CODE4", get(values, "STAT_CODE4"));
                newRow.put("STAT_CODE5", get(values, "STAT_CODE5"));
                newRow.put("STAT_CODE6", get(values, "STAT_CODE6"));
                newRow.put("A.LU_ABW", get(values, "A.LU_ABW", "LU_ABW"));

                formattedList.add(newRow);

            } catch (Exception ex) {
                logger.error("Fehler beim Formatieren der OP-Liste: {}", ex.getMessage(), ex);
            }
        }
        return formattedList;
    }


    public List<RowData> formatForExport(List<RowData> mainFormattedList, String language) {
        if (mainFormattedList == null || mainFormattedList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<RowData>> groupedByInvoice = mainFormattedList.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getValues().get("Rg-NR") + "_" + row.getValues().get("Policen-Nr"),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<RowData> finalFormattedList = new ArrayList<>();

        for (Map.Entry<String, List<RowData>> entry : groupedByInvoice.entrySet()) {
            List<RowData> group = entry.getValue();
            RowData firstRow = group.get(0);

            RowData newRow = new RowData();

            // Copier les champs non-agrégés de la première ligne
            newRow.put("Rg-NR", firstRow.getValues().get("Rg-NR"));
            newRow.put("Policen-Nr", firstRow.getValues().get("Policen-Nr"));
            newRow.put("Zeichnungsjahr", firstRow.getValues().get("Zeichnungsjahr"));
            newRow.put("Versicherungsnehmer", firstRow.getValues().get("Versicherungsnehmer"));

            // Formater les dates
            String rgDatum = firstRow.getValues().get("Rg-Datum");
            newRow.put("Rg-Datum", formatDate(rgDatum));
            String falligkeit = firstRow.getValues().get("Fälligkeit");
            newRow.put("Fälligkeit", formatDate(falligkeit));

            newRow.put("Währung", firstRow.getValues().get("Währung"));

            // Calculer les sommes pour les champs monétaires
            double abrechnungsbetragTotal = group.stream().mapToDouble(row -> parseDouble(row.getValues().get("Abrechnungsbetrag"))).sum();
            double zahlbetragTotal = group.stream().mapToDouble(row -> parseDouble(row.getValues().get("Zahlbetrag/Teilzahlungen"))).sum();
            double saldoTotal = group.stream().mapToDouble(row -> parseDouble(row.getValues().get("SALDO"))).sum();

            // Formater les montants
            newRow.put("Abrechnungsbetrag", DF_MONEY.format(abrechnungsbetragTotal));
            newRow.put("Zahlbetrag/Teilzahlungen", DF_MONEY.format(zahlbetragTotal));
            newRow.put("SALDO", DF_MONEY.format(saldoTotal));

            // Ajouter la nouvelle ligne formatée à la liste finale
            finalFormattedList.add(newRow);
        }

        return finalFormattedList;
    }

    // Ajoutez ces deux méthodes utilitaires à la classe OpListeFormatter pour la conversion des formats.
    private static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (DateTimeParseException e) {
            return dateStr;
        }
    }


    private static String get(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (value != null) {
                return value.trim();
            }
        }
        return "";
    }

    private static String resolveRgNr(Map<String, String> values) {
        String rnr  = get(values, "A.LU_RNR", "LU_RNR");
        String rMak = get(values, "A.LU_RNR_Makler", "LU_RNR_Makler");
        String rR   = get(values, "A.LU_RNR_R", "LU_RNR_R");
        if (!rnr.isEmpty() && !rnr.contains("xxxxxxxx")) return rnr;
        if (!rMak.isEmpty()) return rMak;
        return rR;
    }

    private static String concatStatCodes(String... codes) {
        return Arrays.stream(codes)
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }


    public static Map<String, String> createHeaderToKeyMap(List<String> headers) {
        Map<String, String> keyMap = new HashMap<>();
        if (headers.equals(KUNDE_HEADERS_ENGLISH)) {
            for (int i = 0; i < KUNDE_HEADERS_ENGLISH.size(); i++) {
                keyMap.put(KUNDE_HEADERS_ENGLISH.get(i), KUNDE_HEADERS_GERMAN.get(i));
            }
        } else {
            for (String header : headers) {
                keyMap.put(header, header);
            }
        }
        return keyMap;
    }

    public static List<String> getHeadersForExport(String filter, String language) {
        if (filter.equalsIgnoreCase("Kunde")) {
            return language.equalsIgnoreCase("EN") ?
                    KUNDE_HEADERS_ENGLISH : KUNDE_HEADERS_GERMAN;
        }
        return new ArrayList<>(GENERAL_HEADERS.values());
    }

    private static double parseDouble(String val) {
        if (val == null) return 0.0;
        try {
            String clean = val.replace(",", ".").replaceAll("[^0-9.\\-]", "");
            if (clean.isEmpty() || clean.equals(".") || clean.equals("-")) return 0.0;
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }

}