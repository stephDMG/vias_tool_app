package model.ai.nlp;

import model.ai.ir.ContextType;
import model.ai.ir.QueryIR;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class NLUnderstandingService implements PromptUnderstandingService {

    private final NLParser parser = new NLParser();

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "alle","zeig","zeige","mir","bitte","die","der","den","vom","von","für","mit",
            "und","oder","aber","auch","schnell","mal","dann","zuerst","first","außer","ohne",
            "limit","order","by","sortiere","ordne","nach","cover","vertrag","verträge"
    ));
    private static final Pattern TOKENIZER = Pattern.compile("[^a-zA-Z0-9äöüßÄÖÜ]+");

    @Override
    public UnderstandingResult understand(String prompt) {
        UnderstandingResult r = new UnderstandingResult();
        String s = prompt == null ? "" : prompt.trim();
        r.isCapabilitiesIntent = CapabilityDetector.isCapabilitiesQuery(s);

        if (r.isCapabilitiesIntent) {
            r.status = UnderstandingResult.Status.OK;
            r.confidence = 1.0;
            r.ir = new QueryIR();
            return r;
        }

        // parse en IR (peut être partiel)
        r.ir = parser.parse(s);

        // heuristique de "filtres forts" (à enrichir au besoin)
        boolean hasStrong =
                (r.ir != null && r.ir.filters != null && !r.ir.filters.isEmpty())  // ex. makler id
                        || (s.toLowerCase().contains("vsn "))                              // VSN 4711
                        || s.toLowerCase().matches(".*\\bbeginn\\b.*\\bzwischen\\b.*\\bund\\b.*") // période
                        || s.toLowerCase().matches(".*\\bablauf\\b.*\\bzwischen\\b.*\\bund\\b.*");

        // détection de contexte ambigu/inconnu
        ContextType ctx = (r.ir != null ? r.ir.context : ContextType.UNKNOWN);
        boolean contextKnown = (ctx == ContextType.COVER || ctx == ContextType.SCHADEN);

        // tokens inconnus (pour suggestions UI)
        for (String t : TOKENIZER.split(s.toLowerCase())) {
            if (t.isBlank()) continue;
            if (!STOPWORDS.contains(t)) {
                // heuristique: on laisse passer nombres et ids courtes
                if (t.matches("\\d{1,}")) continue;
                if (t.length() <= 2) continue;
                r.unknownTokens.add(t);
            }
        }

        // statut + confiance
        if (!contextKnown) {
            r.status = UnderstandingResult.Status.INVALID;
            r.confidence = 0.2;
        } else if (!hasStrong) {
            // ex. "alle cover" -> refuse (sécurité)
            r.status = UnderstandingResult.Status.INVALID;
            r.confidence = 0.5;
        } else {
            r.status = UnderstandingResult.Status.OK;
            r.confidence = 0.8;
        }

        // suggestions prêtes à afficher (DE)
        if (r.status != UnderstandingResult.Status.OK) {
            r.suggestions.add("Makler-ID hinzufügen (z. B. 100120)");
            r.suggestions.add("VSN angeben (z. B. 4711)");
            r.suggestions.add("Zeitraum einschränken: Beginn zwischen 01.01.2024 und 31.12.2024");
            r.suggestions.add("Spalten ausschließen: außer Land, Firma");
            r.suggestions.add("zuerst VSN, Makler");
            r.suggestions.add("Limit 500");
            r.suggestions.add("Status außer storniert");
        }
        return r;
    }
}