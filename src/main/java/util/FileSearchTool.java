package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Dienstprogramm zum automatischen Suchen von Dateien auf Windows-Systemen.
 * Diese Klasse bietet Methoden zum Auffinden spezifischer Dateien oder Dateien,
 * die einem Muster entsprechen, in vordefinierten und erweiterten Suchpfaden.
 *
 * @author Stephane Dongmo
 * @since 16/07/2025
 */
public class FileSearchTool {

    private static final Logger logger = LoggerFactory.getLogger(FileSearchTool.class);

    /**
     * Sucht eine Datei anhand ihres Namens auf dem Windows-Computer.
     * Die Suche beginnt in vordefinierten, priorisierten Verzeichnissen und wird
     * bei Bedarf auf eine erweiterte Suche auf dem Laufwerk C: ausgedehnt.
     *
     * @param fileName Der Name der zu suchenden Datei (Groß-/Kleinschreibung wird ignoriert).
     * @return Der vollständige Pfad zur gefundenen Datei als String, oder {@code null}, wenn die Datei nicht gefunden wurde.
     */
    public static String findFile(String fileName) {
        logger.info("🔍 Suche nach Datei: {}", fileName);

        // 1. Priorisierte Windows-Verzeichnisse
        List<String> searchPaths = getWindowsSearchPaths();

        // 2. Suche in den priorisierten Verzeichnissen
        for (String searchPath : searchPaths) {
            Path foundFile = searchInDirectory(searchPath, fileName);
            if (foundFile != null) {
                logger.info("✅ Datei gefunden: {}", foundFile);
                return foundFile.toString();
            }
        }

        // 3. Erweiterte Suche auf Laufwerk C: (mit begrenzter Tiefe)
        logger.info("🔍 Erweiterte Suche auf C:... (begrenzte Tiefe)");
        Path foundFile = searchInDirectory("C:\\", fileName, 3); // Suche bis zu einer Tiefe von 3 Ebenen
        if (foundFile != null) {
            logger.info("✅ Datei gefunden: {}", foundFile);
            return foundFile.toString();
        }

        logger.warn("❌ Datei nicht gefunden: {}", fileName);
        return null;
    }

