package model.op.email;

import java.time.LocalDateTime;

public class EmailMapping {

    private Long id;
    private String kundeName;
    private String policeNr;
    private String versicherungsnehmer;
    private String toEmail;
    private String ccEmail;
    private String language;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public EmailMapping() {
    }

    public EmailMapping(String kundeName, String policeNr, String versicherungsnehmer) {
        this.kundeName = kundeName;
        this.policeNr = policeNr;
        this.versicherungsnehmer = versicherungsnehmer;
        this.language = "DE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKundeName() { return kundeName; }
    public void setKundeName(String kundeName) { this.kundeName = kundeName; }

    public String getPoliceNr() { return policeNr; }
    public void setPoliceNr(String policeNr) { this.policeNr = policeNr; }

    public String getVersicherungsnehmer() { return versicherungsnehmer; }
    public void setVersicherungsnehmer(String versicherungsnehmer) { this.versicherungsnehmer = versicherungsnehmer; }

    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }

    public String getCcEmail() { return ccEmail; }
    public void setCcEmail(String ccEmail) { this.ccEmail = ccEmail; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean hasEmail() {
        return toEmail != null && !toEmail.isBlank();
    }

    @Override
    public String toString() {
        return "EmailMapping{" + policeNr + " -> " + toEmail + "}";
    }
}