package model.ai.nlp;

import model.ai.ir.QueryIR;
import java.util.ArrayList;
import java.util.List;

public interface PromptUnderstandingService {
    UnderstandingResult understand(String prompt);

    class UnderstandingResult {
        public enum Status { OK, AMBIGUOUS, INVALID }

        public QueryIR ir;
        public boolean isCapabilitiesIntent;
        public Status status = Status.INVALID;
        public double confidence = 0.0;

        public final List<String> unknownTokens = new ArrayList<>();
        public final List<String> suggestions = new ArrayList<>();
    }

}