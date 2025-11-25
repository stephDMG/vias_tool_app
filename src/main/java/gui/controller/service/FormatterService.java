package gui.controller.service;

import com.google.gson.Gson;
import formatter.DateFieldFormatter;
import formatter.MoneyFieldFormatter;
import gui.controller.utils.format.FormatterConfig;
import gui.controller.utils.format.FormatterField;
import model.RowData;
import model.enums.ExportFormat;
import service.ServiceFactory;
import service.interfaces.FileService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import formatter.ColumnValueFormatter;


public final class FormatterService {
    private static final Gson gson = new Gson();

    private static final Set<String> moneyFields = new LinkedHashSet<>();
    private static final Set<String> dateFields  = new LinkedHashSet<>();
    private static final Set<String> sbFields    = new LinkedHashSet<>();

    private static Path getAppHome() {
        try {
            // Dossier où tourne le binaire (launcher/JAR)
            Path bin = Paths.get(FormatterService.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());

            return Files.isDirectory(bin) ? bin : bin.getParent();
        } catch (Exception e) {
            // fallback: current working dir
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    private static Path appLocalUserConfig() {
        // ./config/user/formatter.json relatif au dossier binaire
        return getAppHome().resolve("config").resolve("user").resolve("formatter.json");
    }

    private static Path roamingUserConfig() {
        String appData = System.getenv("APPDATA"); // ex. C:\Users\...\AppData\Roaming
        if (appData == null || appData.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".vias", "formatter.json");
        }
        return Paths.get(appData, "CarlSchroeter", "formatter.json");
    }

    private static Path programDataConfig() {
        String programData = System.getenv("PROGRAMDATA"); // ex. C:\ProgramData
        if (programData == null || programData.isBlank()) return null;
        return Paths.get(programData, "CarlSchroeter", "formatter.json");
    }

    private static boolean ensureDirWritable(Path file) {
        try {
            Path dir = file.getParent();
            if (dir == null) return false;
            Files.createDirectories(dir);
            // Test écriture légère
            Path probe = dir.resolve(".write_test");
            Files.writeString(probe, "ok", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static FormatterConfig loadFromClasspathDefaults() {
        try (InputStream in = FormatterService.class.getResourceAsStream("/config/formatter.json")) {
            if (in == null) return null;
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return gson.fromJson(r, FormatterConfig.class);
            }
        } catch (Exception e) { return null; }
    }

    private static FormatterConfig loadFromPath(Path p) {
        if (p == null || !Files.exists(p)) return null;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return gson.fromJson(r, FormatterConfig.class);
        } catch (Exception e) { return null; }
    }


    public static void reloadRuntimeConfig() {
        // 1) defaults (classpath)
        FormatterConfig base = loadFromClasspathDefaults();

        // 2) overrides (app-local user → roaming user → programdata)
        FormatterConfig appUser = loadFromPath(appLocalUserConfig());
        FormatterConfig roam    = loadFromPath(roamingUserConfig());
        FormatterConfig machine = loadFromPath(programDataConfig());

        // merge par priorité: machine > roaming > appLocal > base
        Map<String,String> typeByName = new LinkedHashMap<>();
        if (base != null && base.getFields()!=null) base.getFields().forEach(f -> typeByName.put(f.getName(), f.getType()));
        if (appUser != null && appUser.getFields()!=null) appUser.getFields().forEach(f -> typeByName.put(f.getName(), f.getType()));
        if (roam   != null && roam.getFields()!=null)     roam.getFields().forEach(f -> typeByName.put(f.getName(), f.getType()));
        if (machine!= null && machine.getFields()!=null) machine.getFields().forEach(f -> typeByName.put(f.getName(), f.getType()));

        FormatterConfig merged = new FormatterConfig();
        List<FormatterField> fields = new ArrayList<>();
        typeByName.forEach((n,t) -> fields.add(new FormatterField(n, t)));
        merged.setFields(fields);

        initRuntimeSetsFrom(merged);
        ColumnValueFormatter.setAdditionalSbHeaders(sbFields);
    }



    private static File getUserConfigFile() {
        String base = System.getProperty("os.name","").toLowerCase().contains("win")
                ? System.getenv("APPDATA") + File.separator + "CarlSchroeter"
                : System.getProperty("user.home") + File.separator + ".vias";
        File dir = new File(base);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "formatter.json");
    }



    private static FormatterConfig loadFromUserFile() {
        File f = getUserConfigFile();
        if (!f.exists()) return null;
        try (java.io.FileReader r = new java.io.FileReader(f)) {
            return gson.fromJson(r, FormatterConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static void initRuntimeSetsFrom(FormatterConfig config) {
        moneyFields.clear();
        dateFields.clear();
        sbFields.clear();
        if (config == null || config.getFields() == null) return;
        for (FormatterField f : config.getFields()) {
            if (f == null || f.getName() == null) continue;
            String name = f.getName();
            String type = f.getType();
            if ("MONEY".equalsIgnoreCase(type)) moneyFields.add(name);
            else if ("DATE".equalsIgnoreCase(type)) dateFields.add(name);
            else if ("SB".equalsIgnoreCase(type)) sbFields.add(name);  // NEW
        }
    }

    public static synchronized void setColumnFormat(String originalKey, String headerText, String type) throws Exception {
        if (originalKey == null && headerText == null) return;

        // retirer d’abord des 3 ensembles
        if (originalKey != null) { moneyFields.remove(originalKey); dateFields.remove(originalKey); sbFields.remove(originalKey); }
        if (headerText != null)  { moneyFields.remove(headerText);  dateFields.remove(headerText);  sbFields.remove(headerText);  }

        // écrire
        if ("MONEY".equalsIgnoreCase(type)) {
            if (originalKey != null) moneyFields.add(originalKey);
            if (headerText != null)  moneyFields.add(headerText);
        } else if ("DATE".equalsIgnoreCase(type)) {
            if (originalKey != null) dateFields.add(originalKey);
            if (headerText != null)  dateFields.add(headerText);
        } else if ("SB".equalsIgnoreCase(type)) {
            if (originalKey != null) sbFields.add(originalKey);
            if (headerText != null)  sbFields.add(headerText);
        } // "NONE" → rien n’ajouter

        // sauvegarder + propager dans ColumnValueFormatter
        saveUserConfig();
        ColumnValueFormatter.setAdditionalSbHeaders(sbFields);
    }

    private static Path pickWritableUserConfigTarget() {
        Path appLocal = appLocalUserConfig();
        if (ensureDirWritable(appLocal)) return appLocal;
        Path roam = roamingUserConfig();
        if (ensureDirWritable(roam)) return roam;
        // dernier recours: tente ProgramData (nécessite admin)
        Path machine = programDataConfig();
        if (machine != null && ensureDirWritable(machine)) return machine;
        // sinon, retombe sur le roaming (dossier créé, mais non writable → abandon)
        return roam;
    }

    private static void saveUserConfig() throws IOException {
        FormatterConfig cfg = new FormatterConfig();
        List<FormatterField> list = new ArrayList<>();
        moneyFields.forEach(n -> list.add(new FormatterField(n, "MONEY")));
        dateFields.forEach(n  -> list.add(new FormatterField(n, "DATE")));
        sbFields.forEach(n    -> list.add(new FormatterField(n, "SB")));
        cfg.setFields(list);

        Path target = pickWritableUserConfigTarget();
        Files.createDirectories(target.getParent());
        try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(cfg, w);
        }
    }

    private static FormatterConfig loadConfig() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(FormatterService.class.getResourceAsStream("/config/formatter.json")))) {
            return gson.fromJson(reader, FormatterConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void exportWithFormat(List<RowData> fullResults,
                                        List<String> displayHeaders,
                                        List<String> backingKeys,
                                        File file, ExportFormat format) throws Exception {

        exportWithFormat(fullResults, displayHeaders, backingKeys, file, format, false);
    }

    public static void exportWithFormat(List<RowData> fullResults,
                                        List<String> displayHeaders,
                                        List<String> backingKeys,
                                        File file, ExportFormat format,
                                        boolean fullNameMode) throws Exception {

        FileService fileService = ServiceFactory.getFileService();

        // Sécurité: aligner les listes
        int n = Math.min(displayHeaders.size(), backingKeys.size());

        List<RowData> formattedRows = new ArrayList<>(fullResults.size());
        for (RowData row : fullResults) {
            // IMPORTANT: LinkedHashMap pour préserver l'ordre des colonnes
            Map<String, String> vals = new LinkedHashMap<>(n);

            for (int i = 0; i < n; i++) {
                String header = displayHeaders.get(i); // clé visible attendue par le writer
                String key    = backingKeys.get(i);    // clé source dans RowData

                String raw = row.getValues().getOrDefault(key, "");
                String v   = (raw == null) ? "" : raw;

                if (fullNameMode) {
                    // Applique Voll.Name uniquement pour les colonnes SB_*
                    v = ColumnValueFormatter.displayOnly(key, v);
                }
                // Conserve tes formats existants
                v = DateFieldFormatter.tryFormat(key, v);
                v = MoneyFieldFormatter.tryFormat(key, v);
                if (v == null) v = "";

                // La clé DANS LA MAP = header (puisque le FileService n’a pas backingKeys)
                vals.put(header, v);
            }
            RowData rd = new RowData();
            rd.putAll(vals);
            formattedRows.add(rd);
        }


        fileService.writeFileWithHeaders(
                formattedRows,
                displayHeaders,
                file.getAbsolutePath(),
                format
        );
    }



    public static boolean isMoneyField(String name) { return moneyFields.contains(name); }
    public static boolean isDateField(String name)  { return dateFields.contains(name); }
    // utile si tu veux vérifier au rendu
    public static boolean isSbField(String name)    { return sbFields.contains(name); }


}