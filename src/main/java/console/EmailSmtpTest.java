package console;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.Scanner;

public class EmailSmtpTest {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("===========================================");
        System.out.println("   SMTP EMAIL TEST - Carl Schröter");
        System.out.println("===========================================\n");

        // Config SMTP
        String host = "mail.carlschroeter.de";
        int port = 25;

        // Expéditeur
        System.out.print("Dein Email (From): ");
        String from = scanner.nextLine().trim();
        if (from.isEmpty()) {
            from = "stephane.dongmo@carlschroeter.de";
        }

        // Destinataire
        System.out.print("Empfänger Email (To): ");
        String to = scanner.nextLine().trim();
        if (to.isEmpty()) {
            to = from; // S'envoyer à soi-même pour test
        }

        // Test avec ou sans auth
        System.out.print("Mit Authentifizierung? (j/n) [n]: ");
        String authChoice = scanner.nextLine().trim().toLowerCase();
        boolean useAuth = authChoice.equals("j") || authChoice.equals("y");

        String username = null;
        String password = null;

        if (useAuth) {
            System.out.print("Username (Email): ");
            username = scanner.nextLine().trim();
            if (username.isEmpty()) username = from;

            System.out.print("Passwort: ");
            password = scanner.nextLine().trim();
        }

        // Test STARTTLS
        System.out.print("STARTTLS aktivieren? (j/n) [n]: ");
        String tlsChoice = scanner.nextLine().trim().toLowerCase();
        boolean useTls = tlsChoice.equals("j") || tlsChoice.equals("y");

        System.out.println("\n--- Konfiguration ---");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        System.out.println("From: " + from);
        System.out.println("To: " + to);
        System.out.println("Auth: " + useAuth);
        System.out.println("TLS: " + useTls);
        System.out.println("---------------------\n");

        // Properties
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(useAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        // Debug activé pour voir les échanges SMTP
        props.put("mail.debug", "true");

        try {
            Session session;

            if (useAuth) {
                final String user = username;
                final String pass = password;
                session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass);
                    }
                });
            } else {
                session = Session.getInstance(props);
            }

            session.setDebug(true);

            System.out.println("Sende Test-Email...\n");

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("[TEST] VIAS OP-Liste Email Test");
            message.setText("Dies ist eine Test-Email vom VIAS Export Tool.\n\n" +
                    "Wenn Sie diese Email erhalten, funktioniert die SMTP-Konfiguration.\n\n" +
                    "Konfiguration:\n" +
                    "- Host: " + host + "\n" +
                    "- Port: " + port + "\n" +
                    "- Auth: " + useAuth + "\n" +
                    "- TLS: " + useTls + "\n\n" +
                    "Grüße,\nVIAS Export Tool");

            Transport.send(message);

            System.out.println("\n===========================================");
            System.out.println("   ✅ EMAIL ERFOLGREICH GESENDET!");
            System.out.println("===========================================");
            System.out.println("Prüfe dein Postfach: " + to);

        } catch (AuthenticationFailedException e) {
            System.err.println("\n❌ AUTHENTIFIZIERUNG FEHLGESCHLAGEN!");
            System.err.println("Fehler: " + e.getMessage());
            System.err.println("\n→ Versuche ohne Auth oder prüfe Username/Passwort");

        } catch (MessagingException e) {
            System.err.println("\n❌ EMAIL KONNTE NICHT GESENDET WERDEN!");
            System.err.println("Fehler: " + e.getMessage());

            if (e.getCause() != null) {
                System.err.println("Ursache: " + e.getCause().getMessage());
            }

            System.err.println("\n→ Mögliche Lösungen:");
            System.err.println("  1. Firewall prüfen");
            System.err.println("  2. IT fragen ob Relay erlaubt ist");
            System.err.println("  3. Anderen Port versuchen");

        } catch (Exception e) {
            System.err.println("\n❌ UNBEKANNTER FEHLER!");
            e.printStackTrace();
        }

        scanner.close();
    }
}