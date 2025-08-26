package model.ai.ir;

import java.util.ArrayList;
import java.util.List;

public class QueryIR {
    public ContextType context = ContextType.UNKNOWN;
    public final List<FilterGroup> filters = new ArrayList<>();
    public final List<Projection> projections = new ArrayList<>();
    public final List<Sort> sortOrders = new ArrayList<>();
    public Integer limit = null;

    public QueryIR() {
        // Start with one default filter group
        this.filters.add(new FilterGroup());
    }

    // Helper to add a predicate to the first (main) filter group
    public void addPredicate(Predicate predicate) {
        if (this.filters.isEmpty()) {
            this.filters.add(new FilterGroup());
        }
        this.filters.get(0).predicates.add(predicate);
    }
}