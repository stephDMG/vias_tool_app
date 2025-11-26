package gui.controller.manager;

import javafx.application.Platform;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TableLayoutHelper {
    private static final Logger log = LoggerFactory.getLogger(TableLayoutHelper.class);
    private static final double MIN_HEIGHT = 400.0;

    public static void configureTableContainer(VBox parentContainer, VBox tableContainer, String controllerName) {
        log.info("=== Configuration table pour {} ===", controllerName);

        // 🔑 ÉTAPE 1: Contraintes sur le conteneur TABLE
        VBox.setVgrow(tableContainer, javafx.scene.layout.Priority.ALWAYS);
        tableContainer.setMaxHeight(Double.MAX_VALUE);
        tableContainer.setMinHeight(MIN_HEIGHT);

        // 🔑 ÉTAPE 2: Contraintes sur le conteneur PARENT
        parentContainer.setMinHeight(MIN_HEIGHT);
        parentContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);
        VBox.setVgrow(parentContainer, javafx.scene.layout.Priority.ALWAYS);

        log.debug("parentContainer: minH={} prefH={}", parentContainer.getMinHeight(), parentContainer.getPrefHeight());
        log.debug("tableContainer: minH={} maxH={}", tableContainer.getMinHeight(), tableContainer.getMaxHeight());

        // 🔑 ÉTAPE 3: Ajout et recalcul
        parentContainer.getChildren().clear();
        parentContainer.getChildren().add(tableContainer);

        Platform.runLater(() -> {
            parentContainer.requestLayout();
            tableContainer.requestLayout();
            log.info("Layout request exécuté pour {}", controllerName);
        });
    }
}