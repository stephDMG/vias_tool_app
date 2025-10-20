package file.extrator.protocol;

import model.RowData;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ProtocolReport {

    private final String sourceFilePath;
    private final String extractorType;
    private final Instant startedAt;

    private Instant finishedAt;
    private final List<ProtocolEntry> entries = new ArrayList<>();

    private int infoCount = 0;
    private int warnCount = 0;
    private int correctionCount = 0;
    private int missingCount = 0;
    private int symbolCount = 0;

    private ProtocolReport(String sourceFilePath, String extractorType) {
        this.sourceFilePath = Objects.requireNonNull(sourceFilePath);
        this.extractorType = Objects.requireNonNull(extractorType);
        this.startedAt = Instant.now();
    }

    public static ProtocolReport start(String sourceFilePath, String extractorType) {
        return new ProtocolReport(sourceFilePath, extractorType);
    }

    /** Kompatibilität: ohne Offset (0) – bisheriges Verhalten */
    public List<RowData> toRowDataList() {
        return toRowDataList(0);
    }

    public ProtocolReport finish() {
        this.finishedAt = Instant.now();
        return this;
    }

    public ProtocolReport addEntry(ProtocolEntry entry) {
        if (entry == null) return this;
        entries.add(entry);
        switch (entry.getLevel()) {
            case INFO -> infoCount++;
            case WARN -> warnCount++;
            case CORRECTION -> correctionCount++;
            case MISSING -> missingCount++;
            case SYMBOL -> symbolCount++;
        }
        return this;
    }

    // ---------- Helpers ----------
    public ProtocolReport info(String code, String message) {
        return addEntry(ProtocolEntry.info(extractorType, code, message));
    }
    public ProtocolReport info(String code, String message, Integer rowNo, Map<String, String> details) {
        return addEntry(ProtocolEntry.info(extractorType, code, message, rowNo, details));
    }

    public ProtocolReport warn(String code, String message) {
        return addEntry(ProtocolEntry.warn(extractorType, code, message));
    }
    public ProtocolReport warn(String code, String message, Integer rowNo, Map<String, String> details) {
        return addEntry(ProtocolEntry.warn(extractorType, code, message, rowNo, details));
    }

    public ProtocolReport correction(String code, String message) {
        return addEntry(ProtocolEntry.correction(extractorType, code, message));
    }
    public ProtocolReport correction(String code, String message, Integer rowNo, Map<String, String> details) {
        return addEntry(ProtocolEntry.correction(extractorType, code, message, rowNo, details));
    }

    public ProtocolReport missing(String code, String message) {
        return addEntry(ProtocolEntry.missing(extractorType, code, message));
    }
    public ProtocolReport missing(String code, String message, Integer rowNo, Map<String, String> details) {
        return addEntry(ProtocolEntry.missing(extractorType, code, message, rowNo, details));
    }

    public ProtocolReport symbol(String code, String message) {
        return addEntry(ProtocolEntry.symbol(extractorType, code, message));
    }
    public ProtocolReport symbol(String code, String message, Integer rowNo, Map<String, String> details) {
        return addEntry(ProtocolEntry.symbol(extractorType, code, message, rowNo, details));
    }

    // ---------- Stats ----------
    public boolean hasWarningsOrCorrectionsOrMissing() {
        return warnCount > 0 || correctionCount > 0 || missingCount > 0;
    }

    public int getInfoCount()       { return infoCount; }
    public int getWarnCount()       { return warnCount; }
    public int getCorrectionCount() { return correctionCount; }
    public int getMissingCount()    { return missingCount; }
    public int getSymbolCount()     { return symbolCount; }
    public int getTotalCount()      { return entries.size(); }

    // ---------- Export (RowData) ----------
    public static List<String> defaultHeaders() {
        return List.of(
                "Extractor",
                "SourceFile",
                "Row",
                "Level",
                "Code",
                "Message",
                "Context",
                "Timestamp"
        );
    }
    /**
     * Baut die Zeilenliste für den Export mit einem Anzeige-Offset für die Zeilennummer.
     * Typischerweise 1 wegen Kopfzeile im Ziel (CSV/XLSX).
     */
    public List<RowData> toRowDataList(int displayRowOffset) {
        String baseName = getSourceFileName();
        return entries.stream().map(e -> {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("Extractor", extractorType);
            map.put("SourceFile", baseName);

            // ⚠️ Offset nur anwenden, wenn rowNo nicht null ist
            String row = "";
            if (e.getRowNo() != null) {
                row = String.valueOf(e.getRowNo() + displayRowOffset);
            }
            map.put("Row", row);

            map.put("Level", e.getLevel().name());
            map.put("Code", safe(e.getCode()));
            map.put("Message", safe(e.getMessage()));
            map.put("Context", serializeDetails(e.getDetails()));
            map.put("Timestamp", e.timestampIso());

            RowData rd = new RowData();
            rd.putAll(map);
            return rd;
        }).collect(Collectors.toList());
    }



    private static String serializeDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) return "";
        return details.entrySet().stream()
                .map(en -> en.getKey() + "=" + Objects.toString(en.getValue(), ""))
                .collect(Collectors.joining(" | "));
    }

    private static String safe(String s) { return (s == null) ? "" : s; }

    private String getSourceFileName() {
        try { return Paths.get(sourceFilePath).getFileName().toString(); }
        catch (Exception e) { return sourceFilePath; }
    }

    // ---------- Getters ----------
    public String getSourceFilePath() { return sourceFilePath; }
    public String getExtractorType()  { return extractorType; }
    public Instant getStartedAt()     { return startedAt; }
    public Instant getFinishedAt()    { return finishedAt; }
    public List<ProtocolEntry> getEntries() { return Collections.unmodifiableList(entries); }
}
