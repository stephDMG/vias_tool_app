package model.ai.nlp;

public final class FeatureSwitch {
    public static boolean detectAdvancedFeatures(String text) {
        String t = text.toLowerCase();
        return t.contains("außer") || t.contains("ohne") || t.contains("except")
                || t.contains("zuerst") || t.contains("first")
                || t.matches(".*\\blimit\\s+\\d+.*");
    }
}