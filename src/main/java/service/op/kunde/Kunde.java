package service.op.kunde;

import service.op.Hartrodt;

import java.util.Objects;

public class Kunde {
    private final String kundeName;
    private final String policeNr;
    private final String name;
    private final String land;
    private final String ort;

    public Kunde(String kundeName, String policeNr, String name, String land, String ort) {
        this.kundeName = kundeName;
        this.policeNr = safeTrim(policeNr);
        this.name = safeTrim(name);
        this.land = safeTrim(land);
        this.ort = safeTrim(ort);
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    // Getter
    public String getPoliceNr() {
        return policeNr;
    }

    public String getName() {
        return name;
    }

    public String getLand() {
        return land;
    }

    public String getOrt() {
        return ort;
    }

    public String getKundeName() {return kundeName;}

    // Deduplizierung nach Police/Land/Ort (Name kann je nach Schreibweise variieren)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Kunde that)) return false;
        return Objects.equals(policeNr, that.policeNr)
                && Objects.equals(land, that.land)
                && Objects.equals(ort, that.ort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policeNr, land, ort);
    }

    @Override
    public String toString() {
        return kundeName+"{" +
                "policeNr='" + policeNr + '\'' +
                ", name='" + name + '\'' +
                ", land='" + land + '\'' +
                ", ort='" + ort + '\'' +
                '}';
    }
}