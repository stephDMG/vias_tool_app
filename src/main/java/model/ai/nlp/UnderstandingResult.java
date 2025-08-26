package model.ai.nlp;

import model.ai.ir.QueryIR;
import java.util.ArrayList;
import java.util.List;

public class UnderstandingResult {
    public enum Status { OK, AMBIGUOUS, INVALID }

    public QueryIR ir;                    // peut être partiel
    public boolean isCapabilitiesIntent;  // "was kannst du"...
    public Status status = Status.INVALID;
    public double confidence = 0.0;

    public final List<String> unknownTokens = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    public final List<String> suggestions = new ArrayList<>();
}
