package model.ai.ir;

import java.util.ArrayList;
import java.util.List;

public class FilterGroup {
    public final List<Predicate> predicates = new ArrayList<>();
    public final List<FilterGroup> subGroups = new ArrayList<>();
    public Logic logic = Logic.AND; // Default to AND
    public boolean negated = false; // For "NOT (...)" groups
    public enum Logic {AND, OR}
}