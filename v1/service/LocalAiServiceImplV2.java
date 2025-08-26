package service.impl;

import model.ai.AiColumnSpec;
import model.ai.AiReportTemplate;
import model.ai.register.AiKnowledgeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.AiService;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finale Architektur mit intelligentem Scoring und robuster, direkter Parameter-Extraktion.
 *
 * @author Coding-Assistent
 * @version 8.0 (Final)
 */
@SuppressWarnings("ALL")
public class LocalAiServiceImplV2 implements AiService {

    private static final Logger logger = LoggerFactory.getLogger(LocalAiServiceImplV2.class);

    // --- FINALE, ROBUSTE PATTERNS ---
    // Erkennt Phrasen wie "makler 123456" oder "vermittler id W123456"
    private static final Pattern MAKLER_ID_PATTERN = Pattern.compile("(?:makler|vermittler id|vermittler nr|makler mit id)\\s+([a-zA-Z0-9]{6,})", Pattern.CASE_INSENSITIVE);
    // Erkennt Phrasen wie "vsn 1234567" oder "vsn 12345678"
    private static final Pattern VSN_PATTERN = Pattern.compile("vsn\\s+((?:W\\d{7}|\\d{8}))", Pattern.CASE_INSENSITIVE);
    // Le caractère * est maintenant autorisé dans le nom
    private static final Pattern MAKLER_NAME_PATTERN =  Pattern.compile("(?:nach|für|von dem|vom|des)\\s*(?:makler|vermittler|maklers|vermittler|vermitlers)\\s*name\\s+([a-zA-Z0-9&\\s.*]+)");
    // Erkennt "sparte [spartenname]"
    private static final Pattern SPARTE_PATTERN = Pattern.compile("sparte\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
    //verträge nach land oder land-code
    private static final Pattern LAND_PATTERN = Pattern.compile("(?:land code|land)", Pattern.CASE_INSENSITIVE);
    //Pattern.compile("status\\s+([a-zA-Z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_PATTERN = Pattern.compile("(?:mit\\s+|für\\s+|bei\\s+)?status\\s+([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    // --- FELDER-TRENNUNG ---
    private static final Pattern FIELDS_SEPARATOR_PATTERN =
            Pattern.compile(
            "\\s+(?:und|für\\s+)?(?:"
                    + "mit\\s+(?:den|dem|die|alle|allen)?\\s*(?:feldern|felder|feld|spalten|spalte)"
                    + "|feldern|felder|feld|spalten|spalte"
                    + "|zeige\\s+mir(?:\\s+(?:die|den|dem|alle|allen)?\\s*(?:feldern|felder|feld|spalten|spalte)?)?"
                    + "|und\\s+zwar(?:\\s+(?:die|den|dem|alle|allen)?\\s*(?:feldern|felder|feld|spalten|spalte)?)?"
                    + ")\\b:?\\s*",
            Pattern.CASE_INSENSITIVE
    );
    // NEU: Eine Liste von Schlüsselwörtern, die für Hauptparameter reserviert sind und ignoriert werden sollen.
    private static final Set<String> MAIN_PARAMETER_KEYWORDS = Set.of(
            "makler", "vermittler", "makler-id", "makler id", "makler nr", "vermittler id",
            "makler name", "vermittler name", "maklername", "vermittlername",
            "vsn", "versicherungsschein", "sparte", "land", "land code"
    );

    @Override
    public String generateQuery(String description) {
        // Normalisieren der Beschreibung: Entfernen von überflüssigen Leerzeichen und Trim
        // FINALE KORREKTUR: Normalisiert Leerräume UND Anführungszeichen
        String normalizedDescription = description
                .replace('\u00A0', ' ')   // NBSP → normales Leerzeichen
                .replace('\u202F', ' ')   // schmaler NBSP → normales Leerzeichen
                .replaceAll("\\s+", " ")  // alle Leerraumfolgen vereinheitlichen
                .replace('‘', '\'')      // typografisches öffnendes Anführungszeichen
                .replace('’', '\'')      // typografisches schließendes Anführungszeichen
                .replace('`', '\'')      // Backtick
                .trim();

        logger.info("🧠 V9 AI-Engine (Final) analysiert: \"{}\"", normalizedDescription);

        String criteriaBlock = normalizedDescription;
        String fieldsBlock = "";
        // Trennen des Kriterienblocks und des Felderblocks
        Matcher separatorMatcher = FIELDS_SEPARATOR_PATTERN.matcher(normalizedDescription);
        if (separatorMatcher.find()) {
            criteriaBlock = normalizedDescription.substring(0, separatorMatcher.start());
            fieldsBlock = normalizedDescription.substring(separatorMatcher.end());
        }

        logger.info("-> Kriterienblock: '{}'", criteriaBlock);
        logger.info("-> Felderblock: '{}'", fieldsBlock);

        AiReportTemplate reportTemplate = findBestReportTemplate(criteriaBlock);
        if (reportTemplate == null) { return "LOKALER KI-FEHLER: Berichtstyp nicht erkannt.";}
        logger.info("Beste Vorlage gefunden: '{}'", reportTemplate.getReportName());

        List<String> parameters = extractParameters(criteriaBlock, reportTemplate);
        if (parameters == null || parameters.isEmpty()) { return  "-- LOKALER KI-FEHLER: Hauptparameter (Makler, VSN, USW.) nicht gefunden."; }

        List<String> additionalFilters = extractAdditionalFilters(criteriaBlock, reportTemplate);
        List<String> requestedFields = extractRequestedFields(fieldsBlock);

        // Übergeben Sie die Filter als neuen Parameter an buildQuery
        return buildQuery(reportTemplate, parameters, requestedFields ,additionalFilters);
    }

    private AiReportTemplate findBestReportTemplate(String criteriaBlock) {
        String lowerCriteria = criteriaBlock.toLowerCase();

        return AiKnowledgeRegistry.getAllTemplates().stream()
                .map(template -> {
                    long keywordScore = template.getMainKeywords().stream()
                            .filter(lowerCriteria::contains)
                            .count();
                    long parameterScore = 0;
                    if (template.getReportName().contains("nach Makler Name") && MAKLER_NAME_PATTERN.matcher(lowerCriteria).find()) {
                        parameterScore = 10;
                    } else if (template.getReportName().contains("nach Makler") && MAKLER_ID_PATTERN.matcher(lowerCriteria).find()) {
                        parameterScore = 10;
                    } else if (template.getReportName().contains("nach VSN") && VSN_PATTERN.matcher(lowerCriteria).find()) {
                        parameterScore = 10;
                    } else if (template.getReportName().contains("nach Sparte") && SPARTE_PATTERN.matcher(lowerCriteria).find()) {
                        parameterScore = 10;
                    }else if(template.getReportName().contains("nach Land") && LAND_PATTERN.matcher(lowerCriteria).find()) {
                        parameterScore = 10;
                    } else if (template.getReportName().contains("nach Sparte") && SPARTE_PATTERN.matcher(lowerCriteria).find()) {
                        parameterScore = 10;
                    }

                    long totalScore = keywordScore + parameterScore;
                    return new AbstractMap.SimpleEntry<>(template, totalScore);
                })
                .filter(entry -> entry.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }


    private List<String> extractParameters(String criteriaBlock, AiReportTemplate template) {
        String lowerCriteria = criteriaBlock.toLowerCase();

        // Die Reihenfolge (spezifisch vor allgemein) ist entscheidend
        if (template.getReportName().contains("nach Makler Name")) {
            Matcher m = MAKLER_NAME_PATTERN.matcher(lowerCriteria);
            if (m.find()) {
                String maklerName = m.group(1).trim();
                String likeParameter = maklerName.replace('*', '%');
                if(maklerName.contains("*")){
                    likeParameter += "%";
                }
                return Arrays.asList(likeParameter.toUpperCase());
            }
        } else if (template.getReportName().contains("nach Sparte")) {
            Matcher maklerMatcher = MAKLER_ID_PATTERN.matcher(lowerCriteria);
            Matcher sparteMatcher = SPARTE_PATTERN.matcher(lowerCriteria);
            if (maklerMatcher.find() && sparteMatcher.find()) {
                return List.of(maklerMatcher.group(1), "%" + sparteMatcher.group(1).toUpperCase() + "%");
            }
        } else if (template.getReportName().contains("nach Makler")) {
            Matcher m = MAKLER_ID_PATTERN.matcher(lowerCriteria);
            if (m.find()) return List.of(m.group(1));
        } else if (template.getReportName().contains("nach VSN")) {
            Matcher m = VSN_PATTERN.matcher(lowerCriteria);
            if (m.find()) return List.of(m.group(1));
        } else if (template.getReportName().contains("nach Land")) {
            Matcher m = LAND_PATTERN.matcher(lowerCriteria);
            if (m.find()) {
               // String value = lowerCriteria.substring(m.end()).trim();
                String value = m.group(1) != null ? m.group(1).trim() : (m.group(2) != null ? m.group(2).trim() : null);
                if (!value.isEmpty()) {
                    value = value.toUpperCase();
                    return List.of(value, value);
                }
            }
        }

        return null;
    }

    /**
     * Extrahiert zusätzliche Filter aus dem Kriterienblock (z.B. "mit Status A").
     */
    private List<String> extractAdditionalFilters(String criteriaBlock, AiReportTemplate template) {
        List<String> filters = new ArrayList<>();
        String lowerCriteria = criteriaBlock.toLowerCase();

        for (AiColumnSpec spec : template.getAvailableColumns().values()) {
            for (String keyword : spec.getKeywords()) {
                if (MAIN_PARAMETER_KEYWORDS.contains(keyword)) {
                    continue;
                }
                // Dieses Pattern sucht nach: [Schlüsselwort] [optional =] [optional '] [WERT] [optional ']
                Pattern filterPattern = Pattern.compile("\\b" + keyword + "\\s*=?\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);
                Matcher matcher = filterPattern.matcher(lowerCriteria);

                if (matcher.find()) {
                    String value = matcher.group(1);
                    // Benutzt die saubere getDbColumn() Methode
                    String columnSql = spec.getTableAlias() + "." + spec.getDbColumn();
                    filters.add("RTRIM(LTRIM(" + columnSql + ")) = '" + value.toUpperCase() + "'");
                    break;
                }
            }
        }
        logger.info("Zusätzliche Filter extrahiert: {}", filters);
        return filters;
    }


    /**
     * Extrait les champs demandés (inclusions) depuis le bloc des colonnes.
     * Ignore proprement :
     *  - les exclusions ("ohne/außer ... Feldern ...")
     *  - les indications d’ordre en tête ("zuerst/first ...")
     *  - les conjonctions "und"
     */
    private List<String> extractRequestedFields(String fieldsBlock) {
        if (fieldsBlock == null || fieldsBlock.isBlank()) {
            return new ArrayList<>();
        }

        String raw = fieldsBlock.trim().toLowerCase();

        // 1) retirer la partie exclusions "ohne/außer ... (felder|spalten|columns)"
        raw = raw.replaceFirst("(?i)\\b(ohne|außer|except|sans)\\b\\s+(felder|feldern|spalten|columns?)\\b.*?$", "").trim();

        // 2) retirer la partie "zuerst/first ..." (pin-first)
        raw = raw.replaceFirst("(?i)(zuerst|first)\\s+.+$", "").trim();

        if (raw.isBlank()) return new ArrayList<>();

        // 3) normaliser les séparateurs ("und" => virgule)
        String normalized = raw.replaceAll("\\s+und\\s+", ",");

        // 4) splitter et nettoyer
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("[^a-zäöüß0-9 -]", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String buildQuery(AiReportTemplate template, List<String> parameters, List<String> requestedFields, List<String> additionalFilters) {
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

        String finalSql = template.getSqlTemplate().replace("{COLUMNS}", columnsPart);
        for (String param : parameters) {
            finalSql = finalSql.replaceFirst("\\?", "'" + param + "'");
        }
        // --- NEU HINZUGEFÜGTER BLOCK START ---
        if (!additionalFilters.isEmpty()) {
            String andFilters = " AND " + String.join(" AND ", additionalFilters);
            int orderByIndex = finalSql.toUpperCase().indexOf(" ORDER BY");
            if (orderByIndex != -1) {
                finalSql = new StringBuilder(finalSql).insert(orderByIndex, andFilters).toString();
            } else {
                finalSql += andFilters;
            }
        }
        // --- NEU HINZUGEFÜGTER BLOCK ENDE ---//
        logger.info("🚀 Finale, dynamische SQL-Abfrage generiert:\n{}", finalSql);
        return finalSql;
    }

    @Override
    public String optimizeQuery(String originalQuery) {
        return originalQuery;
    }
}