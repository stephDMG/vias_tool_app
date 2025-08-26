package service;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UpdateService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);

    /**
     * Erstellt einen Task, der den Installer herunterlädt und den Fortschritt meldet.
     */
    public Task<File> createDownloadTask(String installerUrl) {
        return new Task<>() {
            @Override
            protected File call() throws Exception {
                updateMessage("Download wird initialisiert...");
                URL url = toUrl(installerUrl);

                URLConnection connection = url.openConnection();
                long totalBytes = connection.getContentLengthLong();
                if (totalBytes <= 0) {
                    logger.warn("Dateigröße unbekannt (Server liefert kein Content-Length).");
                }

                Path tempFile = Files.createTempFile("VIAS-Export-Tool-Update-", ".msi");
                long transferredBytes = 0;
                byte[] buffer = new byte[8192]; // 8 KB Puffer

                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (isCancelled()) {
                            updateMessage("Download abgebrochen.");
                            throw new InterruptedIOException("Update wurde vom Benutzer abgebrochen.");
                        }
                        out.write(buffer, 0, bytesRead);
                        transferredBytes += bytesRead;

                        if (totalBytes > 0) {
                            updateProgress(transferredBytes, totalBytes);
                            updateMessage(String.format("%.1f / %.1f MB", transferredBytes / 1024.0 / 1024.0, totalBytes / 1024.0 / 1024.0));
                        } else {
                            updateMessage(String.format("%.1f MB heruntergeladen", transferredBytes / 1024.0 / 1024.0));
                        }
                    }
                }
                updateMessage("Download abgeschlossen.");
                logger.info("Installer heruntergeladen nach: {}", tempFile);
                return tempFile.toFile();
            }
        };
    }

    /**
     * Lädt die erwartete SHA-256-Prüfsumme von einer .sha256-Datei.
     */
    public String downloadExpectedChecksum(String checksumUrl) throws Exception {
        URL url = toUrl(checksumUrl);
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes()).trim();
        }
    }

    /**
     * Verifiziert die SHA-256-Prüfsumme einer Datei.
     */
    public boolean verifyChecksum(File file, String expectedChecksum) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                sha256.update(buffer, 0, bytesRead);
            }
        }
        byte[] digest = sha256.digest();
        String actualChecksum = bytesToHex(digest);
        logger.info("Prüfsummen-Vergleich:\nErwartet: {}\nAktuell:  {}", expectedChecksum, actualChecksum);
        return actualChecksum.equalsIgnoreCase(expectedChecksum.replaceAll("\\s+", ""));
    }

    // Hilfsmethode: Konvertiert einen UNC- oder lokalen Pfad in eine URL
    private URL toUrl(String path) throws Exception {
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:/")) {
            return new URL(path);
        }
        return Paths.get(path).toUri().toURL();
    }

    // Hilfsmethode: Konvertiert Bytes in einen Hex-String
    private String bytesToHex(byte[] bytes) {
        return IntStream.range(0, bytes.length)
                .mapToObj(i -> String.format("%02x", bytes[i] & 0xff))
                .collect(Collectors.joining());
    }
}