package file.extrator.protocol;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable protocol entry. */
public final class ProtocolEntry implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public enum Level {
        INFO,        // neutrale Information
        WARN,        // potentielle Probleme
        CORRECTION,  // automatische Korrektur durchgeführt
        MISSING,     // erwartetes Feld fehlt
        SYMBOL       // besondere Markierung/Restwert (z. B. "V")
    }

    private final String extractorType;
    private final Integer rowNo;         // kann null sein
    private final Level level;
    private final String code;           // kurzer techn./fachlicher Code
    private final String message;        // menschenlesbar
    private final Map<String, String> details; // Kontext (optional)
    private final Instant timestamp;

    private ProtocolEntry(String extractorType,
                          Integer rowNo,
                          Level level,
                          String code,
                          String message,
                          Map<String, String> details,
                          Instant timestamp) {

        this.extractorType = Objects.requireNonNull(extractorType, "extractorType");
        this.rowNo = rowNo;
        this.level = Objects.requireNonNull(level, "level");
        this.code = code;
        this.message = message;
        this.details = (details == null || details.isEmpty())
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.timestamp = (timestamp == null) ? Instant.now() : timestamp;
    }

    // ---------- Static factories (no Builder) ----------
    public static ProtocolEntry info(String extractorType, String code, String message) {
        return new ProtocolEntry(extractorType, null, Level.INFO, code, message, null, null);
    }
    public static ProtocolEntry info(String extractorType, String code, String message, Integer rowNo, Map<String,String> details) {
        return new ProtocolEntry(extractorType, rowNo, Level.INFO, code, message, details, null);
    }

    public static ProtocolEntry warn(String extractorType, String code, String message) {
        return new ProtocolEntry(extractorType, null, Level.WARN, code, message, null, null);
    }
    public static ProtocolEntry warn(String extractorType, String code, String message, Integer rowNo, Map<String,String> details) {
        return new ProtocolEntry(extractorType, rowNo, Level.WARN, code, message, details, null);
    }

    public static ProtocolEntry correction(String extractorType, String code, String message) {
        return new ProtocolEntry(extractorType, null, Level.CORRECTION, code, message, null, null);
    }
    public static ProtocolEntry correction(String extractorType, String code, String message, Integer rowNo, Map<String,String> details) {
        return new ProtocolEntry(extractorType, rowNo, Level.CORRECTION, code, message, details, null);
    }

    public static ProtocolEntry missing(String extractorType, String code, String message) {
        return new ProtocolEntry(extractorType, null, Level.MISSING, code, message, null, null);
    }
    public static ProtocolEntry missing(String extractorType, String code, String message, Integer rowNo, Map<String,String> details) {
        return new ProtocolEntry(extractorType, rowNo, Level.MISSING, code, message, details, null);
    }

    public static ProtocolEntry symbol(String extractorType, String code, String message) {
        return new ProtocolEntry(extractorType, null, Level.SYMBOL, code, message, null, null);
    }
    public static ProtocolEntry symbol(String extractorType, String code, String message, Integer rowNo, Map<String,String> details) {
        return new ProtocolEntry(extractorType, rowNo, Level.SYMBOL, code, message, details, null);
    }

    // ---------- Getters ----------
    public String  getExtractorType() { return extractorType; }
    public Integer getRowNo()         { return rowNo; }
    public Level   getLevel()         { return level; }
    public String  getCode()          { return code; }
    public String  getMessage()       { return message; }
    public Map<String, String> getDetails() { return details; }
    public Instant getTimestamp()     { return timestamp; }

    public String timestampIso() {
        return DateTimeFormatter.ISO_INSTANT.format(timestamp);
    }


    @Override public String toString() {
        return "ProtocolEntry{" +
                "extractorType='" + extractorType + '\'' +
                ", rowNo=" + rowNo +
                ", level=" + level +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", details=" + details +
                ", timestamp=" + timestamp +
                '}';
    }
}
