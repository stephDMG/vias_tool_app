package model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * Die Klasse RowData repräsentiert eine Zeile von Daten, die aus einer Datenbankabfrage stammen.
 * Es speichert die Spaltennamen und ihre zugehörigen Werte in einer geordneten Map.
 * Diese Klasse wird verwendet, um die Ergebnisse von Datenbankabfragen zu speichern und zu verarbeiten.
 * Die Werte werden in der Reihenfolge gespeichert, in der sie hinzugefügt wurden, was für die Ausgabe in CSV-Format nützlich ist.
 * Die Klasse bietet Methoden zum Hinzufügen von Werten, Abrufen der gespeicherten Werte und Konvertieren der Werte in ein Array.
 * </p>
 *
 * @author Stephane Dongmo
 * @version 1.0
 * @since 07/07/2025
 */
public class RowData {
    /**
     * Die Map, die die Spaltennamen und ihre zugehörigen Werte speichert.
     */
    private final Map<String, String> values = new LinkedHashMap<>();


    /**
     * Fügt einen Wert für eine bestimmte Spalte hinzu.
     * Zum Beispiel: put("Name", "John Doe"); -> fügt den Wert "John Doe" für die Spalte "Name" hinzu.
     *
     * @param column der Name der Spalte, für die der Wert hinzugefügt wird
     * @param value  der Wert, der für die Spalte hinzugefügt wird
     */
    public void put(String column, String value) {
        values.put(column.trim(), value.trim());
    }

    //putAll Methode
    public void putAll(Map<String, String> map) {
        values.putAll(map);
    }

    /**
     * Gibt die gespeicherten Werte als Map zurück.
     * Diese Map enthält die Spaltennamen als Schlüssel (String) und die zugehörigen Werte (String) als Werte.
     *
     * @return eine Map, die die Spaltennamen und ihre zugehörigen Werte enthält
     */
    public Map<String, String> getValues() {
        return values;
    }

    /**
     * Gibt die gespeicherten Werte in der Reihenfolge zurück, in der sie hinzugefügt wurden.
     * Diese Methode konvertiert die Map-Werte in ein Array von Strings.
     *
     * @return ein Array von Strings, das die Werte in der Reihenfolge enthält, in der sie hinzugefügt wurden
     */
    public String[] getOrderedValues() {
        return values.values().toArray(new String[0]);
    }

    /**
     * Gibt die gespeicherten Werte als String zurück.
     * Diese Methode gibt eine String-Darstellung der Map zurück, die die Spaltennamen und ihre zugehörigen Werte enthält.
     *
     * @return eine String-Darstellung der gespeicherten Werte
     */
    @Override
    public String toString() {
        return values.toString();
    }
}