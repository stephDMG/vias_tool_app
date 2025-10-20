package service.audit;

import model.RowData;
import model.audit.AuditDocumentRecord;
import model.audit.CoverAuditRecord;
import model.audit.VsnAuditRecord;
import model.op.kunde.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.audit.repository.AuditRepository;
import service.interfaces.FileService;
import service.interfaces.ProgressReporter;
import util.FileUtil;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import model.enums.ExportFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service für die Durchführung der Audit-Prozesse (Vertrag und/oder Schaden).
 * Koordiniert das Lesen der Input-Datei, den Datenbankabruf und die physische
 * Organisation und Kopie der Dokumente in der Zielstruktur.
 */
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    // --- FESTE PFAD-KONSTANTEN (Source) ---
    private static final String VERTRAG_PATH = "X:\\FREIE ZONE\\000_AUDIT_2025\\SOURCE (NICHT_AENDERN)\\Vertragsliste.xlsx";
    private static final String SCHADEN_PATH = "X:\\FREIE ZONE\\000_AUDIT_2025\\SOURCE (NICHT_AENDERN)\\Schadenliste.xlsx";
    final int MAX_DISPLAY_LENGTH = 100;
    // ------------------------------------

    private final FileService fileService;
    private final AuditRepository auditRepository;

    // Enum zur Steuerung, welchen Prozess wir durchführen (für die GUI)
    public enum AuditType {
        VERTRAG("Verträge"),
        SCHADEN("Schaden"),
        BEIDE("Verträge und Schaden");

        private final String ordnerName;

        AuditType(String ordnerName) {
            this.ordnerName = ordnerName;
        }

        public String getOrdnerName() {
            return ordnerName;
        }
    }

    public AuditService(FileService fileService, AuditRepository auditRepository) {
        this.fileService = fileService;
        this.auditRepository = auditRepository;
    }


    /**
     * Startet den Audit-Prozess basierend auf dem Typ.
     * Nutzt die fest kodierten Pfade für die Input-Dateien (VERTRAG_PATH/SCHADEN_PATH).
     *
     * @param auditType Der gewünschte Audit-Typ (Vertrag, Schaden oder Beide).
     * @param reporter  Schnittstelle zur Berichterstattung über den Fortschritt.
     * @return Ein ExecutionResult mit dem Status der Operation.
     */
    public ExecutionResult startAudit(AuditType auditType, ProgressReporter reporter) {

        // Initialer Fortschritt
        reporter.updateProgress(0, 100);
        reporter.updateMessage("Starte Audit-Prozess: " + auditType.getOrdnerName());

        try {
            int totalCopied = 0;
            // Speichert das Ende des vorherigen Prozesses. Startet bei 0.
            int currentProgress = 0;

            // --- 1. VERTRAGS-AUDIT ---
            if (auditType == AuditType.VERTRAG || auditType == AuditType.BEIDE) {
                // Endet bei 50% wenn BEIDE, sonst bei 100%.
                int endProgress = (auditType == AuditType.BEIDE) ? 50 : 100;

                reporter.updateProgress(currentProgress, 100);
                reporter.updateMessage("Lese Policennummern aus: " + VERTRAG_PATH);

                List<String> policeNrs = readKeysFromExcel(VERTRAG_PATH, "Policennummer");

                if (policeNrs.isEmpty()) {
                    logger.warn("Keine Policennummern für den Vertrags-Audit gefunden.");
                } else {
                    reporter.updateMessage("Hole Vertragsdokumente für " + policeNrs.size() + " Policen...");
                    List<CoverAuditRecord> coverRecords = auditRepository.fetchCoverDocumentsByPolicyNr(policeNrs);

                    reporter.updateMessage(String.format("Kopiere %d Vertragsdokumente...", coverRecords.size()));
                    // Führt Kopierlogik von 0% bis 50% (oder 100%) durch
                    totalCopied += processDocumentCopy(coverRecords, reporter, currentProgress, endProgress);
                }

                // Stellt sicher, dass der Fortschritt nach dem Ende dieser Phase auf 50% (oder 100%) ist.
                currentProgress = endProgress;
            }

            // --- 2. SCHADEN-AUDIT ---
            if (auditType == AuditType.SCHADEN || auditType == AuditType.BEIDE) {
                // Startet bei 0% (wenn nur SCHADEN) oder beim Wert von currentProgress (50%, wenn BEIDE)
                int startProgress = (auditType == AuditType.SCHADEN) ? 0 : currentProgress;

                reporter.updateProgress(startProgress, 100);
                reporter.updateMessage("Lese Schaden-Nummern aus: " + SCHADEN_PATH);

                List<String> schadenNrs = readKeysFromExcel(SCHADEN_PATH, "Schaden Nr. CS");

                if (schadenNrs.isEmpty()) {
                    logger.warn("Keine Schaden-Nummern für den Schaden-Audit gefunden.");
                } else {
                    reporter.updateMessage("Hole Schaden-Dokumente für " + schadenNrs.size() + " Schäden...");
                    List<VsnAuditRecord> schadenRecords = auditRepository.fetchSchadenDocumentsByVsnNr(schadenNrs);

                    reporter.updateMessage(String.format("Kopiere %d Schadendokumente...", schadenRecords.size()));
                    // Führt Kopierlogik von 0% (oder 50%) bis 100% durch
                    totalCopied += processDocumentCopy(schadenRecords, reporter, startProgress, 100);
                }
            }


            if (totalCopied == 0) {
                return new ExecutionResult(ExecutionResult.Status.SUCCESS, "Keine Dokumente gefunden oder kopiert.");
            }

            // 3. Abschluss
            reporter.updateProgress(100, 100);
            return new ExecutionResult(ExecutionResult.Status.SUCCESS,
                    String.format("Audit erfolgreich abgeschlossen. %d Dokumente kopiert.", totalCopied),
                    FileUtil.BASE_AUDIT_PATH, totalCopied);

        } catch (Exception ex) {
            logger.error("Kritischer Fehler im AuditService", ex);
            reporter.updateMessage("❌ Fehler: " + ex.getMessage());
            return new ExecutionResult(ExecutionResult.Status.FAILURE, "Kritischer Fehler: " + ex.getMessage());
        }
    }

    /**
     * Liest die Schlüssel aus der angegebenen Spalte der XLSX-Datei.
     *
     * @param excelFilePath Pfad zur Datei.
     * @param keyHeader     Der Header der Spalte, die den Schlüssel enthält (z.B. "Policennummer" oder "Schaden Nr. CS").
     * @return Liste der eindeutigen Schlüssel (Policen/Schaden-Nummern).
     */
    private List<String> readKeysFromExcel(String excelFilePath, String keyHeader) {
        List<RowData> data = fileService.readFile(excelFilePath);
        List<String> keys = new ArrayList<>();

        if (data.isEmpty()) return keys;

        for (RowData row : data) {
            // Extrahieren der Nummer
            String key = row.getValues().getOrDefault(keyHeader, "").trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }

        // Verwenden von Distinct, um Duplikate im Input-File zu entfernen
        return keys.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Führt die Hauptlogik des Kopierens und Organisierens der Dokumente durch,
     * mit skalierter Fortschrittsberichterstattung.
     *
     * @param records       Liste der zu kopierenden AuditDocumentRecords.
     * @param reporter      ProgressReporter.
     * @param startProgress Startwert für den Fortschritt (z.B. 0 oder 50).
     * @param endProgress   Endwert für den Fortschritt (z.B. 50 oder 100).
     * @return Die Anzahl der erfolgreich kopierten Dokumente.
     */
    // AuditService.processDocumentCopy(...)
    private int processDocumentCopy(List<? extends AuditDocumentRecord> records,
                                    ProgressReporter reporter, int startProgress, int endProgress) throws InterruptedException {

        final int totalRecords = records.size();
        int copiedCount = 0;
        final int progressRange = endProgress - startProgress;

        Map<String, Set<String>> usedBaseNamesByPath = new HashMap<>();

        // ---------- PRÉ-SCAN: compter les baseName par dossier ----------
        Map<String, Integer> countsByPathAndBase = new HashMap<>();
        for (AuditDocumentRecord r : records) {
            String auditType;
            String schluessel;
            if (r instanceof CoverAuditRecord cover) {
                auditType = AuditType.VERTRAG.getOrdnerName();
                schluessel = cover.getPoliceNr();
            } else if (r instanceof VsnAuditRecord vsn) {
                auditType = AuditType.SCHADEN.getOrdnerName();
                schluessel = vsn.getVsnNummer();
            } else {
                continue;
            }

            String plannedPath = FileUtil.buildTargetPathString(
                    r.getVorname(), r.getNachname(), auditType, schluessel, r.getBeschreibung()
            );
            String baseName = FileUtil.sanitizeFileName(r.getBetreff());
            String key = plannedPath + "|" + baseName;
            countsByPathAndBase.merge(key, 1, Integer::sum);
        }
        // ----------------------------------------------------------------

        for (int i = 0; i < totalRecords; i++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("Prozess wurde abgebrochen.");
                throw new InterruptedException("Audit-Prozess wurde abgebrochen.");
            }

            AuditDocumentRecord record = records.get(i);
            String betreff = record.getBetreff();
            if (betreff.length() > MAX_DISPLAY_LENGTH) {
                betreff = betreff.substring(0, MAX_DISPLAY_LENGTH) + "...";
            }

            int currentProgress = startProgress + (int) ((double) i / totalRecords * progressRange);
            reporter.updateProgress(currentProgress, 100);
            reporter.updateMessage(String.format("Kopiere Dokument %d/%d (%s)...", i + 1, totalRecords, betreff));

            try {
                String schluessel;
                String auditType;
                if (record instanceof CoverAuditRecord cover) {
                    schluessel = cover.getPoliceNr();
                    auditType = AuditType.VERTRAG.getOrdnerName();
                } else if (record instanceof VsnAuditRecord vsn) {
                    schluessel = vsn.getVsnNummer();
                    auditType = AuditType.SCHADEN.getOrdnerName();
                } else {
                    continue;
                }

                String sourcePath = record.getFilePath();
                if (sourcePath == null || sourcePath.isEmpty()) {
                    logger.warn("Dokument ohne physischen Pfad (Parameter leer) übersprungen: {}, {}", schluessel, betreff);
                    continue;
                }

                // Crée (au besoin) le dossier final
                String targetPath = FileUtil.buildAndEnsureTargetPath(
                        record.getVorname(), record.getNachname(), auditType, schluessel, record.getBeschreibung()
                );

                Set<String> existingBaseNames = usedBaseNamesByPath.computeIfAbsent(targetPath, k -> new HashSet<>());
                String extension = FileUtil.getFileExtension(sourcePath);

                // Décider si on doit dater "aussi le premier"
                String baseName = FileUtil.sanitizeFileName(record.getBetreff());
                String key = FileUtil.buildTargetPathString(
                        record.getVorname(), record.getNachname(), auditType, schluessel, record.getBeschreibung()
                ) + "|" + baseName;
                boolean stampAlways = countsByPathAndBase.getOrDefault(key, 1) > 1;

                String newFileName = FileUtil.generateUniqueFileName(
                        record.getBetreff(),
                        record.getBezugsdatum(),
                        record.getUhrzeit(),
                        extension,
                        existingBaseNames,
                        stampAlways // <-- force le renommage du premier si doublons
                );

                FileUtil.copyFile(sourcePath, targetPath, newFileName);
                copiedCount++;

            } catch (IOException e) {
                logger.error("❌ I/O-Fehler beim Kopieren von Dokument {}: {}", record.getBetreff(), e.getMessage());
            } catch (Exception e) {
                logger.error("❌ Unerwarteter Fehler beim Verarbeiten des Dokuments {}: {}", record.getBetreff(), e.getMessage());
            }
        }
        return copiedCount;
    }


    // AuditService.java
    public ExecutionResult startManualSchadenAudit(List<String> luSnrList, ProgressReporter reporter) {
        reporter.updateProgress(0, 100);
        reporter.updateMessage("Starte manuellen Schaden-Audit...");

        try {
            // Nettoyage / distinct
            List<String> cleaned = luSnrList.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(AuditService::normalizeToLuSnr) // accepte CS-20YY-xxxxx OU LU_SNR
                    .distinct()
                    .toList();

            if (cleaned.isEmpty()) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE,
                        "Keine gültigen Schaden-Nummern angegeben.");
            }

            reporter.updateMessage("Hole Schaden-Dokumente für " + cleaned.size() + " Nummern...");
            List<VsnAuditRecord> schadenRecords = auditRepository.fetchSchadenDocumentsByVsnNr(cleaned);

            reporter.updateMessage(String.format("Kopiere %d Schadendokumente...", schadenRecords.size()));
            int copied = processDocumentCopy(schadenRecords, reporter, 0, 100);

            reporter.updateProgress(100, 100);
            String msg = String.format("Manueller Schaden-Audit fertig. %d Dokumente kopiert.", copied);
            reporter.updateMessage(msg);
            return new ExecutionResult(ExecutionResult.Status.SUCCESS, msg, FileUtil.BASE_AUDIT_PATH, copied);

        } catch (Exception ex) {
            logger.error("Kritischer Fehler im manuellen Schaden-Audit", ex);
            reporter.updateMessage("❌ Fehler: " + ex.getMessage());
            return new ExecutionResult(ExecutionResult.Status.FAILURE, "Kritischer Fehler: " + ex.getMessage());
        }
    }

    /**
     * Accepte 'CS-2024-04343' ou '2404343' et renvoie LU_SNR (z. B. '2404343').
     */
    private static String normalizeToLuSnr(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return s;

        // Si format texte: CS-20YY-xxxxx -> LU_SNR = YY + xxxxx
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)^CS-20(\\d{2})-(\\d{5})$")
                .matcher(s);
        if (m.find()) {
            return m.group(1) + m.group(2); // "24" + "04343" -> "2404343"
        }

        // Sinon garder uniquement les chiffres (au cas où espaces, etc.)
        String digits = s.replaceAll("\\D+", "");
        return digits;
    }

    /**
     * Startet den VERTRAG-Audit im manuellen Modus.
     * Erwartet 1..N Police-Nummern (keine Transformation notwendig).
     */
    public ExecutionResult startManualVertragAudit(List<String> policeNrList, ProgressReporter reporter) {
        reporter.updateProgress(0, 100);
        reporter.updateMessage("Starte manuellen Verträge-Audit...");

        try {
            // 1) Eingaben säubern + deduplizieren
            List<String> cleaned = policeNrList.stream()
                    .filter(Objects::nonNull)
                    .map(AuditService::normalizePoliceNr) // trim + Whitespaces entfernen
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            if (cleaned.isEmpty()) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE,
                        "Keine gültigen Policennummern angegeben.");
            }

            // 2) Daten aus DB holen
            reporter.updateMessage("Hole Vertragsdokumente für " + cleaned.size() + " Policen...");
            List<CoverAuditRecord> coverRecords = auditRepository.fetchCoverDocumentsByPolicyNr(cleaned);

            // 3) Kopieren/Organisieren (0 -> 100%)
            reporter.updateMessage(String.format("Kopiere %d Vertragsdokumente...", coverRecords.size()));
            int copied = processDocumentCopy(coverRecords, reporter, 0, 100);

            // 4) Ergebnis
            reporter.updateProgress(100, 100);
            String msg = String.format("Manueller Verträge-Audit fertig. %d Dokumente kopiert.", copied);
            reporter.updateMessage(msg);
            return new ExecutionResult(ExecutionResult.Status.SUCCESS, msg, FileUtil.BASE_AUDIT_PATH, copied);

        } catch (Exception ex) {
            logger.error("Kritischer Fehler im manuellen Verträge-Audit", ex);
            reporter.updateMessage("❌ Fehler: " + ex.getMessage());
            return new ExecutionResult(ExecutionResult.Status.FAILURE, "Kritischer Fehler: " + ex.getMessage());
        }
    }

    /**
     * Minimal-‘Normalisierung’ für Police Nr.: trim + alle Whitespaces entfernen.
     */
    private static String normalizePoliceNr(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return s;
        // interne Leerzeichen entfernen (Excel-Padding etc.)
        s = s.replaceAll("\\s+", "");
        return s;
    }

    /**
     * Normalise un nom pour comparaison robuste (minuscules, espaces, accents).
     */
    private static String normalizeName(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", ""); // enlève accents
        t = t.replaceAll("\\s+", " "); // espaces multiples -> simple
        return t;
    }

    private static String joinFullName(String vor, String nach) {
        String v = (vor == null) ? "" : vor.trim();
        String n = (nach == null) ? "" : nach.trim();
        return (v + " " + n).trim();
    }

    /**
     * LU_SNR (ex: "2404343") -> LU_SNR_TEXT "CS-2024-04343" pour affichage/chemin.
     */
    private static String toSvaLuSnrText(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("\\D+", "");
        if (digits.length() < 3) return raw.trim();
        String yy = digits.substring(0, 2);
        String tail = digits.substring(2);
        if (tail.length() > 5) tail = tail.substring(tail.length() - 5);
        if (tail.length() < 5) tail = String.format("%5s", tail).replace(' ', '0');
        return "CS-20" + yy + "-" + tail;
    }

    /**
     * LU_SNR_TEXT "CS-2024-04343" -> LU_SNR "2404343" (utile si besoin).
     */
    private static String textToLuSnr(String text) {
        if (text == null) return "";
        var m = java.util.regex.Pattern.compile("(?i)^CS-20(\\d{2})-(\\d{5})$").matcher(text.trim());
        return m.find() ? (m.group(1) + m.group(2)) : text.replaceAll("\\D+", "");
    }

    private static String plannedBaseFolder(String sb, String typeDir, String key) {
        String sbDir = FileUtil.sanitizeFileName(sb);
        String keyDir = FileUtil.sanitizeFileName(key);
        return Path.of(FileUtil.BASE_AUDIT_PATH, sbDir, FileUtil.sanitizeFileName(typeDir), keyDir).toString();
    }

    private static Path ensureReportsDir() throws IOException {
        Path dir = Path.of(FileUtil.BASE_AUDIT_PATH, "_reports");
        Files.createDirectories(dir);
        return dir;
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

// ------------------ 1) SB-Abgleich VERTRAG ------------------

    /**
     * Compare SB Vertrag (Excel) vs SB in VIAS (COVER.LU_SACHBEA_VT via SAB) et génère un XLSX.
     * Sortie: ...\AUDIT\_reports\sb_mismatch_vertrag_YYYYMMDD_HHmmss.xlsx
     */
    public ExecutionResult checkSbMismatchVertrag(ProgressReporter reporter) {
        try {
            if (reporter != null) {
                reporter.updateProgress(0, 100);
                reporter.updateMessage("Prüfe SB-Abweichungen (Verträge)...");
            }

            // 1) Lire Excel
            List<RowData> rows = fileService.readFile(VERTRAG_PATH);
            if (rows == null || rows.isEmpty()) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE,
                        "Vertragsliste.xlsx ist leer oder nicht lesbar: " + VERTRAG_PATH);
            }

            // Excel -> map Policennummer -> SB Vertrag
            Map<String, String> excelSbByPolicy = new LinkedHashMap<>();
            for (RowData r : rows) {
                String pol = r.getValues().getOrDefault("Policennummer", "").trim();
                String sb = r.getValues().getOrDefault("SB Vertrag", "").trim();
                if (!pol.isEmpty() && !sb.isEmpty()) {
                    excelSbByPolicy.put(pol, sb);
                }
            }
            if (excelSbByPolicy.isEmpty()) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE,
                        "Keine Policennummern/SB Vertrag in der Vertragsliste gefunden.");
            }

            // 2) VIAS: fetch documents -> SB réel par police
            List<CoverAuditRecord> cover = auditRepository.fetchCoverDocumentsByPolicyNr(new ArrayList<>(excelSbByPolicy.keySet()));
            Map<String, Set<String>> viasSbsByPolicy = new HashMap<>();
            for (CoverAuditRecord c : cover) {
                String pol = c.getPoliceNr();
                String sbVias = joinFullName(c.getVorname(), c.getNachname());
                viasSbsByPolicy.computeIfAbsent(pol, k -> new LinkedHashSet<>()).add(sbVias);
            }

            // 3) Construire lignes mismatch
            List<RowData> out = new ArrayList<>();
            int total = excelSbByPolicy.size();
            int idx = 0;

            for (var entry : excelSbByPolicy.entrySet()) {
                idx++;
                String pol = entry.getKey();
                String sbExcel = entry.getValue();

                Set<String> viasSet = viasSbsByPolicy.getOrDefault(pol, Collections.emptySet());
                boolean match = viasSet.stream().anyMatch(v -> normalizeName(v).equals(normalizeName(sbExcel)));

                if (!match) {
                    RowData rd = new RowData();
                    rd.put("Typ", "Vertrag");
                    rd.put("Policen Nr.", pol);
                    rd.put("SB (Excel)", sbExcel);
                    rd.put("SB (VIAS)", viasSet.isEmpty() ? "(kein Datensatz)" : String.join(", ", viasSet));

                    String excelFolder = plannedBaseFolder(sbExcel, AuditType.VERTRAG.getOrdnerName(), pol);
                    String viasFolders = viasSet.isEmpty()
                            ? ""
                            : String.join(" | ", viasSet.stream()
                            .map(sb -> plannedBaseFolder(sb, AuditType.VERTRAG.getOrdnerName(), pol))
                            .toList());

                    rd.put("Ordner (Excel)", excelFolder);
                    rd.put("Ordner (VIAS)", viasFolders);
                    rd.put("Hinweis", viasSet.size() > 1 ? "Mehrere VIAS-SB" : "Abweichung");

                    out.add(rd);
                }

                if (reporter != null) reporter.updateProgress((int) (idx * 100.0 / total), 100);
            }

            // 4) Export XLSX
            Path reports = ensureReportsDir();
            String outPath = reports.resolve("sb_mismatch_vertrag_" + timestamp() + ".xlsx").toString();

            List<String> headers = List.of("Typ", "Policen Nr", "SB (Excel)", "SB (VIAS)", "Ordner (Excel)", "Ordner (VIAS)", "Hinweis");
            fileService.writeFileWithHeaders(out, headers, outPath, ExportFormat.XLSX);

            String msg = out.isEmpty()
                    ? "Keine SB-Abweichungen (Verträge) gefunden."
                    : String.format("%d SB-Abweichungen (Verträge) – Bericht: %s", out.size(), outPath);

            if (reporter != null) {
                reporter.updateProgress(100, 100);
                reporter.updateMessage(msg);
            }
            return new ExecutionResult(ExecutionResult.Status.SUCCESS, msg, outPath, out.size());

        } catch (Exception ex) {
            logger.error("Fehler bei checkSbMismatchVertrag", ex);
            if (reporter != null) reporter.updateMessage("❌ Fehler: " + ex.getMessage());
            return new ExecutionResult(ExecutionResult.Status.FAILURE, "Fehler SB-Abgleich (Verträge): " + ex.getMessage());
        }
    }

