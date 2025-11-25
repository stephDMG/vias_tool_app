package service.audit.repository;

import model.RowData;
import model.audit.CoverAuditRecord;
import model.audit.VsnAuditRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.DatabaseService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Konkrete Implementierung des AuditRepository.
 * Verantwortlich für die Ausführung der spezifischen SQL-Abfragen zur Extraktion
 * von Dokumenteninformationen für Audits (Vertrag und Schaden).
 */
public class AuditRepositoryImpl implements AuditRepository {

    private static final Logger logger = LoggerFactory.getLogger(AuditRepositoryImpl.class);
    // --- NEUE FORMATTER FÜR ROHDATEN (VIAS) ---
    // Format für reine Datumsfelder (z.B. 20250807)
    private static final DateTimeFormatter VIAS_DATE_INPUT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // Format für reine Uhrzeitfelder (z.B. 114448)
    private static final DateTimeFormatter VIAS_TIME_INPUT_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final String COVER_AUDIT_SQL = """
            SELECT
                SAB.LU_SB_NAM AS "Nachname",
                SAB.LU_SB_VOR AS "Vorname",
                COVER.LU_VSN AS "Police Nr",
                DC3.Beschreibung AS "Beschreibung",
                DC2.Parameter AS "Parameter",
                DC2.Extension AS "Extension",
                DC2.Betreff AS "Betreff",
                DC2.Bezugsdatum AS "Bezugsdatum",
                DC2.Uhrzeit AS "Uhrzeit",
                DC2.DateiName AS "DateiName"
            FROM LU_ALLE AS COVER
                     LEFT JOIN DCPL0300 AS DC3 ON COVER.VPointer = DC3.VPointer
                     LEFT JOIN DCPL0200 AS DC2 ON DC3.Vorgang = DC2.Vorgang
                     LEFT JOIN SACHBEA AS SAB ON  COVER.LU_SACHBEA_VT =  SAB.LU_SB_KURZ
            WHERE COVER.Sparte LIKE '%COVER'
              AND COVER.LU_VSN IN (%s)
              AND DC2.Vorgang IS NOT NULL
              AND DC2.Vorgang <> ''
            """;

    // --- SQL-Abfragen (Unverändert gelassen, wie gewünscht) ---
    private static final String SCHADEN_AUDIT_SQL = """
            SELECT
                SAB.LU_SB_VOR AS "Vorname",
                SAB.LU_SB_NAM AS "Nachname",
                SVA.LU_SNR_TEXT AS "SchadenNr", -- Wichtig: Wir nutzen den Text-Schaden-Key
                DC3.Beschreibung AS "Beschreibung",
                DC2.Parameter AS "Parameter",
                DC2.Extension AS "Extension",
                DC2.Betreff AS "Betreff",
                DC2.Bezugsdatum AS "Bezugsdatum",
                DC2.Uhrzeit AS "Uhrzeit"
            FROM LU_SVA AS SVA
                     LEFT JOIN DCPL0300 AS DC3 ON SVA.VPointer = DC3.VPointer
                     LEFT JOIN DCPL0200 AS DC2 ON DC3.Vorgang = DC2.Vorgang
                     LEFT JOIN SACHBEA AS SAB ON SVA.LU_SACHBEA_SC = SAB.LU_SB_KURZ
            WHERE SVA.Sparte = 'SVA'
              AND SVA.LU_SNR IN (%s)
              AND DC2.Vorgang IS NOT NULL
              AND DC2.Vorgang <> ''
            """;
    private final DatabaseService databaseService;


