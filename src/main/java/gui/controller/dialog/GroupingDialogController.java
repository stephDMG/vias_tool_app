package gui.controller.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.List;

public class GroupingDialogController {

    @FXML private ComboBox<String> headerCombo;
    @FXML private ColorPicker colorPickerA;
    @FXML private ColorPicker colorPickerB;
    @FXML private CheckBox enableCheck;

    private Result result = null;

    public static class Result {
        public final boolean enabled;
        public final String header;
        public final Color colorA;
        public final Color colorB;

        public Result(boolean enabled, String header, Color colorA, Color colorB) {
            this.enabled = enabled;
            this.header = header;
            this.colorA = colorA;
            this.colorB = colorB;
        }
    }

    public void init(List<String> selectableHeaders, String currentHeader, Color currentA, Color currentB, boolean enabled) {
        headerCombo.getItems().setAll(selectableHeaders);
        if (currentHeader != null) headerCombo.getSelectionModel().select(currentHeader);
        else if (!selectableHeaders.isEmpty()) headerCombo.getSelectionModel().select(0);

        colorPickerA.setValue(currentA != null ? currentA : Color.web("#B4C8F0", 0.25));
        colorPickerB.setValue(currentB != null ? currentB : Color.web("#DDE7FF", 0.20));
        enableCheck.setSelected(enabled);
    }

    @FXML
    private void onCancel() {
        ((Stage) enableCheck.getScene().getWindow()).close();
    }

    @FXML
    private void onApply() {
        String header = headerCombo.getSelectionModel().getSelectedItem();
        if (enableCheck.isSelected() && (header == null || header.isBlank())) {
            new Alert(Alert.AlertType.WARNING, "Veuillez choisir une colonne à regrouper.").showAndWait();
            return;
        }
        result = new Result(enableCheck.isSelected(), header, colorPickerA.getValue(), colorPickerB.getValue());
        ((Stage) enableCheck.getScene().getWindow()).close();
    }

    public Result getResult() {
        return result;
    }
}
