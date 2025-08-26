package model.ai.ir;

public class Sort {
    public final String field;
    public final Direction direction;

    public Sort(String field, Direction direction) {
        this.field = field;
        this.direction = direction;
    }
}