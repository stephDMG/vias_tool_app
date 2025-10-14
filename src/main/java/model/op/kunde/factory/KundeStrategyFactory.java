package model.op.kunde.factory;

import model.op.kunde.IKundeStrategy;
import model.op.kunde.fivestar.FiveStar;
import model.op.kunde.gateway.Gateway;
import model.op.kunde.hartrodt.Hartrodt;
import model.op.kunde.saco.Saco;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Eine Factory-Klasse, die als zentrales Register für alle verfügbaren
 * {@link IKundeStrategy}-Implementierungen dient.
 * Sie entkoppelt die Benutzeroberfläche (GUI) von den konkreten Strategieklassen.
 * Die GUI muss nur den Namen des Kunden kennen, nicht die spezifische Klasse, die ihn behandelt.
 */
public class KundeStrategyFactory {

    private final Map<String, IKundeStrategy> strategyMap;

    /**
     * Konstruktor, der alle verfügbaren Kundenstrategien initialisiert und registriert.
     * <p>
     * UM EINEN NEUEN KUNDEN HINZUZUFÜGEN:
     * Erstellen Sie einfach eine neue Strategieklasse und fügen Sie hier eine Zeile
     * `strategyMap.put("KundenName", new IhreNeueKlasse());` hinzu.
     */
    public KundeStrategyFactory() {
        strategyMap = new HashMap<>();
        strategyMap.put("Hartrodt", new Hartrodt());
        strategyMap.put("Fivestar", new FiveStar());
        strategyMap.put("Gateway", new Gateway());
        strategyMap.put("Saco", new Saco());
        // Fügen Sie hier zukünftige Kunden hinzu...
    }

    /**
     * Ruft die entsprechende Strategie basierend auf dem vom Benutzer ausgewählten Kundennamen ab.
     *
     * @param kundeName Der Name des Kunden (z. B. "Hartrodt"), wie er in der GUI angezeigt wird.
     * @return Eine Instanz von {@link IKundeStrategy}, die dem Namen entspricht, oder {@code null},
     * wenn kein passender Kunde gefunden wird.
     */
    public IKundeStrategy getStrategy(String kundeName) {
        return strategyMap.get(kundeName);
    }

    /**
     * Gibt eine Liste aller registrierten Kundennamen zurück.
     * Dies ist nützlich, um die Dropdown-Liste in der GUI dynamisch zu füllen.
     *
     * @return Ein Set<String> mit allen registrierten Kundennamen.
     */
    public Set<String> getAllKundeNames() {
        return strategyMap.keySet();
    }
}