package service.impl;

import model.ai.AiColumnSpec;
import model.ai.AiReportTemplate;
import model.ai.register.AiKnowledgeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.AiQueryBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finale, stabile Architektur: Verwendet eine saubere Kette von "if"-Abfragen,
 * um Filter eindeutig zu erkennen und Mehrdeutigkeit zu vermeiden.
 *
 * @author Coding-Assistent
 * @version 12.1 (Stable Engine)
 */
public class CoverQueryBuilder implements AiQueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(CoverQueryBuilder.class);

    // Patterns für Vertragsbeginn
    private static final Pattern BEGINN_AFTER_PATTERN = Pattern.compile(
            "(?:beginn|anfang)\\s*(?:nach|seit|ab)(?:\\s+dem)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BEGINN_BEFORE_PATTERN = Pattern.compile(
            "(?:beginn|anfang)\\s*(?:vor|bis)(?:\\s+zum)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BEGINN_RANGE_PATTERN = Pattern.compile(
            "(?:beginn|anfang)\\s*zwischen\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})\\s+und\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );

    // NEU: Patterns für Vertragsende (Ablauf)
    private static final Pattern ABLAUF_AFTER_PATTERN = Pattern.compile(
            "(?:ablauf|ende)\\s*(?:nach|seit|ab)(?:\\s+dem)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ABLAUF_BEFORE_PATTERN = Pattern.compile(
            "(?:ablauf|ende)\\s*(?:vor|bis)(?:\\s+zum)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ABLAUF_RANGE_PATTERN = Pattern.compile(
            "(?:ablauf|ende)\\s*zwischen\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})\\s+und\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );


    private static final Pattern MAKLER_NAME_PATTERN = Pattern.compile(
            "(?:makler|vermittler)\\s+name\\s+([a-zA-Z0-9%&\\s.*äöüßÄÖÜ-]+?)(?=\\s+(?:mit|und\\s+zwar|zuwar|felder|feld|spalten|zeige|order\\s+by|limit)\\b|$)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LAND_PATTERN = Pattern.compile(
            "(?:land\\s+code|land)\\s+([a-zA-ZäöüßA-Z0-9 ._-]+?)(?=\\s+(?:mit|und\\s+zwar|zuwar|felder|feld|spalten|zeige|order\\s+by|limit)\\b|$)",
            Pattern.CASE_INSENSITIVE
    );

    //private static final Pattern MAKLER_ID_PATTERN = Pattern.compile("(?:makler|vermittler id|makler nr|vermittler nr)\\s+([a-zA-Z0-9]{6,})", Pattern.CASE_INSENSITIVE);
    // Korrektur: Der RegEx erkennt nun 'makler id' und 'makler nr'
    //private static final Pattern MAKLER_ID_PATTERN = Pattern.compile("(?:makler|vermittler)(?:\\s+(id|nr))?\\s+([a-zA-Z0-9]{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAKLER_ID_PATTERN = Pattern.compile("(?:makler|vermittler)(?:\\s+(?:id|nr))?\\s+([a-zA-Z0-9]{6,})",Pattern.CASE_INSENSITIVE);

    private static final Pattern VSN_PATTERN = Pattern.compile("vsn\\s+([a-zA-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_PATTERN = Pattern.compile("status\\s+'?([a-zA-Z0-9]+)'?", Pattern.CASE_INSENSITIVE);

    private static final Pattern FIELDS_SEPARATOR_PATTERN = Pattern.compile(
            "\\s+(?:" +
                    "und\\s+zwar" +
                    "|mit\\s+den\\s+feldern" +
                    "|mit\\s+feldern" +
                    "|mit\\s+der\\s+feldern" +
                    "|zeige\\s+mir" +
                    "|zeige" +
                    ")\\b:?\\s*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Prüft, ob dieser Builder die Beschreibung verarbeiten kann.
     */
    @Override
    public boolean canHandle(String description) {
        String lowerDesc = description.toLowerCase();
        return lowerDesc.contains("vertrag") || lowerDesc.contains("verträge") || lowerDesc.contains("cover");
    }

    /**
     * Generiert eine SQL-Abfrage basierend auf der Benutzeranfrage.
     * @param description Die vollständige, normalisierte Anfrage des Benutzers.
     * @return Die generierte SQL-Abfrage oder eine Fehlermeldung, wenn keine gültigen Filterkriterien gefunden wurden.
     */
    @Override
    public String generateQuery(String description) {
        String normalizedDescription = description.replaceAll("[\\s‘`’]", " ").replaceAll("\\s+", " ").trim();
        logger.info("🧠 CoverQueryBuilder analysiert: \"{}\"", normalizedDescription);

        String criteriaBlock = normalizedDescription;
        String fieldsBlock = "";
        Matcher separatorMatcher = FIELDS_SEPARATOR_PATTERN.matcher(normalizedDescription);
        if (separatorMatcher.find()) {
            criteriaBlock = normalizedDescription.substring(0, separatorMatcher.start());
            fieldsBlock = normalizedDescription.substring(separatorMatcher.end());
        }

        AiReportTemplate template = AiKnowledgeRegistry.getAllTemplates().get(0);

        List<String> conditions = buildWhereClause(criteriaBlock);
        if (conditions.isEmpty()) {
            return "-- LOKALER KI-FEHLER: Keine gültigen Filterkriterien (wie Makler, VSN, etc.) gefunden.";
        }

        List<String> requestedFields = extractRequestedFields(fieldsBlock);
        return buildQuery(template, conditions, requestedFields);
    }

    /**
     * Hilfsmethode, die ein Datum in verschiedenen Formaten erkennt und in das DB-Format "yyyyMMdd" umwandelt.
     * Unterstützt Formate wie "dd.MM.yyyy", "d.M.yyyy", "dd/MM/yyyy" und "d/M/yyyy".
     * Gibt null zurück, wenn das Datum nicht erkannt wird.
     */
    private String parseAndFormatDate(String dateStr){
        DateTimeFormatter[] inputFormatters = {
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("d.M.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy")
        };
        // Das DB-Format
        DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        for (DateTimeFormatter formatter : inputFormatters) {
            try {
                // Versuche, das Datum zu parsen
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return date.format(dbFormatter);
            } catch (DateTimeParseException e) {
                // Ignoriere Fehler
            }
        }
        logger.warn("Konnte Datum nicht umwandeln: {}", dateStr);
        return null;
    }

    /**
     * Baut die WHERE-Klausel basierend auf den erkannten Kriterien.
     * @param criteriaBlock Der Textblock, der die Filterkriterien enthält.
     * @return Eine Liste von Bedingungen für die WHERE-Klausel.
     */
    private List<String> buildWhereClause(String criteriaBlock) {
        List<String> conditions = new ArrayList<>();
        String remainingCriteria = criteriaBlock.toLowerCase();

        Matcher maklerNameMatcher = MAKLER_NAME_PATTERN.matcher(remainingCriteria);
        // Prüfen, ob ein Maklername angegeben ist
        if (maklerNameMatcher.find()) {
            String name = maklerNameMatcher.group(1).trim();
            // Wildcard-Behandlung
            if (name.contains("%")) {
                conditions.add("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE '" + name.toUpperCase() + "'");
            } else if (name.startsWith("*") && name.endsWith("*")) {
                name = "%" + name.substring(1, name.length()-1) + "%";
                conditions.add("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE '" + name.toUpperCase() + "'");
            } else if (name.endsWith("*")) {
                name = name.substring(0, name.length()-1) + "%";
                conditions.add("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE '" + name.toUpperCase() + "'");
            } else if (name.startsWith("*")) {
                name = "%" + name.substring(1);
                conditions.add("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE '" + name.toUpperCase() + "'");
            } else {
                conditions.add("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) = '" + name.toUpperCase() + "'");
            }

            remainingCriteria = maklerNameMatcher.replaceAll("");
        }
        Matcher maklerIdMatcher = MAKLER_ID_PATTERN.matcher(remainingCriteria);
        // Prüfen, ob eine Makler-ID angegeben ist
        if (maklerIdMatcher.find()) {
            conditions.add("RTRIM(LTRIM(LAL.LU_VMT)) = '" + maklerIdMatcher.group(1).toUpperCase() + "'");
            remainingCriteria = maklerIdMatcher.replaceAll("");
        }
        Matcher vsnMatcher = VSN_PATTERN.matcher(remainingCriteria);
        // Prüfen, ob eine VSN angegeben ist
        if (vsnMatcher.find()) {
            conditions.add("RTRIM(LTRIM(LAL.LU_VSN)) = '" + vsnMatcher.group(1).toUpperCase() + "'");
            remainingCriteria = vsnMatcher.replaceAll("");
        }
        Matcher landMatcher = LAND_PATTERN.matcher(remainingCriteria);
        // Prüfen, ob ein Land oder Land-Code angegeben ist
        if (landMatcher.find()) {
            String value = landMatcher.group(1).trim().toUpperCase();
            conditions.add("(RTRIM(LTRIM(V05.LU_LANDNAME)) = '" + value + "' OR RTRIM(LTRIM(LUM.LU_NAT)) = '" + value + "')");
            remainingCriteria = landMatcher.replaceAll("");
        }
        Matcher statusMatcher = STATUS_PATTERN.matcher(remainingCriteria);
        // Prüfen, ob ein Status angegeben ist
        if (statusMatcher.find()) {
            conditions.add("RTRIM(LTRIM(LAL.LU_STA)) = '" + statusMatcher.group(1).toUpperCase() + "'");
            remainingCriteria = statusMatcher.replaceAll("");
        }


        Matcher beginnRangeMatcher = BEGINN_RANGE_PATTERN.matcher(remainingCriteria);
        // --- Beginn-Filter ---
        if (beginnRangeMatcher.find()) {
            String start = parseAndFormatDate(beginnRangeMatcher.group(1));
            String end = parseAndFormatDate(beginnRangeMatcher.group(2));
            if (start != null && end != null) {
                conditions.add("LAL.LU_BEG BETWEEN '" + start + "' AND '" + end + "'");
            }
        }
        Matcher beginnAfterMatcher = BEGINN_AFTER_PATTERN.matcher(remainingCriteria);
        if (beginnAfterMatcher.find()) {
            String date = parseAndFormatDate(beginnAfterMatcher.group(1));
            if (date != null) {
                conditions.add("LAL.LU_BEG >= '" + date + "'");
            }
        }
        Matcher beginnBeforeMatcher = BEGINN_BEFORE_PATTERN.matcher(remainingCriteria);
        if (beginnBeforeMatcher.find()) {
            String date = parseAndFormatDate(beginnBeforeMatcher.group(1));
            if (date != null) {
                conditions.add("LAL.LU_BEG <= '" + date + "'");
            }
        }

        // --- Ablauf-Filter ---
        Matcher ablaufRangeMatcher = ABLAUF_RANGE_PATTERN.matcher(remainingCriteria);
        if (ablaufRangeMatcher.find()) {
            String start = parseAndFormatDate(ablaufRangeMatcher.group(1));
            String end = parseAndFormatDate(ablaufRangeMatcher.group(2));
            if (start != null && end != null) {
                conditions.add("LAL.LU_ABL BETWEEN '" + start + "' AND '" + end + "'");
            }
        }
        Matcher ablaufAfterMatcher = ABLAUF_AFTER_PATTERN.matcher(remainingCriteria);
        // Prüfen, ob ein Ablauf nach einem bestimmten Datum angegeben ist
        if (ablaufAfterMatcher.find()) {
            String date = parseAndFormatDate(ablaufAfterMatcher.group(1));
            if (date != null) {
                conditions.add("LAL.LU_ABL >= '" + date + "'");
            }
        }
        Matcher ablaufBeforeMatcher = ABLAUF_BEFORE_PATTERN.matcher(remainingCriteria);
        // Prüfen, ob ein Ablauf vor einem bestimmten Datum angegeben ist
        if (ablaufBeforeMatcher.find()) {
            String date = parseAndFormatDate(ablaufBeforeMatcher.group(1));
            if (date != null) {
                conditions.add("LAL.LU_ABL <= '" + date + "'");
            }
        }

        logger.info("Gefundene WHERE-Bedingungen: {}", conditions);
        return conditions;
    }

    /**
     * Extrahiert die angeforderten Felder aus dem gegebenen Textblock.
     * Normalisiert den Text, entfernt unerwünschte Zeichen und teilt die Felder auf.
     *
     * @param fieldsBlock Der Textblock, der die angeforderten Felder enthält.
     * @return Eine Liste der angeforderten Felder, bereinigt und normalisiert.
     */
    private List<String> extractRequestedFields(String fieldsBlock) {
        if (fieldsBlock == null || fieldsBlock.isEmpty()) { return new ArrayList<>(); }
        String normalizedString = fieldsBlock.toLowerCase().replaceAll("\\s+und\\s+", ",");
        return Arrays.stream(normalizedString.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("[^a-zäöüß0-9 -]", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Baut die finale SQL-Abfrage basierend auf der Vorlage, den Bedingungen und den angeforderten Feldern.
     * @param template Die Vorlage, die die SQL-Struktur definiert.
     * @param conditions Die Bedingungen für die WHERE-Klausel.
     * @param requestedFields Die angeforderten Felder, die in der Abfrage enthalten sein sollen.
     * @return Die generierte SQL-Abfrage als String.
     */
    private String buildQuery(AiReportTemplate template, List<String> conditions, List<String> requestedFields) {
        Map<String, AiColumnSpec> library = template.getAvailableColumns();
        Set<AiColumnSpec> selectedSpecs = new LinkedHashSet<>();
        if (requestedFields.isEmpty()) {
            selectedSpecs.addAll(library.values());
        } else {
            for (String phrase : requestedFields) {
                library.values().stream()
                        .filter(spec -> spec.matches(phrase))
                        .findFirst()
                        .ifPresent(selectedSpecs::add);
            }
        }
        if (selectedSpecs.isEmpty() && !requestedFields.isEmpty()) {
            return "-- LOKALER KI-FEHLER: Keine der angeforderten Spalten konnte zugeordnet werden.";
        }
        String columnsPart = selectedSpecs.stream()
                .map(AiColumnSpec::getSqlDefinition)
                .collect(Collectors.joining(",\n    "));

        String whereClause = String.join(" AND ", conditions);
        // Die korrekte Ersetzung beider Platzhalter
        String finalSql = template.getSqlTemplate()
                .replace("{COLUMNS}", columnsPart)
                .replace("{CONDITIONS}", whereClause);

        logger.info("🚀 Finale, dynamische SQL-Abfrage generiert:\n{}", finalSql);
        return finalSql;
    }
}
