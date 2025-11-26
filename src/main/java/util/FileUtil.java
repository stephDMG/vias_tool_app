package util;


import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.impl.FileServiceImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;

/**
 * Hilfsklasse für Dateioperationen.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class FileUtil {
    public static final String BASE_AUDIT_PATH = "X:\\FREIE ZONE\\000_AUDIT_2025\\AUDIT\\";
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final DateTimeFormatter FILE_DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");


    /**
     * Erstellt Ausgabeverzeichnis falls nicht vorhanden.
     */
    public static void ensureDirectoryExists(String path) {
        try {
            Path dir = Paths.get(path).getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new RuntimeException(" ❌ Kann Verzeichnis nicht erstellen: " + path, e);

        }
    }

    /**
     * Erkennt Dateiformat anhand Dateiendung.
     */
    public static ExportFormat detectFormat(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();

        return switch (extension) {
            case "csv" -> ExportFormat.CSV;
            case "xlsx", "xls" -> ExportFormat.XLSX;
            case "txt" -> ExportFormat.TXT;
            case "pdf" -> ExportFormat.PDF;
            default -> throw new IllegalArgumentException("Unbekanntes Format: " + extension);
        };
    }

    /**
     * Gibt Dateiendung zurück.
     */
    public static String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot + 1) : "";
    }

    /**
     * Prüft ob Datei existiert und lesbar ist.
     */
    public static boolean isValidFile(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.canRead() && file.isFile();
    }

    /**
     * Erstellt Backup-Dateiname mit Timestamp.
     */
    public static String createBackupName(String originalPath) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String nameWithoutExt = originalPath.substring(0, originalPath.lastIndexOf('.'));
        String extension = getFileExtension(originalPath);

        return nameWithoutExt + "_backup_" + timestamp + "." + extension;
    }

    /**
     * Bereinigt Dateinamen von ungültigen Zeichen.
     */
    // Dans src/main/java/util/FileUtil.java
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

    /**
     * Gibt Dateigröße in menschenlesbarem Format zurück.
     */
    public static String getFileSizeFormatted(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "0 B";

        long size = file.length();
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024) + " MB";
        return (size / 1024 / 1024 / 1024) + " GB";
    }


    /**
     * Erstellt den vollständigen Zielpfad für das Dokument gemäß der Audit-Struktur und stellt sicher,
     * dass alle Verzeichnisse existieren.
     * Struktur: X:\...\SB_[Vorname]_[Nachname]\[Vertrag|Schaden]\[Schlüssel]\[Beschreibung]\
     *
     * @param vorname      Vorname des Sachbearbeiters.
     * @param nachname     Nachname des Sachbearbeiters.
     * @param auditType    "Vertrag" oder "Schaden".
     * @param schluessel   Policennummer oder Schaden-Nummer.
     * @param beschreibung Dokumenten-Beschreibung (z.B. "01) Schriftwechsel").
     * @return Der vollständige Zielpfad als String.
     */
    public static String buildAndEnsureTargetPath(String vorname, String nachname, String auditType,
                                                  String schluessel, String beschreibung) {

        // 1. Pfad-Teile bereinigen
        String sbDir = sanitizeFileName(vorname) + " " + sanitizeFileName(nachname);
        String schluesselDir = sanitizeFileName(schluessel);
        String beschreibungDir = sanitizeFileName(beschreibung);
        String typeDir = sanitizeFileName(auditType);

        Path targetPath = Path.of(BASE_AUDIT_PATH)
                .resolve(sbDir)
                .resolve(typeDir)
                .resolve(schluesselDir)
                .resolve(beschreibungDir);

        // 2. Verzeichnisse erstellen
        try {
            // Files.createDirectories ist sicher für bereits existierende Pfade
            Files.createDirectories(targetPath);
        } catch (IOException e) {
            logger.error("❌ Konnte Zielverzeichnis nicht erstellen: {}", targetPath, e);
            throw new RuntimeException("Konnte Zielverzeichnis nicht erstellen: " + targetPath, e);
        }

        return targetPath.toString();
    }


    /**
     * Generiert einen eindeutigen Dateinamen (Betreff.Extension) unter Berücksichtigung von Konflikten.
     * Wenn der Betreff bereits in 'existingBetreffs' enthalten ist, wird er mit Datum/Uhrzeit ergänzt.
     *
     * @param betreff     Der Betreff (Basis-Dateiname).
     * @param bezugsdatum Das Bezugsdatum des Dokuments.
     * @param uhrzeit     Die Erstellungszeit des Dokuments.
     * @param extension   Die Dateierweiterung (ohne Punkt).
     * @return Der eindeutige, sichere Dateiname (ohne Pfad).
     */
    // FileUtil.java
    public static String generateUniqueFileName(String betreff, java.time.LocalDateTime bezugsdatum,
                                                java.time.LocalDateTime uhrzeit, String extension,
                                                java.util.Set<String> existingBaseNames,
                                                boolean stampAlways) {
        String safeBetreff = sanitizeFileName(betreff);
        String baseName = safeBetreff.isEmpty() ? "Dokument_ohne_Betreff" : safeBetreff;

        boolean mustStamp = stampAlways || existingBaseNames.contains(baseName);

        if (!mustStamp) {
            // Pas de doublon => on garde le nom simple
            existingBaseNames.add(baseName);
            return baseName + "." + extension;
        }

        // Priorité à l'heure réelle
        java.time.LocalDateTime dateTimeToUse = (uhrzeit != null) ? uhrzeit : bezugsdatum;

        java.time.format.DateTimeFormatter FN_FMT =
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH.mm.ss");

        String dateTimePart = (dateTimeToUse != null)
                ? dateTimeToUse.format(FN_FMT)
                : java.time.LocalDateTime.now().format(FN_FMT);

        String finalBaseName = baseName + " - " + dateTimePart;

        // Si deux docs tombent à la même seconde, on différencie (2), (3), ...
        String uniqueName = finalBaseName;
        int suffix = 2;
        while (existingBaseNames.contains(uniqueName)) {
            uniqueName = finalBaseName + " (" + suffix++ + ")";
        }
        existingBaseNames.add(uniqueName);

        return uniqueName + "." + extension;
    }


    // FileUtil.java
    public static String buildTargetPathString(String vorname, String nachname, String auditType,
                                               String schluessel, String beschreibung) {
        String sbDir = sanitizeFileName(vorname) + " " + sanitizeFileName(nachname);
        String schluesselDir = sanitizeFileName(schluessel);
        String beschreibungDir = sanitizeFileName(beschreibung);
        String typeDir = sanitizeFileName(auditType);

        java.nio.file.Path targetPath = java.nio.file.Path.of(BASE_AUDIT_PATH)
                .resolve(sbDir)
                .resolve(typeDir)
                .resolve(schluesselDir)
                .resolve(beschreibungDir);

        return targetPath.toString();
    }


    /**
     * Führt die Kopieroperation einer Datei von Quelle zu Ziel durch.
     *
     * @param sourcePath      Physischer Pfad der Quelldatei.
     * @param targetDirectory Pfad zum Zielordner.
     * @param newFileName     Der neue, eindeutige Dateiname (inkl. Extension).
     * @throws IOException Wenn die Datei nicht kopiert werden kann.
     */
    public static void copyFile(String sourcePath, String targetDirectory, String newFileName) throws IOException {
        Path source = Paths.get(sourcePath);
        Path target = Paths.get(targetDirectory, newFileName);

        // Überprüfung, ob Quelldatei existiert und lesbar ist
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            logger.warn("❌ Quelldatei nicht gefunden oder nicht zugreifbar: {}", sourcePath);
            throw new IOException("Quelldatei nicht gefunden oder nicht zugreifbar: " + sourcePath);
        }

        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}