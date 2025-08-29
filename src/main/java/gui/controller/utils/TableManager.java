package gui.controller.utils;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import formatter.ColumnValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static gui.controller.utils.Dialog.showWarningDialog;

/**
 * Hilfsklasse zur Verwaltung der TableView (Spaltenaufbau, Kontextmenü,
 * Auswahl/Löschung, Formatierung der Zellwerte für die Anzeige).
 */
public class TableManager {
    private static final Logger logger = LoggerFactory.getLogger(TableManager.class);

    /**
     * Diese Klasse verwaltet die TableView und deren Daten.
     * Sie ermöglicht das Hinzufügen, Löschen und Umbenennen von Spalten.
     */
    private final TableView<ObservableList<String>> previewTableView;

    public TableManager(TableView<ObservableList<String>> previewTableView) {
        this.previewTableView = previewTableView;
    }

    public void allowSelection(Button deleteColumnsButton) {
        previewTableView.getSelectionModel().setCellSelectionEnabled(true);
        previewTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        deleteColumnsButton.setDisable(true);

        previewTableView.getSelectionModel().getSelectedCells().addListener((ListChangeListener<TablePosition>) c -> {
            deleteColumnsButton.setDisable(previewTableView.getSelectionModel().getSelectedCells().isEmpty());
        });
    }

    /**
     * Befüllt die TableView mit Daten.
     * @param data Die Datenliste, die die anzuzeigenden Zeilen enthält.
     */
    public void populateTableView(List<RowData> data) {
        if (data == null || data.isEmpty()) {
            this.previewTableView.getItems().clear();
            return;
        }

        // WICHTIG: Spalten immer zuerst löschen und dann neu aufbauen
        this.previewTableView.getColumns().clear();

        List<String> headers = new ArrayList<>(data.get(0).getValues().keySet());

        for (int i = 0; i < headers.size(); i++) {
            final int finalI = i;
            final String originalKey = headers.get(i);

            TableColumn<ObservableList<String>, String> column = new TableColumn<>(originalKey);
            column.setUserData(originalKey); // Original-Key merken für Format/Export

            column.setCellValueFactory(param -> {
                ObservableList<String> row = param.getValue();
                return (row != null && finalI < row.size())
                        ? new SimpleStringProperty(row.get(finalI))
                        : new SimpleStringProperty("");
            });

            addContextMenuToColumn(column, data);
            this.previewTableView.getColumns().add(column);
        }

        ObservableList<ObservableList<String>> tableData = FXCollections.observableArrayList();
        for (RowData row : data) {
            ObservableList<String> rowValues = FXCollections.observableArrayList();
            for (String header : headers) {
                String formattedValue = ColumnValueFormatter.format(row, header);
                rowValues.add(formattedValue);
            }
            tableData.add(rowValues);
        }
        this.previewTableView.setItems(tableData);
    }

    /**
     * Fügt ein Kontextmenü zu einer Spalte hinzu.
     * @param column Die Spalte, der das Kontextmenü hinzugefügt werden soll.
     */
    @SuppressWarnings("unchecked")
    public void addContextMenuToColumn(TableColumn<ObservableList<String>, String> column, List<RowData> data) {
        MenuItem renameItem = new MenuItem("Spalte umbenennen");
        MenuItem deleteItem = new MenuItem("Spalte löschen");

        renameItem.setStyle("-fx-text-fill: #000;");
        deleteItem.setStyle("-fx-text-fill: #d00;");

        // Nur die ANZEIGE umbenennen – Daten/Keys bleiben unverändert
        renameItem.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog(column.getText());
            dialog.setTitle("Spalte umbenennen");
            dialog.setHeaderText("Geben Sie einen neuen Namen für die Spalte '" + column.getText() + "' ein.");
            dialog.setContentText("Neuer Name:");
            dialog.showAndWait().ifPresent(newName -> {
                if (!newName.isBlank()) {
                    String oldHeaderShown = column.getText();
                    column.setText(newName);
                    this.previewTableView.refresh();
                    logger.info("Spalte umbenannt (Anzeige): {} -> {}", oldHeaderShown, newName);
                }
            });
        });

        // Spalte (Anzeige) und zugrundeliegende Werte entfernen
        deleteItem.setOnAction(e -> {
            if (showWarningDialog()) {
                deleteColumns(List.of(column), data);
                logger.info("Spalte gelöscht: {}", column.getText());
            }
        });

        ContextMenu contextMenu = new ContextMenu(renameItem, new SeparatorMenuItem(), deleteItem);
        column.setContextMenu(contextMenu);
    }

    /**
     * Löscht eine Spalte aus der TableView und aus den zugrunde liegenden Daten.
     * @param columnsToDelete Die zu löschenden Spalten.
     */
    public void deleteColumns(List<TableColumn<ObservableList<String>, ?>> columnsToDelete, List<RowData> data) {
        Set<String> headersToDelete = columnsToDelete.stream()
                .map(TableColumn::getText)
                .collect(Collectors.toSet());

        // 1. Lösche die Spalten aus der TableView
        this.previewTableView.getColumns().removeAll(columnsToDelete);

        // 2. Entferne die Daten für die gelöschten Header aus der fullResults-Liste
        for (RowData row : data) {
            row.getValues().keySet().removeAll(headersToDelete);
        }

        // 3. Aktualisiere die Tabellenansicht
        this.previewTableView.refresh();
        logger.info("Spalten gelöscht. Aktuelle Spalten: {}",
                this.previewTableView.getColumns().stream()
                        .map(TableColumn::getText)
                        .collect(Collectors.joining(", ")));
    }

    public void handleDeleteSelectedColumns(List<RowData> data) {
        // 1. Hole die ausgewählten Spalten über die ausgewählten Zellen
        ObservableList<TablePosition> selectedCells = this.previewTableView.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) {
            Dialog.showErrorDialog("Keine Spalte ausgewählt", "Bitte wählen Sie mindestens eine Spalte zum Löschen aus.");
            return;
        }

        // 2. Eindeutige Spalten aus den ausgewählten Zellen extrahieren
        List<TableColumn<ObservableList<String>, ?>> selectedColumns = selectedCells.stream()
                .map(pos -> (TableColumn<ObservableList<String>, ?>) pos.getTableColumn())
                .distinct()
                .collect(Collectors.toList());

        if (selectedColumns.isEmpty()) {
            Dialog.showErrorDialog("Keine Spalte ausgewählt", "Bitte wählen Sie mindestens eine Spalte zum Löschen aus.");
            return;
        }

        if (showWarningDialog()) {
            deleteColumns(selectedColumns, data);
            logger.info("Ausgewählte Spalten gelöscht.");
        } else {
            logger.info("Spalte konnte nicht gelöscht werden, da der Benutzer die Aktion abgebrochen hat.");
        }
    }
}
