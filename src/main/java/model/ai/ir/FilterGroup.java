package model.ai.ir;

import java.util.ArrayList;
import java.util.List;

public class FilterGroup {
    public enum Logic { AND, OR }
    public Logic logic = Logic.AND; // Default to AND
    public boolean negated = false; // For "NOT (...)" groups
    public final List<Predicate> predicates = new ArrayList<>();
    public final List<FilterGroup> subGroups = new ArrayList<>();
}