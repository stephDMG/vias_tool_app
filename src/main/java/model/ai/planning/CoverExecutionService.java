package model.ai.planning;

import model.ai.AiReportTemplate;

public class CoverExecutionService implements PromptExecutionService {
    private final CoverPlanner planner;

    public CoverExecutionService(AiReportTemplate template) {
        this.planner = new CoverPlanner(template);
    }

    @Override
    public SqlPlan plan(model.ai.ir.QueryIR ir) {
        return planner.fromIR(ir);
    }
}