    /**
     * Sucht nach einer Datei mit dem exakten Namen in gängigen Benutzerverzeichnissen.
     * Durchsucht Desktop, Downloads und das Benutzer-Hauptverzeichnis.
     *
     * @param fileName Der exakte Name der zu suchenden Datei (z.B. "document.pdf").
     * @return Der vollständige Pfad zur ersten gefundenen Datei oder null, wenn nichts gefunden wurde.
     */
    public static String findFileByName(String fileName) {
        String userHome = System.getProperty("user.home");
        List<Path> searchDirs = List.of(
                Paths.get(userHome, "Downloads"),
                Paths.get(userHome, "Desktop"),
                Paths.get(userHome)
        );

        for (Path dir : searchDirs) {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> walk = Files.walk(dir, 2)) { // Sucht 2 Ebenen tief
                    String foundPath = walk
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                            .map(Path::toString)
                            .findFirst()
                            .orElse(null);
                    if (foundPath != null) {
                        return foundPath;
                    }
                } catch (IOException e) {
                    System.err.println("Fehler beim Durchsuchen des Verzeichnisses: " + dir + " - " + e.getMessage());
                }
            }
        }
        return null; // Nichts gefunden
    }


    /**
     * Sucht alle PDF-Dateien, die dem Muster "*Versicherungsbestätigung*.pdf" entsprechen.
     * Die Suche erfolgt in den priorisierten Windows-Suchpfaden.
     *
     * @return Eine Liste von Strings, die die vollständigen Pfade zu allen gefundenen Dateien enthalten.
     */
    public static List<String> findAllVersicherungsFiles() {
        logger.info("🔍 Suche nach allen 'Versicherungsbestätigung'-Dateien...");
        List<String> foundFiles = new ArrayList<>();

        List<String> searchPaths = getWindowsSearchPaths();

        for (String searchPath : searchPaths) {
            List<Path> files = searchMultipleInDirectory(searchPath, "*Versicherungsbestätigung*.pdf");
            for (Path file : files) {
                foundFiles.add(file.toString());
            }
        }

        logger.info("✅ {} 'Versicherungsbestätigung'-Dateien gefunden", foundFiles.size());
        return foundFiles;
    }

    // ==================== PRIVATE METHODEN ====================

    /**
     * Gibt eine Liste von priorisierten Suchpfaden für Windows-Systeme zurück.
     * Dazu gehören gängige Benutzerverzeichnisse (Desktop, Downloads, Dokumente, OneDrive)
     * sowie Projektverzeichnisse und allgemeine Windows-Pfade.
     *
     * @return Eine Liste von Strings, die die priorisierten Verzeichnispfade darstellen.
     */
    private static List<String> getWindowsSearchPaths() {
        List<String> paths = new ArrayList<>();
        String userHome = System.getProperty("user.home");

        // Gängige Benutzerverzeichnisse
        paths.add(userHome + "\\Desktop");
        paths.add(userHome + "\\Downloads");
        paths.add(userHome + "\\Documents");
        paths.add(userHome + "\\OneDrive");
        paths.add(userHome + "\\OneDrive\\Desktop");
        paths.add(userHome + "\\OneDrive\\Documents");

        // Aktuelles Projektverzeichnis und zugehörige Unterverzeichnisse
        paths.add(System.getProperty("user.dir"));
        paths.add(System.getProperty("user.dir") + "\\input");
        paths.add(System.getProperty("user.dir") + "\\files");

        // Allgemeine Windows-Verzeichnisse
        paths.add("C:\\Users\\Public\\Documents");
        paths.add("C:\\temp");
        paths.add("C:\\tmp");

        return paths;
    }

    /**
     * Sucht eine Datei rekursiv in einem Verzeichnis.
     * Diese Methode ist eine Überladung von {@link #searchInDirectory(String, String, int)}
     * und verwendet {@code Integer.MAX_VALUE} als maximale Suchtiefe, um eine vollständige Rekursion zu gewährleisten.
     *
     * @param directoryPath Der Pfad des Verzeichnisses, in dem gesucht werden soll.
     * @param fileName      Der Name der zu suchenden Datei.
     * @return Der Pfad zur gefundenen Datei als {@link Path}-Objekt, oder {@code null}, wenn die Datei nicht gefunden wurde.
     */
    private static Path searchInDirectory(String directoryPath, String fileName) {
        return searchInDirectory(directoryPath, fileName, Integer.MAX_VALUE);
    }

    /**
     * Sucht eine Datei in einem Verzeichnis mit einer begrenzten Rekursionstiefe.
     * Die Suche wird abgebrochen, sobald die Datei gefunden wurde.
     *
     * @param directoryPath Der Pfad des Verzeichnisses, in dem gesucht werden soll.
     * @param fileName      Der Name der zu suchenden Datei (Groß-/Kleinschreibung wird ignoriert).
     * @param maxDepth      Die maximale Rekursionstiefe für die Suche.
     * @return Der Pfad zur gefundenen Datei als {@link Path}-Objekt, oder {@code null}, wenn die Datei nicht gefunden wurde.
     */
    private static Path searchInDirectory(String directoryPath, String fileName, int maxDepth) {
        Path dir = Paths.get(directoryPath);
        // Prüfen, ob das Verzeichnis existiert und tatsächlich ein Verzeichnis ist.
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            logger.debug("Verzeichnis existiert nicht oder ist kein Verzeichnis: {}", directoryPath);
            return null;
        }

        // AtomicReference wird verwendet, um den gefundenen Pfad in der inneren Klasse zu speichern.
        AtomicReference<Path> result = new AtomicReference<>();

        try {
            // Durchläuft den Dateibaum, beginnend im angegebenen Verzeichnis.
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                private int currentDepth = 0; // Aktuelle Rekursionstiefe

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    currentDepth++; // Tiefe erhöhen beim Betreten eines Verzeichnisses
                    if (currentDepth > maxDepth) {
                        // Wenn die maximale Tiefe überschritten ist, überspringe dieses Unterverzeichnis.
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE; // Suche fortsetzen
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Prüfen, ob der Dateiname mit dem gesuchten Namen übereinstimmt (Groß-/Kleinschreibung ignorieren).
                    if (file.getFileName().toString().equalsIgnoreCase(fileName)) {
                        result.set(file); // Pfad speichern
                        return FileVisitResult.TERMINATE; // Suche beenden, da Datei gefunden
                    }
                    return FileVisitResult.CONTINUE; // Suche fortsetzen
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Fehler beim Zugriff auf eine Datei ignorieren (z.B. Berechtigungsprobleme)
                    logger.debug("Fehler beim Besuch der Datei {}: {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    currentDepth--; // Tiefe verringern nach dem Verlassen eines Verzeichnisses
                    return FileVisitResult.CONTINUE; // Suche fortsetzen
                }
            });
        } catch (IOException e) {
            logger.debug("Fehler bei der Suche in {}: {}", directoryPath, e.getMessage());
        }

        return result.get(); // Den gefundenen Pfad zurückgeben
    }

    /**
     * Sucht mehrere Dateien in einem Verzeichnis, die einem bestimmten "glob"-Muster entsprechen.
     * Die Suche ist nicht rekursiv in ihrer Standardimplementierung über {@code Files.walkFileTree},
     * kann aber durch die Verwendung von `maxDepth` in `Files.walk` gesteuert werden,
     * hier ist sie auf die Standardtiefe (unbegrenzt) eingestellt, aber die Methode selbst
     * ist für Muster konzipiert, nicht für Rekursionstiefe.
     *
     * @param directoryPath Der Pfad des Verzeichnisses, in dem gesucht werden soll.
     * @param pattern       Das "glob"-Muster, dem die Dateinamen entsprechen müssen (z.B. "*.pdf", "dokument*.txt").
     * @return Eine Liste von {@link Path}-Objekten, die alle gefundenen Dateien repräsentieren.
     */
    private static List<Path> searchMultipleInDirectory(String directoryPath, String pattern) {
        Path dir = Paths.get(directoryPath);
        List<Path> results = new ArrayList<>();

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            logger.debug("Verzeichnis existiert nicht oder ist kein Verzeichnis für Mustersuche: {}", directoryPath);
            return results;
        }

        try {
            // Erstellt einen PathMatcher für das gegebene "glob"-Muster.
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            // Durchläuft den Dateibaum, um Dateien zu finden, die dem Muster entsprechen.
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Prüfen, ob der Dateiname dem Muster entspricht.
                    if (matcher.matches(file.getFileName())) {
                        results.add(file); // Datei zur Ergebnisliste hinzufügen
                    }
                    return FileVisitResult.CONTINUE; // Suche fortsetzen
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Fehler beim Zugriff auf eine Datei ignorieren
                    logger.debug("Fehler beim Besuch der Datei {}: {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.debug("Fehler bei der Mustersuche in {}: {}", directoryPath, e.getMessage());
        }

        return results;
    }
}