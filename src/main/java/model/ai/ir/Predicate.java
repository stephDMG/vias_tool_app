package model.ai.ir;

public class Predicate {
    public final String field;      // Semantic field, e.g., "makler.id"
    public final Op op;             // Operator from the enum
    public final Object value;      // Can be a String, List, or Range object

    public Predicate(String field, Op op, Object value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }
}
