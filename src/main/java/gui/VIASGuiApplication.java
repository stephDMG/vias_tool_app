package gui;

import console.Main;
import javafx.fxml.FXMLLoader;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import org.kordamp.bootstrapfx.BootstrapFX;
import org.kordamp.bootstrapfx.scene.layout.Panel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Objects;

public class VIASGuiApplication  extends Application {

    private static final Logger logger = LoggerFactory.getLogger(VIASGuiApplication.class);

    @Override
    public void start(Stage primaryStage){
        try {
            logger.info("🚀 VIAS GUI-Anwendung wird gestartet...");
            // FXML-Datei laden

            Panel panel = new Panel("Willkommen bei VIAS Export Tool");
            panel.getStyleClass().add("panel-primary");                            //(2)
            BorderPane content = new BorderPane();

            panel.setBody(content);


            java.net.URL fxmlLocation = getClass().getResource("/fxml/MainWindow.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            BorderPane root = loader.load();
            //root.setTop(panel);

            // Scene erstellen
            Scene scene = new Scene(root, 1200, 800);

            scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());

            java.net.URL cssLocation = getClass().getResource("/css/styles.css");
            scene.getStylesheets().add(Objects.requireNonNull(cssLocation).toExternalForm());

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

    @Override
    public void stop() {
        logger.info("🛑 VIAS GUI-Anwendung wird beendet");
    }

    /**
     * Hauptmethode für GUI-Start.
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Startet die GUI-Anwendung programmatisch.
     */
    public static void launchGui() {
        new Thread(Application::launch).start();
    }
}
