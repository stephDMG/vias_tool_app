package service.emaill;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Einfacher E-Mail-Dienst auf Basis von JavaMail.
 * Unterstützt Textkörper und Dateianhänge.
 */
public class EmailService {

    private final Properties properties;
    private final String username;
    private final String password;

    /**
     * Erstellt einen neuen EmailService.
     *
     * @param host     SMTP-Host
     * @param port     SMTP-Port
     * @param username Benutzername/Absenderadresse
     * @param password Passwort oder App-spezifisches Passwort
     */
    public EmailService(String host, String port, String username, String password) {
        this.properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);

        this.username = username;
        this.password = password;
    }

    /**
     * Sendet eine E-Mail mit optionalen Dateianhängen.
     *
     * @param to          Empfängeradresse (kommagetrennt möglich)
     * @param subject     Betreff
     * @param body        Nachrichtentext (UTF-8)
     * @param attachments Liste von Dateien, die angehängt werden sollen (kann leer sein)
     * @throws MessagingException wenn das Senden fehlschlägt
     * @throws IOException        bei Fehlern beim Anhängen von Dateien
     */
    public void sendEmail(String to, String subject, String body, List<File> attachments) throws MessagingException, IOException {
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(body, "text/plain; charset=utf-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        for (File file : attachments) {
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(file);
            multipart.addBodyPart(attachmentPart);
        }

        message.setContent(multipart);
        Transport.send(message);
    }
}