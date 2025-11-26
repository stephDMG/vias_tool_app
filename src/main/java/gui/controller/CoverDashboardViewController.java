package gui.controller;

import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerLight;
import gui.cover.CoverDomainController;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Objects;

import static gui.controller.dialog.Dialog.showErrorDialog;

/**
 * Controller für das Cover-Dashboard. Steuert Animationen und das Öffnen der Domain-Viewer.
 */
public class CoverDashboardViewController {
    private static final Logger log = LoggerFactory.getLogger(CoverDashboardViewController.class);

    @FXML
    private VBox animatedVBox1, animatedVBox2, animatedVBox3, animatedVBox4;

    /**
     * Initialisiert die Hover-Animationen für die Kacheln des Dashboards.
     */
    public void initialize() {
        for (VBox box : new VBox[]{animatedVBox1, animatedVBox2, animatedVBox3, animatedVBox4}) {
            box.setOnMouseEntered(event -> {
                ScaleTransition scaleUp = new ScaleTransition(Duration.millis(300), box);
                scaleUp.setToX(0.95); // Verkleinern auf 95%
                scaleUp.setToY(0.95);
                scaleUp.play();

                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), box);
                fadeIn.setToValue(1.0); // Voll sichtbar
                fadeIn.play();
            });

            box.setOnMouseExited(event -> {
                ScaleTransition scaleDown = new ScaleTransition(Duration.millis(300), box);
                scaleDown.setToX(1.0); // Originalgröße
                scaleDown.setToY(1.0);
                scaleDown.play();

                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), box);
                fadeOut.setToValue(0.9); // Leicht ausgeblendet
                fadeOut.play();
            });
        }
    }

    // =========================
    // Generic Viewer Launcher
    // =========================

    /**
     * Öffnet den Domain-Viewer für das Angebotswesen in einem neuen Fenster.
     */
    private void openDomainViewer(String domain, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CoverDomainViewer.fxml"));
            Parent root = loader.load();

            // Controller + Domaine
            CoverDomainController controller = loader.getController();
            controller.initDomain(domain);

            // Fenêtre
            Stage stage = new Stage();
            stage.setTitle("COVER • " + title);
            Scene scene = new Scene(root, 1200, 950);
            Application.setUserAgentStylesheet(new NordLight().getUserAgentStylesheet());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/fixed-header.css")).toExternalForm());

            stage.setScene(scene);
            stage.setMinWidth(1100);
            stage.setMinHeight(900);
            stage.setMaximized(true);
            stage.show();

        } catch (Exception e) {
            log.error("Fehler beim Öffnen von {}", domain, e);
            showErrorDialog("Ansichtsfehler", e.getMessage());
        }
    }


    @FXML
    private void openAngebotswesen() {
        openDomainViewer("angebotswesen", "Angebotswesen");
    }

    @FXML
    private void openVertragsstatus() {
        openDomainViewer("vertragsstatus", "Vertragsstatus");
    }

    @FXML
    private void openKuendigungsfrist() {
        openDomainViewer("kuendigungsfrist", "Kündigungsfristverkürzung");
    }

    @FXML
    private void openRelevanteVias() {
        openDomainViewer("viasfelder", "Relevante VIAS-Felder");
    }


}