// ------------------ 2) SB-Abgleich SCHADEN ------------------

    /**
     * Compare SB Schaden (Excel) vs SB in VIAS (SVA.LU_SACHBEA_SC via SAB) et génère un XLSX.
     * Sortie: ...\AUDIT\_reports\sb_mismatch_schaden_YYYYMMDD_HHmmss.xlsx
     * NB: Excel donne LU_SNR (ex: 2404343) — on convertit en LU_SNR_TEXT pour l'affichage/chemin.
     */
    public ExecutionResult checkSbMismatchSchaden(ProgressReporter reporter) {
        try {
            if (reporter != null) {
                reporter.updateProgress(0, 100);
                reporter.updateMessage("Prüfe SB-Abweichungen (Schaden)...");
            }

            // 1) Lire Excel
            List<RowData> rows = fileService.readFile(SCHADEN_PATH);
            if (rows == null || rows.isEmpty()) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE,
                        "Schadenliste.xlsx ist leer oder nicht lesbar: " + SCHADEN_PATH);
            }

            // Excel -> map LU_SNR (numérique) -> SB Schaden
            Map<String, String> excelSbByLuSnr = new LinkedHashMap<>();
            for (RowData r : rows) {
                String luSnr = r.getValues().getOrDefault("Schaden Nr. CS", "").trim(); // LU_SNR
                String sb = r.getValues().getOrDefault("SB Schaden", "").trim();
                if (!luSnr.isEmpty() && !sb.isEmpty()) {
                    // garder que les chiffres (au cas où Excel formate)
                    excelSbByLuSnr.put(luSnr.replaceAll("\\D+", ""), sb);
                }
            }
            if (excelSbByLuSnr.isEmpty()) {
                return new ExecutionResult(ExecutionResult.Status.FAILURE,
                        "Keine Schaden-Nummern/SB Schaden in der Schadenliste gefunden.");
            }

            // 2) VIAS: fetch -> SB réel par LU_SNR (on mappe via vsnText->luSnr)
            List<VsnAuditRecord> vsn = auditRepository.fetchSchadenDocumentsByVsnNr(new ArrayList<>(excelSbByLuSnr.keySet()));
            Map<String, Set<String>> viasSbsByLuSnr = new HashMap<>();
            Map<String, String> textByLuSnr = new HashMap<>(); // pour affichage chemin

            for (VsnAuditRecord v : vsn) {
                String luSnrText = v.getVsnNummer();          // ex: CS-2024-04343
                String luSnr = textToLuSnr(luSnrText);    // -> 2404343
                String sbVias = joinFullName(v.getVorname(), v.getNachname());

                viasSbsByLuSnr.computeIfAbsent(luSnr, k -> new LinkedHashSet<>()).add(sbVias);
                textByLuSnr.put(luSnr, luSnrText);
            }

            // 3) Lignes mismatch
            List<RowData> out = new ArrayList<>();
            int total = excelSbByLuSnr.size();
            int idx = 0;

            for (var entry : excelSbByLuSnr.entrySet()) {
                idx++;
                String luSnr = entry.getKey();         // 2404343
                String sbExcel = entry.getValue();
                String luSnrText = textByLuSnr.getOrDefault(luSnr, toSvaLuSnrText(luSnr));

                Set<String> viasSet = viasSbsByLuSnr.getOrDefault(luSnr, Collections.emptySet());
                boolean match = viasSet.stream().anyMatch(v -> normalizeName(v).equals(normalizeName(sbExcel)));

                if (!match) {
                    RowData rd = new RowData();
                    rd.put("Typ", "Schaden");
                    rd.put("SchadenNr Text", luSnrText);
                    rd.put("SchadenNr", luSnr);
                    rd.put("SB (Excel)", sbExcel);
                    rd.put("SB (VIAS)", viasSet.isEmpty() ? "(kein Datensatz)" : String.join(", ", viasSet));

                    String excelFolder = plannedBaseFolder(sbExcel, AuditType.SCHADEN.getOrdnerName(), luSnrText);
                    String viasFolders = viasSet.isEmpty()
                            ? ""
                            : String.join(" | ", viasSet.stream()
                            .map(sb -> plannedBaseFolder(sb, AuditType.SCHADEN.getOrdnerName(), luSnrText))
                            .toList());

                    rd.put("Ordner (Excel)", excelFolder);
                    rd.put("Ordner (VIAS)", viasFolders);
                    rd.put("Hinweis", viasSet.size() > 1 ? "Mehrere VIAS-SB" : "Abweichung");

                    out.add(rd);
                }

                if (reporter != null) reporter.updateProgress((int) (idx * 100.0 / total), 100);
            }

            // 4) Export XLSX
            Path reports = ensureReportsDir();
            String outPath = reports.resolve("sb_mismatch_schaden_" + timestamp() + ".xlsx").toString();

            List<String> headers = List.of(
                    "Typ", "SchadenNr Text", "SchadenNr",
                    "SB (Excel)", "SB (VIAS)", "Ordner (Excel)", "Ordner (VIAS)", "Hinweis"
            );
            fileService.writeFileWithHeaders(out, headers, outPath, ExportFormat.XLSX);

            String msg = out.isEmpty()
                    ? "Keine SB-Abweichungen (Schaden) gefunden."
                    : String.format("%d SB-Abweichungen (Schaden) – Bericht: %s", out.size(), outPath);

            if (reporter != null) {
                reporter.updateProgress(100, 100);
                reporter.updateMessage(msg);
            }
            return new ExecutionResult(ExecutionResult.Status.SUCCESS, msg, outPath, out.size());

        } catch (Exception ex) {
            logger.error("Fehler bei checkSbMismatchSchaden", ex);
            if (reporter != null) reporter.updateMessage("❌ Fehler: " + ex.getMessage());
            return new ExecutionResult(ExecutionResult.Status.FAILURE, "Fehler SB-Abgleich (Schaden): " + ex.getMessage());
        }
    }
}