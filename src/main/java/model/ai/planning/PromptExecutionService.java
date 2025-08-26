package model.ai.planning;

import model.ai.ir.QueryIR;

public interface PromptExecutionService {
    SqlPlan plan(QueryIR ir);
}