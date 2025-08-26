package service.interfaces;

public interface ProgressReporter {
    void updateMessage(String message);
    void updateProgress(long workDone, long max);
}
