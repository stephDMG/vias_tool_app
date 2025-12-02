package service.op.repository;

import config.op.OpListDatabaseConfig;
import model.op.email.EmailMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmailMappingRepository {

    private static final Logger log = LoggerFactory.getLogger(EmailMappingRepository.class);

    public List<EmailMapping> findAll() {
        String sql = "SELECT * FROM email_mappings ORDER BY kunde_name, police_nr";
        List<EmailMapping> result = new ArrayList<>();

        try (Connection conn = OpListDatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Fehler bei findAll: {}", e.getMessage());
        }
        return result;
    }

    public List<EmailMapping> findByKunde(String kundeName) {
        String sql = "SELECT * FROM email_mappings WHERE kunde_name = ? ORDER BY police_nr";
        List<EmailMapping> result = new ArrayList<>();

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, kundeName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Fehler bei findByKunde: {}", e.getMessage());
        }
        return result;
    }

    public Optional<EmailMapping> findByPoliceNr(String policeNr) {
        String sql = "SELECT * FROM email_mappings WHERE police_nr = ?";

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, policeNr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Fehler bei findByPoliceNr: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public List<EmailMapping> findMissingEmails(String kundeName) {
        String sql = "SELECT * FROM email_mappings WHERE kunde_name = ? AND (to_email IS NULL OR to_email = '')";
        List<EmailMapping> result = new ArrayList<>();

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, kundeName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Fehler bei findMissingEmails: {}", e.getMessage());
        }
        return result;
    }

    public void save(EmailMapping mapping) {
        String sql = """
            INSERT INTO email_mappings (kunde_name, police_nr, versicherungsnehmer, to_email, cc_email, language)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(kunde_name, police_nr) DO UPDATE SET
                versicherungsnehmer = excluded.versicherungsnehmer,
                to_email = excluded.to_email,
                cc_email = excluded.cc_email,
                language = excluded.language,
                updated_at = CURRENT_TIMESTAMP
        """;

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, mapping.getKundeName());
            ps.setString(2, mapping.getPoliceNr());
            ps.setString(3, mapping.getVersicherungsnehmer());
            ps.setString(4, mapping.getToEmail());
            ps.setString(5, mapping.getCcEmail());
            ps.setString(6, mapping.getLanguage());
            ps.executeUpdate();

            log.debug("Mapping gespeichert: {}", mapping.getPoliceNr());
        } catch (SQLException e) {
            log.error("Fehler bei save: {}", e.getMessage());
        }
    }

    public void updateEmail(String policeNr, String toEmail, String ccEmail) {
        String sql = "UPDATE email_mappings SET to_email = ?, cc_email = ?, updated_at = CURRENT_TIMESTAMP WHERE police_nr = ?";

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, toEmail);
            ps.setString(2, ccEmail);
            ps.setString(3, policeNr);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                log.info("Email aktualisiert für Police: {}", policeNr);
            }
        } catch (SQLException e) {
            log.error("Fehler bei updateEmail: {}", e.getMessage());
        }
    }

    public void delete(String policeNr) {
        String sql = "DELETE FROM email_mappings WHERE police_nr = ?";

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, policeNr);
            ps.executeUpdate();
            log.info("Mapping gelöscht: {}", policeNr);
        } catch (SQLException e) {
            log.error("Fehler bei delete: {}", e.getMessage());
        }
    }

    public void moveToBackup(String policeNr, String reason) {
        Optional<EmailMapping> opt = findByPoliceNr(policeNr);
        if (opt.isEmpty()) return;

        EmailMapping m = opt.get();
        String insertSql = """
            INSERT INTO email_backup (kunde_name, police_nr, versicherungsnehmer, to_email, cc_email, language, reason)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            ps.setString(1, m.getKundeName());
            ps.setString(2, m.getPoliceNr());
            ps.setString(3, m.getVersicherungsnehmer());
            ps.setString(4, m.getToEmail());
            ps.setString(5, m.getCcEmail());
            ps.setString(6, m.getLanguage());
            ps.setString(7, reason);
            ps.executeUpdate();

            delete(policeNr);
            log.info("Police {} nach Backup verschoben: {}", policeNr, reason);
        } catch (SQLException e) {
            log.error("Fehler bei moveToBackup: {}", e.getMessage());
        }
    }

    public void restoreFromBackup(String policeNr) {
        String selectSql = "SELECT * FROM email_backup WHERE police_nr = ? ORDER BY archived_at DESC LIMIT 1";

        try (Connection conn = OpListDatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {

            ps.setString(1, policeNr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EmailMapping m = new EmailMapping();
                    m.setKundeName(rs.getString("kunde_name"));
                    m.setPoliceNr(rs.getString("police_nr"));
                    m.setVersicherungsnehmer(rs.getString("versicherungsnehmer"));
                    m.setToEmail(rs.getString("to_email"));
                    m.setCcEmail(rs.getString("cc_email"));
                    m.setLanguage(rs.getString("language"));
                    save(m);
                    log.info("Police {} aus Backup wiederhergestellt", policeNr);
                }
            }
        } catch (SQLException e) {
            log.error("Fehler bei restoreFromBackup: {}", e.getMessage());
        }
    }

    private EmailMapping mapRow(ResultSet rs) throws SQLException {
        EmailMapping m = new EmailMapping();
        m.setId(rs.getLong("id"));
        m.setKundeName(rs.getString("kunde_name"));
        m.setPoliceNr(rs.getString("police_nr"));
        m.setVersicherungsnehmer(rs.getString("versicherungsnehmer"));
        m.setToEmail(rs.getString("to_email"));
        m.setCcEmail(rs.getString("cc_email"));
        m.setLanguage(rs.getString("language"));

        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) m.setCreatedAt(created.toLocalDateTime());
        if (updated != null) m.setUpdatedAt(updated.toLocalDateTime());

        return m;
    }
}