package gui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import java.io.IOException;

/**
 * Wiederverwendbare Statistik-Karte für das Dashboard.
 *
 * Eigenschaften:
 * - value: Hervorgehobener Zahlenwert
 * - title: Untertitel/Beschreibung
 *
 * Diese Control lädt sein Layout aus StatCard.fxml und setzt die Styleklasse
 * "stat-card", sodass es durch styles-atlantafx.css gestaltet werden kann.
 */
public class StatCardControl extends AnchorPane {

    private final StringProperty value = new SimpleStringProperty(this, "value", "-");
    private final StringProperty title = new SimpleStringProperty(this, "title", "");

    @FXML private Label valueLabel;
    @FXML private Label titleLabel;

    public StatCardControl() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/StatCard.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("StatCard.fxml konnte nicht geladen werden", e);
        }
        getStyleClass().add("stat-card");

        // Bindings
        valueLabel.textProperty().bind(this.value);
        titleLabel.textProperty().bind(this.title);
    }

    // --- Properties ---
    public final StringProperty valueProperty() { return value; }
    public final String getValue() { return value.get(); }
    public final void setValue(String v) { value.set(v); }

    public final StringProperty titleProperty() { return title; }
    public final String getTitle() { return title.get(); }
    public final void setTitle(String t) { title.set(t); }
}
