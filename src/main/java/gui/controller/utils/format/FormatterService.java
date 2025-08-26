package gui.controller.utils.format;

import com.google.gson.Gson;
import formatter.DateFieldFormatter;
import formatter.MoneyFieldFormatter;
import model.RowData;
import model.enums.ExportFormat;
import service.ServiceFactory;
import service.interfaces.FileService;

import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public final class FormatterService {
    private static final Gson gson = new Gson();
    private static final Set<String> moneyFields;
    private static final Set<String> dateFields;

    static {
        FormatterConfig config = loadConfig();
        if (config != null) {
            moneyFields = config.getFields().stream()
                    .filter(f -> "MONEY".equalsIgnoreCase(f.getType()))
                    .map(FormatterField::getName)
                    .collect(Collectors.toSet());
            dateFields = config.getFields().stream()
                    .filter(f -> "DATE".equalsIgnoreCase(f.getType()))
                    .map(FormatterField::getName)
                    .collect(Collectors.toSet());
        } else {
            moneyFields = Collections.emptySet();
            dateFields = Collections.emptySet();
        }
    }

    private static FormatterConfig loadConfig() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(FormatterService.class.getResourceAsStream("/config/formatter.json")))) {
            return gson.fromJson(reader, FormatterConfig.class);
        } catch (Exception e) {
            // Loggen Sie den Fehler
            return null;
        }
    }

    public static void exportWithFormat(List<RowData> fullResults,
                                        List<String> displayHeaders,
                                        List<String> backingKeys,
                                        File file, ExportFormat format) throws Exception {

        FileService fileService = ServiceFactory.getFileService();

        List<RowData> formattedData = new ArrayList<>();

        for (RowData row : fullResults) {
            RowData formattedRow = new RowData();
            for (int i = 0; i < backingKeys.size(); i++) {
                String key = backingKeys.get(i);
                String value = row.getValues().getOrDefault(key, "");

                if (isMoneyField(displayHeaders.get(i))) {
                    value = MoneyFieldFormatter.tryFormat(displayHeaders.get(i), value);
                } else if (isDateField(displayHeaders.get(i))) {
                    value = DateFieldFormatter.tryFormat(displayHeaders.get(i), value);
                }

                formattedRow.put(displayHeaders.get(i), value);
            }
            formattedData.add(formattedRow);
        }

        fileService.writeFileWithHeaders(formattedData, displayHeaders, file.getAbsolutePath(), format);
    }



    public static boolean isMoneyField(String columnName) {
        return moneyFields.contains(columnName);
    }

    public static boolean isDateField(String columnName) {
        return dateFields.contains(columnName);
    }


}