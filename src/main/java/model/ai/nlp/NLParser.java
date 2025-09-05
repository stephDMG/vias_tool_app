package model.ai.nlp;

import model.ai.ir.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NLParser {
    // --- PATTERNS (RegEx) ---
    private static final Pattern FIELDS_SEPARATOR =
            Pattern.compile("\\s+(?:und\\s+zwar|mit\\s+den\\s+feldern|mit\\s+feldern|mit\\s+der\\s+feldern|zeige\\s+mir|zeige)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY_SEPARATOR = Pattern.compile("\\s+(?:order\\s+by|sortiert\\s+nach)\\b", Pattern.CASE_INSENSITIVE);
    // FIX: Verbesserte Pattern für globale Projektionen - weniger gierig
    private static final Pattern AUSSER_GLOBAL =
            Pattern.compile("(?:außer|ohne|except)\\s+([a-zA-Zäöüß0-9 _,-]+?)(?=\\s+(?:order|limit|und\\s|$))", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZUERST_GLOBAL =
            Pattern.compile("(?:zuerst|first)\\s+([a-zA-Zäöüß0-9 _,-]+?)(?=\\s+(?:order|limit|und\\s|$))", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEGINN_AFTER = Pattern.compile(
            "(?:beginn|anfang)\\s*(?:nach|seit|ab)(?:\\s+dem)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BEGINN_BEFORE = Pattern.compile(
            "(?:beginn|anfang)\\s*(?:vor|bis)(?:\\s+zum)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BEGINN_RANGE = Pattern.compile(
            "(?:beginn|anfang)\\s*zwischen\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})\\s+und\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    // NEU: Patterns für Vertragsende (Ablauf)
    private static final Pattern ABLAUF_AFTER = Pattern.compile(
            "(?:ablauf|ende)\\s*(?:nach|seit|ab)(?:\\s+dem)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ABLAUF_BEFORE = Pattern.compile(
            "(?:ablauf|ende)\\s*(?:vor|bis)(?:\\s+zum)?\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ABLAUF_RANGE = Pattern.compile(
            "(?:ablauf|ende)\\s*zwischen\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})\\s+und\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LIMIT = Pattern.compile("\\blimit\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    // KORREKTUR: MAKLER_NAME-RegEx, um Überlappungen zu vermeiden (name ist jetzt obligatorisch).
    //private static final Pattern MAKLER_ID = Pattern.compile("\\b(makler|vermittler)(?:\\s+id)?\\s+([a-zA-Z0-9]{5,})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAKLER_ID = Pattern.compile("(?:makler|vermittler)(?:\\s+(id|nr))?\\s+([a-zA-Z0-9]{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAKLER_NAME = Pattern.compile("\\b(makler|vermittler)\\s+name\\s+([a-zA-Z0-9&.\\s*äöüßÄÖÜ]+?)(?=\\s+und\\b|\\s+mit\\b|\\s+order\\b|\\s+limit\\b|$)", Pattern.CASE_INSENSITIVE);
    // KORREKTUR: LAND-RegEx, um NullPointer zu vermeiden und spezifischer zu sein.
    private static final Pattern LAND = Pattern.compile("\\bland\\s+([a-zA-Zäöüß]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS = Pattern.compile("\\bstatus\\s+'?([a-zA-Z]+)'?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VSN = Pattern.compile("\\bvsn\\s+([a-zA-Z0-9-]+)\\b", Pattern.CASE_INSENSITIVE);
    private final Ontology ontology = new Ontology();

    public QueryIR parse(String prompt) {
        QueryIR ir = new QueryIR();
        if (prompt == null || prompt.isBlank()) return ir;

        String mutablePrompt = " " + prompt.toLowerCase() + " ";

        if (mutablePrompt.contains(" cover ") || mutablePrompt.contains(" cover,") || mutablePrompt.contains(" cover.") ||
                mutablePrompt.contains("vertrag") || mutablePrompt.contains("verträge") || mutablePrompt.contains("vertraege")) {
            ir.context = ContextType.COVER;
        } else if (mutablePrompt.contains("schaden") || mutablePrompt.contains("sva")) {
            ir.context = ContextType.SCHADEN;
        }

        // FIX: Globale Projektionen müssen VOR lokalen Projektionen verarbeitet werden
        mutablePrompt = extractSort(mutablePrompt, ir);
        mutablePrompt = extractLimit(mutablePrompt, ir);
        mutablePrompt = extractGlobalProjections(mutablePrompt, ir);
        mutablePrompt = extractProjections(mutablePrompt, ir);

        extractAllFilters(mutablePrompt, ir);

        return ir;
    }

    private String extractSort(String prompt, QueryIR ir) {
        Matcher m = ORDER_BY_SEPARATOR.matcher(prompt);
        if (m.find()) {
            String sortBlock = prompt.substring(m.end());
            Arrays.stream(sortBlock.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(s -> {
                        String field = s.replaceAll("\\b(asc|desc|aufsteigend|absteigend)\\b", "").trim();
                        Direction dir = s.contains("desc") || s.contains("absteigend") ? Direction.DESC : Direction.ASC;
                        ir.sortOrders.add(new Sort(ontology.resolveField(field), dir));
                    });
            return prompt.substring(0, m.start());
        }
        return prompt;
    }

    private String extractProjections(String prompt, QueryIR ir) {
        Matcher m = FIELDS_SEPARATOR.matcher(prompt);
        if (m.find()) {
            String fieldsBlock = prompt.substring(m.end());

            // Zuerst die 'zuerst'-Klausel extrahieren und den String bereinigen
            Matcher zx = Pattern.compile("(?:zuerst|first)\\s+([a-zA-Zäöüß0-9 _,-]+)").matcher(fieldsBlock);
            if (zx.find()) {
                Arrays.stream(zx.group(1).split("[,]|\\s+und\\s+"))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .forEach(f -> {
                            Projection p = new Projection(ontology.resolveField(f), false);
                            p.order = 0;
                            ir.projections.add(p);
                        });
                fieldsBlock = zx.replaceAll(" "); // Ersetzt die Klausel durch Leerzeichen
            }

            if (fieldsBlock.contains("makler nr")) {
                ir.projections.add(new Projection(ontology.resolveField("makler nr"), false));
                fieldsBlock = fieldsBlock.replace("makler nr", "");
            }

            // Dann die 'außer'-Klausel extrahieren
            Matcher ax = Pattern.compile("(?:außer|ohne|except)\\s+([a-zA-Zäöüß0-9 _,-]+)").matcher(fieldsBlock);
            if (ax.find()) {
                Arrays.stream(ax.group(1).split("[,]|\\s+und\\s+"))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .forEach(f -> ir.projections.add(new Projection(ontology.resolveField(f), true)));
                fieldsBlock = ax.replaceAll(" ");
            }

            // Zum Schluss die verbleibenden Felder verarbeiten
            Arrays.stream(fieldsBlock.split("[,]|\\s+und\\s+")).map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(f -> ir.projections.add(new Projection(ontology.resolveField(f), false)));

            return prompt.substring(0, m.start());
        }
        return prompt;
    }

    // Korrektur: Eine einfachere und robustere Methode zur String-Manipulation
    private String extractGlobalProjections(String prompt, QueryIR ir) {
        // 1. Zuerst die 'zuerst'-Klausel verarbeiten
        Matcher zx = ZUERST_GLOBAL.matcher(prompt);
        if (zx.find()) {
            String fieldsList = zx.group(1).trim();
            Arrays.stream(fieldsList.split("[,]|\\s+und\\s+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(f -> {
                        Projection p = new Projection(ontology.resolveField(f), false);
                        p.order = 0;
                        ir.projections.add(p);
                    });
            prompt = zx.replaceAll(" ");
        }

        // 2. Dann die 'außer'-Klausel verarbeiten
        Matcher ax = AUSSER_GLOBAL.matcher(prompt);
        if (ax.find()) {
            String fieldsList = ax.group(1).trim();
            Arrays.stream(fieldsList.split("[,]|\\s+und\\s+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(f -> ir.projections.add(new Projection(ontology.resolveField(f), true)));
            prompt = ax.replaceAll(" ");
        }

        // Gibt den bereinigten Prompt-String zurück
        return prompt;
    }

    private String extractLimit(String prompt, QueryIR ir) {
        Matcher l = LIMIT.matcher(prompt);
        if (l.find()) {
            ir.limit = Integer.parseInt(l.group(1));
            return prompt.substring(0, l.start()) + prompt.substring(l.end());
        }
        return prompt;
    }

    private void extractAllFilters(String criteria, QueryIR ir) {
        applyFilter(criteria, MAKLER_ID, (m) -> ir.addPredicate(new Predicate(ontology.resolveField("makler nr"), Op.EQUALS, m.group(2).trim())));
        applyFilter(criteria, MAKLER_NAME, (m) -> ir.addPredicate(new Predicate(ontology.resolveField("makler name"), Op.LIKE, m.group(2).replace("*", "%").trim())));
        applyFilter(criteria, VSN, (m) -> ir.addPredicate(new Predicate(ontology.resolveField("vsn"), Op.EQUALS, m.group(1).trim())));
        applyFilter(criteria, LAND, (m) -> ir.addPredicate(new Predicate(ontology.resolveField("land"), Op.EQUALS, m.group(1).trim())));
        applyFilter(criteria, STATUS, (m) -> ir.addPredicate(new Predicate(ontology.resolveField("status"), Op.EQUALS, m.group(1).trim())));

        applyFilter(criteria, BEGINN_RANGE, (m) -> {
            List<String> dates = List.of(m.group(1).trim(), m.group(2).trim());
            ir.addPredicate(new Predicate("beginn", Op.BETWEEN, dates));
        });
        applyFilter(criteria, BEGINN_AFTER, (m) -> ir.addPredicate(new Predicate("beginn", Op.GREATER_THAN_OR_EQUAL, m.group(1).trim())));
        applyFilter(criteria, BEGINN_BEFORE, (m) -> ir.addPredicate(new Predicate("beginn", Op.LESS_THAN_OR_EQUAL, m.group(1).trim())));
        applyFilter(criteria, ABLAUF_RANGE, (m) -> {
            List<String> dates = List.of(m.group(1).trim(), m.group(2).trim());
            ir.addPredicate(new Predicate("ablauf", Op.BETWEEN, dates));
        });
        applyFilter(criteria, ABLAUF_AFTER, (m) -> ir.addPredicate(new Predicate("ablauf", Op.GREATER_THAN_OR_EQUAL, m.group(1).trim())));
        applyFilter(criteria, ABLAUF_BEFORE, (m) -> ir.addPredicate(new Predicate("ablauf", Op.LESS_THAN_OR_EQUAL, m.group(1).trim())));

    }

    private void applyFilter(String text, Pattern pattern, Consumer<Matcher> action) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            action.accept(matcher);
        }
    }
}