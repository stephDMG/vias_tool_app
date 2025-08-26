package model.ai.provider.interfaces;

import model.ai.AiReportTemplate;

import java.util.List;

/**
 * Ein Interface für Wissensanbieter. Jede Klasse, die dieses Interface implementiert,
 * kann der KI-Engine eine Liste von Berichtsvorlagen zur Verfügung stellen.
 */
public interface AiKnowledgeProvider {
    List<AiReportTemplate> getReportTemplates();
}
