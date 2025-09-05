package model.ai.register;

import model.ai.AiReportTemplate;
import model.ai.provider.impl.CoverKnowledgeProvider;
import model.ai.provider.impl.SchadenKnowledgeProvider;
import model.ai.provider.interfaces.AiKnowledgeProvider;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Eine zentrale Registrierung, die alle verfügbaren Wissensanbieter (AiKnowledgeProvider) kennt.
 * Dient als einzige Anlaufstelle für die KI-Engine.
 */
public class AiKnowledgeRegistry {

    // Hier registrieren wir alle unsere "Experten"
    private static final List<AiKnowledgeProvider> providers = List.of(
            new CoverKnowledgeProvider(),
            new SchadenKnowledgeProvider()
    );

    /**
     * Sammelt und liefert alle Berichtsvorlagen von allen registrierten Anbietern.
     *
     * @return Eine kombinierte Liste aller verfügbaren AiReportTemplates.
     */
    public static List<AiReportTemplate> getAllTemplates() {
        return providers.stream()
                .flatMap(provider -> provider.getReportTemplates().stream())
                .collect(Collectors.toList());
    }
}