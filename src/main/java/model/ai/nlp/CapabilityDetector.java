package model.ai.nlp;

public final class CapabilityDetector {
    public static boolean isCapabilitiesQuery(String text) {
        String t = text.toLowerCase();
        return t.contains("was kannst du") || t.contains("was kannst du ?") || t.contains("fähigkeiten")
                || t.contains("hilfe") || t.contains("help") || t.contains("capabilities");
    }

}