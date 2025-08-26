package model.ai.ir;

public class Projection {
    public final String field;     // Semantic field, e.g., "vsn"
    public final boolean exclude;  // true for "außer..."
    public int order = 999;        // For "zuerst..." logic

    public Projection(String field, boolean exclude) {
        this.field = field;
        this.exclude = exclude;
    }
}