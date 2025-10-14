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
     * @param reporter Schnittstelle zur Berichterstattung über den Fortschritt.
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
     * @param keyHeader Der Header der Spalte, die den Schlüssel enthält (z.B. "Policennummer" oder "Schaden Nr. CS").
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
     * @param records Liste der zu kopierenden AuditDocumentRecords.
     * @param reporter ProgressReporter.
     * @param startProgress Startwert für den Fortschritt (z.B. 0 oder 50).
     * @param endProgress Endwert für den Fortschritt (z.B. 50 oder 100).
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


    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unbekannt";
        }
        // Remplacer uniquement les caractères ILLÉGAUX (pas les espaces) par un underscore.
        // Les caractères illégaux Windows sont : \ / : * ? " < > |
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Supprimer les points à la fin et les underscores multiples
        sanitized = sanitized.replaceAll("\\.+$", "");
        sanitized = sanitized.replaceAll("_{2,}", "_");

        // L'espace est maintenant CONSERVÉ, ce qui résout votre problème d'affichage (01) Schriftwechsel - ...)

        if (sanitized.isEmpty()) {
            return "unbekannt";
        }
        return sanitized;
    }
}