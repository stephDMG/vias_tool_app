package gui;


import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.scene.layout.Panel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;


/**
 * Startpunkt der JavaFX-Oberfläche des VIAS Export Tools.
 * <p>
 * Diese Klasse initialisiert die JavaFX-Stage, lädt das Hauptlayout aus
 * {@code /fxml/MainWindow.fxml}, wendet BootstrapFX sowie das benutzerdefinierte
 * Stylesheet {@code /css/app-design.css} an und setzt u. a. Fenstertitel, Mindestgrößen
 * und das App-Icon (sofern unter {@code /images/logo.png} verfügbar).
 * </p>
 * <p>
 * Typische Verwendung:
 * <pre>
 *     public static void main(String[] args) {
 *         VIASGuiApplication.main(args);
 *     }
 * </pre>
 * Oder programmatisch mittels {@link #launchGui()} aus einem anderen Thread.
 * </p>
 */
public class VIASGuiApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(VIASGuiApplication.class);

    /**
     * Hauptmethode zum Starten der GUI im Standardmodus.
     * Delegiert an {@link Application#launch(String...)}.
     *
     * @param args Kommandozeilenargumente
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Startet die GUI-Anwendung programmatisch in einem separaten Thread.
     * Nützlich, wenn die GUI aus einer anderen Anwendungsschicht gestartet werden soll (z. B. Konsole).
     */
    public static void launchGui() {
        new Thread(Application::launch).start();
    }

    /**
     * Startet die JavaFX-Anwendung und initialisiert die Hauptbühne (Stage).
     * Lädt das FXML-Hauptfenster, bindet Stylesheets ein und setzt Icon, Titel und Mindestgröße.
     * Bei Fehlern wird protokolliert und der Stacktrace ausgegeben.
     *
     * @param primaryStage die Hauptbühne, die von der JavaFX-Laufzeit übergeben wird
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("🚀 VIAS GUI-Anwendung wird gestartet...");

            Panel panel = new Panel("Willkommen bei VIAS Export Tool");
            panel.getStyleClass().add("panel-primary");                            //(2)
            BorderPane content = new BorderPane();

            panel.setBody(content);

            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

            java.net.URL fxmlLocation = getClass().getResource("/fxml/MainWindow.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            BorderPane root = loader.load();

            // Scene erstellen
            Scene scene = new Scene(root, 1200, 800);

            // App-spezifisches Stylesheet NACH dem UA-Stylesheet von AtlantisFX laden,
            // damit unsere Regeln die Standard-Themes gezielt überschreiben können.
            java.net.URL cssLocation = getClass().getResource("/css/styles-atlantafx.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            } else {
                logger.warn("⚠️ Stylesheet nicht gefunden: /css/styles-atlantafx.css");
            }

            // Stage konfigurieren
            primaryStage.setTitle("VIAS Export Tool");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            try (InputStream iconStream = getClass().getResourceAsStream("/images/logo.png")) {
                if (iconStream != null) {
                    primaryStage.getIcons().add(new Image(iconStream));
                } else {
                    logger.warn("⚠️ Icon-Datei nicht gefunden! Pfad: /images/logo.png");
                }
            }
            primaryStage.show();

            logger.info("✅ GUI erfolgreich gestartet");
        } catch (Exception e) {
            logger.error("❌ Fehler beim Starten der GUI", e);
            e.printStackTrace();
        }
    }

    /**
     * Lifecycle-Hook bei Beendigung der Anwendung.
     * Nutzt das zentrale Logging, um das ordnungsgemäße Herunterfahren zu dokumentieren.
     */
    @Override
    public void stop() throws Exception {
        logger.info("🛑 VIAS GUI-Anwendung wird beendet");
        super.stop();
        System.exit(0); // Sécurité supplémentaire
    }
}
