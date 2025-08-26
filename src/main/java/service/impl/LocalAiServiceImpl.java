package service.impl;

import model.ai.AiReportTemplate;
import model.ai.ir.QueryIR;
import model.ai.nlp.CapabilityService;
import model.ai.nlp.FeatureSwitch;
import model.ai.nlp.PromptUnderstandingService;
import model.ai.nlp.NLUnderstandingService;
import model.ai.planning.CoverExecutionService;
import model.ai.planning.LegacyCoverExecutionAdapter;
import model.ai.planning.PromptExecutionService;
import model.ai.planning.SqlPlan;
import model.ai.provider.impl.CoverKnowledgeProvider;
import model.ai.provider.impl.SchadenKnowledgeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.AiQueryBuilder;
import service.interfaces.AiService;

import java.util.stream.Collectors;

public class LocalAiServiceImpl implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(LocalAiServiceImpl.class);

    // Legacy-Experten als Fallback
    private final AiQueryBuilder legacyCoverBuilder = new CoverQueryBuilder();
    private final AiQueryBuilder legacySchadenBuilder = new SchadenQueryBuilder();

    // Neue Architektur-Komponenten
    private final PromptUnderstandingService understandingService = new NLUnderstandingService();
    private final PromptExecutionService coverExecutionService;
    private final LegacyCoverExecutionAdapter legacyAdapter;


    public LocalAiServiceImpl() {
        // Hole das Template aus dem bestehenden Provider
        AiReportTemplate coverTemplate = new CoverKnowledgeProvider().getReportTemplates().get(0);
        this.coverExecutionService = new CoverExecutionService(coverTemplate);
        this.legacyAdapter = new LegacyCoverExecutionAdapter(legacyCoverBuilder);
    }

    @Override
    public String generateQuery(String prompt) {
        logger.info("🧠 KI-Engine analysiert: \"{}\"", prompt);

        // 1. VERSTEHEN: Die Anfrage wird in eine strukturierte Form (IR) umgewandelt.
        PromptUnderstandingService.UnderstandingResult res = understandingService.understand(prompt);

        // 2. FÄHIGKEITEN?: Prüfen, ob der Benutzer "was kannst du?" gefragt hat.
        if (res.isCapabilitiesIntent) {
            return CapabilityService.renderCapabilities();
        }

        // 3. FEHLERBEHANDLUNG: Prüfen, ob die Anfrage klar genug war.
        if (res.status != PromptUnderstandingService.UnderstandingResult.Status.OK) {
            logger.warn("Anfrage nicht eindeutig verstanden (Status: {}). Versuche Fallback...", res.status);

            // Versuche, ob der Legacy-Builder vielleicht doch etwas damit anfangen kann
            if (legacyCoverBuilder.canHandle(prompt)) {
                String legacySql = legacyCoverBuilder.generateQuery(prompt);
                if (legacySql != null && !legacySql.isBlank() && !legacySql.startsWith("--")) {
                    logger.info("-> Fallback auf Legacy-Engine war erfolgreich.");
                    return legacySql;
                }
            }
            // Wenn auch Legacy nicht helfen kann, gib den formatierten Hilfskommentar zurück
            logger.warn("-> Fallback fehlgeschlagen. Generiere Hilfetext.");
            return buildHelpfulComment(res);
        }

        // 4. ROUTING: Entscheiden, ob der neue oder der alte Motor verwendet wird.
        boolean useAdvancedEngine = FeatureSwitch.detectAdvancedFeatures(prompt);
        SqlPlan plan;

        if (useAdvancedEngine) {
            logger.info("-> Route: Neuer 'Intelligence +++' Motor wird verwendet.");
            plan = coverExecutionService.plan(res.ir);
        } else {
            logger.info("-> Route: Legacy-Motor wird verwendet.");
            // Hier wählen wir den richtigen Legacy-Builder
            if (legacyCoverBuilder.canHandle(prompt)) {
                plan = new LegacyCoverExecutionAdapter(legacyCoverBuilder).planFromLegacy(prompt);
            } else if (legacySchadenBuilder.canHandle(prompt)) {
                plan = new LegacyCoverExecutionAdapter(legacySchadenBuilder).planFromLegacy(prompt);
            } else {
                return buildHelpfulComment(res); // Sicherheitsnetz
            }
        }

        // 5. FINALES ERGEBNIS: Gib das SQL zurück oder eine Fehlermeldung, wenn der Plan leer ist.
        if (plan == null || plan.sql == null || plan.sql.isBlank()) {
            logger.error("Der Planer hat ein leeres SQL-Ergebnis zurückgegeben, obwohl die Anfrage OK war.");
            return buildHelpfulComment(res);
        }

        logger.info("🚀 Generierter SQL-Plan:\n{}", plan.sql);
        if(!plan.params.isEmpty()){
            logger.info("Parameter für PreparedStatement: {}", plan.params);
        }

        return plan.sql;
    }

    /**
     * Baut einen formatierten SQL-Kommentar als Hilfestellung.
     */
    private String buildHelpfulComment(PromptUnderstandingService.UnderstandingResult r) {
        String details = String.format("Kontext=%s, Gefundene Filter=%d",
                r.ir.context,
                r.ir.filters.stream().mapToInt(fg -> fg.predicates.size()).sum()
        );

        String suggestions = r.suggestions.stream()
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n"));

        return String.format(
                "-- KI-Hinweis: Anfrage unklar oder zu allgemein (Status: %s, Konfidenz: %.2f).\n" +
                        "-- Erkannte Details: %s\n" +
                        "-- Vorschläge zur Verbesserung:\n%s",
                r.status, r.confidence, details, suggestions
        );
    }

    @Override
    public String optimizeQuery(String originalQuery) {
        return originalQuery;
    }
}