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

/**
 * Hilfsklasse für Dateioperationen.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

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
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unbekannt";
        }
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|\\s]", "_"); // Remplace les caractères invalides et les espaces
        sanitized = sanitized.replaceAll("\\.+$", ""); // Supprime les points à la fin du nom de fichier
        sanitized = sanitized.replaceAll("_{2,}", "_"); // Remplace les underscores consécutifs par un seul

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
}