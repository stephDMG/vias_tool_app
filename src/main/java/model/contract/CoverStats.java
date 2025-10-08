package model.contract;

/**
 * CoverStats
 * <p>
 * Dieses POJO enthält aggregierte Kennzahlen (KPIs)
 * über eine Menge von Cover-Datensätzen.
 * <p>
 * Typische KPIs:
 * - Gesamtanzahl Verträge.
 * - Anzahl aktiver/abgelaufener Verträge.
 * - Anzahl stornierter/gekündigter Verträge.
 * - Anzahl Verträge mit Kündigungsfristverkürzung.
 * - Durchschnittliche Laufzeit.
 * - Gesamtsumme versicherte Deckung.
 * - Prämien- und Schadenkennzahlen.
 */
public class CoverStats {

    // Basis-KPIs
    private int totalContracts;          // Gesamtanzahl
    private int activeContracts;         // aktiv
    private int endedContracts;          // beendet
    private double averageDurationYears; // durchschnittliche Laufzeit
    private double totalCoverageAmount;  // Gesamtsumme Deckung

    // Erweiterte KPIs
    private int cancelledContracts;         // gekündigt / storniert
    private int shortenedNoticeContracts;   // mit Kündigungsfristverkürzung

    // Prämien- und Schadendaten
    private double totalPremiumPaid;        // gezahlte Prämie
    private double totalPremiumExpected;    // erwartete Prämie
    private double totalClaimsAmount;       // Schadenhöhe gesamt
    private double claimRatio;              // Schadenquote (Schäden / Prämien)

    /**
     * Standard-Konstruktor.
     */
    public CoverStats() {
    }

    /**
     * Voll-Konstruktor.
     */
    public CoverStats(int totalContracts,
                      int activeContracts,
                      int endedContracts,
                      double averageDurationYears,
                      double totalCoverageAmount,
                      int cancelledContracts,
                      int shortenedNoticeContracts,
                      double totalPremiumPaid,
                      double totalPremiumExpected,
                      double totalClaimsAmount,
                      double claimRatio) {
        this.totalContracts = totalContracts;
        this.activeContracts = activeContracts;
        this.endedContracts = endedContracts;
        this.averageDurationYears = averageDurationYears;
        this.totalCoverageAmount = totalCoverageAmount;
        this.cancelledContracts = cancelledContracts;
        this.shortenedNoticeContracts = shortenedNoticeContracts;
        this.totalPremiumPaid = totalPremiumPaid;
        this.totalPremiumExpected = totalPremiumExpected;
        this.totalClaimsAmount = totalClaimsAmount;
        this.claimRatio = claimRatio;
    }

    // ---------------- Getter & Setter ----------------

    public int getTotalContracts() {
        return totalContracts;
    }

    public void setTotalContracts(int totalContracts) {
        this.totalContracts = totalContracts;
    }

    public int getActiveContracts() {
        return activeContracts;
    }

    public void setActiveContracts(int activeContracts) {
        this.activeContracts = activeContracts;
    }

    public int getEndedContracts() {
        return endedContracts;
    }

    public void setEndedContracts(int endedContracts) {
        this.endedContracts = endedContracts;
    }

    public double getAverageDurationYears() {
        return averageDurationYears;
    }

    public void setAverageDurationYears(double averageDurationYears) {
        this.averageDurationYears = averageDurationYears;
    }

    public double getTotalCoverageAmount() {
        return totalCoverageAmount;
    }

    public void setTotalCoverageAmount(double totalCoverageAmount) {
        this.totalCoverageAmount = totalCoverageAmount;
    }

    public int getCancelledContracts() {
        return cancelledContracts;
    }

    public void setCancelledContracts(int cancelledContracts) {
        this.cancelledContracts = cancelledContracts;
    }

    public int getShortenedNoticeContracts() {
        return shortenedNoticeContracts;
    }

    public void setShortenedNoticeContracts(int shortenedNoticeContracts) {
        this.shortenedNoticeContracts = shortenedNoticeContracts;
    }

    public double getTotalPremiumPaid() {
        return totalPremiumPaid;
    }

    public void setTotalPremiumPaid(double totalPremiumPaid) {
        this.totalPremiumPaid = totalPremiumPaid;
    }

    public double getTotalPremiumExpected() {
        return totalPremiumExpected;
    }

    public void setTotalPremiumExpected(double totalPremiumExpected) {
        this.totalPremiumExpected = totalPremiumExpected;
    }

    public double getTotalClaimsAmount() {
        return totalClaimsAmount;
    }

    public void setTotalClaimsAmount(double totalClaimsAmount) {
        this.totalClaimsAmount = totalClaimsAmount;
    }

    public double getClaimRatio() {
        return claimRatio;
    }

    public void setClaimRatio(double claimRatio) {
        this.claimRatio = claimRatio;
    }
}
