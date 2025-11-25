package gui.controller.utils;

import gui.controller.dialog.Dialog;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static gui.controller.dialog.Dialog.showWarningDialog;

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
     * Löscht eine Spalte aus der TableView und aus den zugrunde liegenden Daten.
     *
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
