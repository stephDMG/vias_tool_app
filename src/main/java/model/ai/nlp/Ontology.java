package model.ai.nlp;


import java.util.Map;

/**
 * Definiert die Ontologie: das Wissen über Synonyme und deren Abbildung auf semantische Felder.
 * In einer echten Anwendung würde dies aus einer JSON-Datei geladen.
 */
public class Ontology {
    private static final Map<String, String> synonymMap = Map.ofEntries(
            Map.entry("vsn", "vsn"),
            Map.entry("versicherungsschein", "vsn"),
            Map.entry("makler", "makler_nr"),           // LU_VMT
            Map.entry("makler nr", "makler_nr"),
            Map.entry("beginn", "beginn"),
            Map.entry("anfang", "beginn"),
            Map.entry("ablauf", "ablauf"),
            Map.entry("ende", "ablauf"),
            Map.entry("status", "vertragsstatus"),      // MAO.TAB_VALUE
            Map.entry("stand", "vertragsstand"),        // MAS.TAB_VALUE

            Map.entry("land", "land"),                  // V05.LU_LANDNAME

            Map.entry("land code", "land_code"),        // LUM.LU_NAT
            Map.entry("aus land", "land_code"),
            Map.entry("aus dem land", "land_code"),


            Map.entry("risiko", "risiko"),
            Map.entry("vorgang", "vorgang_id"),
            Map.entry("partner-typ", "partner_typ")

    );

    /**
     * Löst ein Token (Wort/Phrase) in ein semantisches Feld auf.
     * @param token Das vom Benutzer verwendete Wort.
     * @return Das normalisierte, semantische Feld (z.B. "makler.id") oder das Token selbst.
     */

    public String resolveField(String token) {
        return synonymMap.getOrDefault(token.toLowerCase(), token.toLowerCase());
    }
}