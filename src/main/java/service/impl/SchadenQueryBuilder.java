package service.impl;

import model.ai.AiColumnSpec;
import model.ai.AiReportTemplate;
import model.ai.register.AiKnowledgeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.AiQueryBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class SchadenQueryBuilder implements AiQueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SchadenQueryBuilder.class);

    // Patterns, um Filter in der Anfrage zu erkennen
    private static final Pattern MAKLER_ID_PATTERN = Pattern.compile("(?:für den makler|makler)\\s+([a-zA-Z0-9]{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_PATTERN = Pattern.compile("status\\s+'?([a-zA-Z0-9]+)'?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHADEN_NR_PATTERN = Pattern.compile("(?:schaden-nr|schadennummer|snr)\\s+([a-zA-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

    // Das gleiche Pattern wie im CoverQueryBuilder, um den "Felder"-Teil zu trennen
    private static final Pattern FIELDS_SEPARATOR_PATTERN =
            Pattern.compile(
                    "\\s+(?:und|für\\s+)?(?:"
                            + "mit\\s+(?:den|dem|die|alle|allen)?\\s*(?:feldern|felder|feld|spalten|spalte)"
                            + "|feldern|felder|feld|spalten|spalte"
                            + "|zeige\\s+mir(?:\\s+(?:die|den|dem|alle|allen)?\\s*(?:feldern|felder|feld|spalten|spalte)?)?"
                            + "|und\\s+zwar(?:\\s+(?:die|den|dem|alle|allen)?\\s*(?:feldern|felder|feld|spalten|spalte)?)?"
                            + ")\\b:?\\s*",
                    Pattern.CASE_INSENSITIVE);

    @Override
    public boolean canHandle(String description) {
        String lowerDesc = description.toLowerCase();
        return lowerDesc.contains("schaden") || lowerDesc.contains("schäden") || lowerDesc.contains("sva");
    }

    @Override
    public String generateQuery(String description) {
        String normalizedDescription = description.replaceAll("[\\s‘`’]", " ").replaceAll("\\s+", " ").trim();
        logger.info("🧠 SchadenQueryBuilder analysiert: \"{}\"", normalizedDescription);

        // Trenne Kriterien- und Felder-Block
        String criteriaBlock = normalizedDescription;
        String fieldsBlock = "";
        Matcher separatorMatcher = FIELDS_SEPARATOR_PATTERN.matcher(normalizedDescription);
        if (separatorMatcher.find()) {
            criteriaBlock = normalizedDescription.substring(0, separatorMatcher.start());
            fieldsBlock = normalizedDescription.substring(separatorMatcher.end());
        }

        // Finde die passende Vorlage aus der Registry
        AiReportTemplate template = AiKnowledgeRegistry.getAllTemplates().stream()
                .filter(t -> t.getReportName().equals("Dynamischer Schaden-Bericht"))
                .findFirst()
                .orElse(null);

        if (template == null) {
            return "-- LOKALER KI-FEHLER: Keine passende Berichtsvorlage für Schäden gefunden.";
        }

        List<String> conditions = buildWhereClause(criteriaBlock);
        if (conditions.isEmpty()) {
            return "-- LOKALER KI-FEHLER: Keine gültigen Filterkriterien (wie Makler, Status etc.) für Schäden gefunden.";
        }

        List<String> requestedFields = extractRequestedFields(fieldsBlock);
        return buildQuery(template, conditions, requestedFields);
    }

    private List<String> buildWhereClause(String criteriaBlock) {
        List<String> conditions = new ArrayList<>();
        String remainingCriteria = criteriaBlock.toLowerCase();

        // Extrahiere Makler-ID
        Matcher maklerIdMatcher = MAKLER_ID_PATTERN.matcher(remainingCriteria);
        if (maklerIdMatcher.find()) {
            conditions.add("RTRIM(LTRIM(LS.LU_VMT)) = '" + maklerIdMatcher.group(1).toUpperCase() + "'");
            remainingCriteria = maklerIdMatcher.replaceAll("");
        }

        // Extrahiere Status
        Matcher statusMatcher = STATUS_PATTERN.matcher(remainingCriteria);
        if (statusMatcher.find()) {
            conditions.add("RTRIM(LTRIM(LS.LU_SVSTATUS)) = '" + statusMatcher.group(1).toUpperCase() + "'");
            remainingCriteria = statusMatcher.replaceAll("");
        }

        // Extrahiere Schaden-Nr
        Matcher schadenNrMatcher = SCHADEN_NR_PATTERN.matcher(remainingCriteria);
        if (schadenNrMatcher.find()) {
            conditions.add("RTRIM(LTRIM(LS.LU_SNR)) = '" + schadenNrMatcher.group(1).toUpperCase() + "'");
            remainingCriteria = schadenNrMatcher.replaceAll("");
        }

        logger.info("Gefundene WHERE-Bedingungen für Schäden: {}", conditions);
        return conditions;
    }

    // Die folgenden beiden Methoden können direkt aus CoverQueryBuilder kopiert werden,
    // da sie für beide Experten identisch sind.

    private List<String> extractRequestedFields(String fieldsBlock) {
        if (fieldsBlock == null || fieldsBlock.isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedString = fieldsBlock.toLowerCase().replaceAll("\\s+und\\s+", ",");
        return Arrays.stream(normalizedString.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("[^a-zäöüß0-9 -]", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String buildQuery(AiReportTemplate template, List<String> conditions, List<String> requestedFields) {
        Map<String, AiColumnSpec> library = template.getAvailableColumns();
        Set<AiColumnSpec> selectedSpecs = new LinkedHashSet<>();

        if (requestedFields.isEmpty()) {
            // Wähle ALLE verfügbaren Spalten aus der Wissensbibliothek aus,
            // wenn der Benutzer keine spezifischen Spalten anfordert.
            selectedSpecs.addAll(library.values());
        } else {
            // Wenn der Benutzer Spalten angibt, suche die passenden.
            for (String phrase : requestedFields) {
                library.values().stream()
                        .filter(spec -> spec.matches(phrase))
                        .findFirst()
                        .ifPresent(selectedSpecs::add);
            }
        }

        if (selectedSpecs.isEmpty()) {
            // Diese Bedingung fängt beide Fälle ab:
            // 1. Keine Standardspalten definiert (unwahrscheinlich).
            // 2. Keine der angeforderten Spalten konnte zugeordnet werden.
            return "-- LOKALER KI-FEHLER: Keine der angeforderten Spalten für Schäden konnte zugeordnet werden.";
        }

        String columnsPart = selectedSpecs.stream()
                .map(AiColumnSpec::getSqlDefinition)
                .collect(Collectors.joining(",\n    "));

        String whereClause = String.join(" AND ", conditions);

        String finalSql = template.getSqlTemplate()
                .replace("{COLUMNS}", columnsPart)
                .replace("{CONDITIONS}", whereClause);

        logger.info("🚀 Finale Schaden-SQL-Abfrage generiert:\n{}", finalSql);
        return finalSql;
    }
}