    public AuditRepositoryImpl(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    // --- Interface-Methoden (Bleiben unverändert) ---

    @Override
    public List<CoverAuditRecord> fetchCoverDocumentsByPolicyNr(List<String> policeNrs) throws Exception {
        if (policeNrs == null || policeNrs.isEmpty()) {
            return List.of();
        }

        logger.info("Starte Vertrags-Audit-Abfrage für {} Policennummern...", policeNrs.size());

        List<RowData> rawData = databaseService.executeRawQuery(COVER_AUDIT_SQL, policeNrs.toArray(new String[0]));

        return mapToCoverAuditRecords(rawData);
    }

    @Override
    public List<VsnAuditRecord> fetchSchadenDocumentsByVsnNr(List<String> vsnNrs) throws Exception {
        if (vsnNrs == null || vsnNrs.isEmpty()) {
            return List.of();
        }

        logger.info("Starte Schaden-Audit-Abfrage für {} VSN-Nummern...", vsnNrs.size());

        List<RowData> rawData = databaseService.executeRawQuery(SCHADEN_AUDIT_SQL, vsnNrs.toArray(new String[0]));

        return mapToVsnAuditRecords(rawData);
    }

    // --- Mapper-Logik (KORRIGIERT) ---

    /**
     * Mappt die generischen RowData-Objekte des DatabaseService auf spezifische CoverAuditRecord-Objekte.
     */
    private List<CoverAuditRecord> mapToCoverAuditRecords(List<RowData> rawData) {
        List<CoverAuditRecord> records = new ArrayList<>();

        for (RowData row : rawData) {
            try {
                // 1. Rohdaten extrahieren
                String dateStr = trimValue(row, "Bezugsdatum");
                String timeStr = trimValue(row, "Uhrzeit");
                String dateiName = trimValue(row, "DateiName");

                // 2. Datum et Zeit parsen
                LocalDate date = parseVIASDate(dateStr);
                LocalTime time = parseVIASTime(timeStr);

                // 3. Konvertierung in die zwei benötigten LocalDateTime-Objekte:

                // A) bezugsdatum (Nur Datum, Zeit auf 00:00:00). Dient als Datumsträger.
                LocalDateTime bezugsdatum = (date != null) ? date.atStartOfDay() : null;

                // B) uhrzeit (Datum und Zeit kombiniert). WICHTIG: Fallback auf 12:00:00 bei Zeit-Parsing-Fehler.
                LocalDateTime uhrzeit = null;
                if (date != null) {
                    // Verwendet LocalTime.NOON (12:00:00) falls das Parsing der Zeit fehlschlägt.
                    LocalTime safeTime = (time != null) ? time : LocalTime.NOON;
                    uhrzeit = date.atTime(safeTime);
                }

                records.add(new CoverAuditRecord(
                        trimValue(row, "Police Nr"),
                        trimValue(row, "Nachname"),
                        trimValue(row, "Vorname"),
                        trimValue(row, "Beschreibung"),
                        trimValue(row, "Parameter"),
                        trimValue(row, "Extension"),
                        trimValue(row, "Betreff"),
                        bezugsdatum,
                        uhrzeit,      // Enthält nun 12:00:00 als Fallback
                        dateiName
                ));
            } catch (Exception e) {
                logger.error("Fehler beim Mappen einer Zeile zu CoverAuditRecord: {}", row, e);
            }
        }
        return records;
    }

    /**
     * Mappt die generischen RowData-Objekte des DatabaseService auf spezifische VsnAuditRecord-Objekte.
     * KORRIGIERT: Wendet dieselbe Logik für LocalTime.NOON an.
     */
    private List<VsnAuditRecord> mapToVsnAuditRecords(List<RowData> rawData) {
        List<VsnAuditRecord> records = new ArrayList<>();

        for (RowData row : rawData) {
            try {
                String dateStr = trimValue(row, "Bezugsdatum");
                String timeStr = trimValue(row, "Uhrzeit");
                String dateiName = trimValue(row, "DateiName");

                LocalDate date = parseVIASDate(dateStr);
                LocalTime time = parseVIASTime(timeStr);

                LocalDateTime bezugsdatum = (date != null) ? date.atStartOfDay() : null;
                LocalDateTime uhrzeit = null;

                if (date != null) {
                    LocalTime safeTime = (time != null) ? time : LocalTime.NOON;
                    uhrzeit = date.atTime(safeTime);
                }

                records.add(new VsnAuditRecord(
                        trimValue(row, "SchadenNr"),
                        trimValue(row, "Nachname"),
                        trimValue(row, "Vorname"),
                        trimValue(row, "Beschreibung"),
                        trimValue(row, "Parameter"),
                        trimValue(row, "Extension"),
                        trimValue(row, "Betreff"),
                        bezugsdatum, // LocalDateTime (Datum only)
                        uhrzeit,      // LocalDateTime (Datum + Zeit kombiniert oder 12:00:00 als Fallback)
                        dateiName
                ));
            } catch (Exception e) {
                logger.error("Fehler beim Mappen einer Zeile zu VsnAuditRecord: {}", row, e);
            }
        }
        return records;
    }

    // --- Helper-Methoden (KORRIGIERT) ---

    private String trimValue(RowData row, String key) {
        String value = row.getValues().get(key);
        return value != null ? value.trim() : "";
    }

    /**
     * Versucht, einen VIAS-String als Datum (yyyyMMdd) zu parsen (z.B. "20250807").
     */
    private LocalDate parseVIASDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }

        String cleanString = dateString.trim();

        try {
            if (cleanString.length() == 8) {
                return LocalDate.parse(cleanString, VIAS_DATE_INPUT_FMT);
            }
        } catch (DateTimeParseException e) {
            // Fängt die ParseException ab
        }

        // Loggen des Parsing-Fehlers
        logger.warn("Fehler beim Parsen des Datums '{}'. Fehler: Konnte nicht als yyyyMMdd-Datum geparst werden.", dateString);
        return null;
    }

    /**
     * Versucht, einen VIAS-String als Uhrzeit (HHmmss) zu parsen.
     * WICHTIG: Fügt führende Nullen hinzu, wenn die Länge unter 6 liegt, um HHmmss zu erzwingen.
     */
    private LocalTime parseVIASTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return null;
        }

        String cleanString = timeString.trim();

        try {
            // Wenn die Länge unter 6 liegt, führen wir ein Padding mit Nullen durch (z.B. "7420" -> "074200")
            if (cleanString.length() < 6 && cleanString.length() > 0) {
                // Wir nehmen an, dass die fehlenden Ziffern am Ende (Sekunden) oder am Anfang (Stunden) fehlen.
                // Die sicherste Annahme für die meisten Fehler ist das 5-stellige Format, das zur korrekten HHMMSS gepaddet werden muss.
                cleanString = String.format("%6s", cleanString).replace(' ', '0');
            }

            if (cleanString.length() == 6) {
                return LocalTime.parse(cleanString, VIAS_TIME_INPUT_FMT);
            }

        } catch (DateTimeParseException e) {
            // Das Parsing ist fehlgeschlagen, auch nach der Korrektur der Länge.
        }

        // Avertissement nur, wenn das Parsing nach der Korrektur der Länge fehlschlägt
        logger.warn("Fehler beim Parsen der Uhrzeit '{}'. Nutze NULL. Fehler: Konnte nicht als HHmmss geparst werden.", timeString);
        return null; // Retourne null
    }
}