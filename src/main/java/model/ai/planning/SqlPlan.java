package model.ai.planning;

import java.util.ArrayList;
import java.util.List;

public class SqlPlan {
    public String sql;
    public final List<Object> params = new ArrayList<>(); // Für PreparedStatement
    public final List<String> headers = new ArrayList<>();
}