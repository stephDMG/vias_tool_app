package gui.controller.dialog;


import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;


/**
 * Utility-Klasse für standardisierte Dialoge (Fehler, Erfolg, Bestätigung) in der GUI.
 */
public class Dialog {


    public static void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showSuccessDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
    }

    public static boolean showWarningDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Spalten löschen");
        alert.setHeaderText("Möchten Sie die ausgewählten Spalten wirklich löschen?");
        alert.setContentText("Dies kann nicht rückgängig gemacht werden.");

        final boolean[] result = {false};
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                result[0] = true;
            }
        });
        return result[0];
    }

    /**
     * Zeigt einen Bestätigungsdialog mit anpassbarem Titel und Inhalt an.
     *
     * @param title   Der Titel des Dialogs.
     * @param content Der Inhaltstext des Dialogs.
     * @return Ein Optional<ButtonType> mit der Benutzerantwort.
     */
    public static Optional<ButtonType> showConfirmationDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        return alert.showAndWait();
    }


    public static boolean showWarningDialog(String spalte, String s) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(spalte);
        alert.setHeaderText(spalte);
        alert.setContentText(s);

        final boolean[] result = {false};
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                result[0] = true;
            }
        });
        return result[0];
    }
}
