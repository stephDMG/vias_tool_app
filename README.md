# VIAS Export Tool

Dies ist ein Java/JavaFX-basiertes Tool zum Import, zur Verarbeitung und zum Export von Daten (z. B. aus PDF, CSV/XLSX
und Datenbank) mit einer grafischen Benutzeroberfläche.

## Projektüberblick

- UI: JavaFX (FXML, Controller unter `src/main/java/gui/controller`, FXML unter `src/main/resources/fxml`)
- Styles: BootstrapFX + eigene CSS (`src/main/resources/css`)
- Services/Business-Logik: Pakete unter `src/main/java/service` und `src/main/java/model`
- Einstiegspunkte:
    - GUI: `gui.VIASGuiApplication`
    - Konsole/Batch: `console.Main` (falls vorhanden)

## Voraussetzungen

- Java 11 oder höher
- Maven
- Windows wird offiziell unterstützt (PowerShell-Skripte `run-vias.ps1`, `release.ps1` vorhanden)

## Build & Start

1. Build:
    - `mvn clean package`
2. Start (GUI):
    - `mvn exec:java -Dexec.mainClass=gui.VIASGuiApplication` oder
    - PowerShell: `./run-vias.ps1`
3. Release/Packaging:
    - `./release.ps1` (Details siehe Skript)

### Ganz Einfach auf Powershell eingeben:

- `vias-tool start` oder `vias-tool build`

## Ordnerstruktur (Auszug)

- `src/main/java/gui` – JavaFX Application und Controller
- `src/main/resources/fxml` – FXML-Dateien (z. B. `MainWindow.fxml`)
- `src/main/resources/css` – Stylesheets (`styles.css`, `bootstrap3.css`)
- `src/main/resources/images` – Bilder/Icons (z. B. `banner.png`, `logo.png`)
- `src/main/java/service` – Services (z. B. `LoginService`, `AccessControlService`)
- `src/main/java/model` – Domänenmodelle und KI-Module (z. B. `model.ai.planning.CoverPlanner`)
- `src/test/java/` – Unit-Tests (JUnit 5) (z. B. `NLParserTest.java`)

## GUI-Einstieg

`VIASGuiApplication` lädt `MainWindow.fxml` und wendet BootstrapFX sowie `styles.css` an. Das Fenster heißt „VIAS Export
Tool“ und lädt u. a. ein Banner und das App-Icon (falls vorhanden unter `/images/logo.png`).

## Häufige Aktionen (Menü)

- Datei → Daten importieren → Aus PDF extrahieren, Datei anzeigen (CSV/XLSX)
- Datei → Daten exportieren → Aus Datenbank exportieren
- Datenverarbeitung → Pivot-Tabelle erstellen, Daten anreichern, OP-Listen Export (ggf. statusabhängig)
- Toolbar: Dashboard, KI-Assistent

## Entwicklungshinweise

- Logging: SLF4J/Logger (siehe `VIASGuiApplication`)
- Styles: BootstrapFX + eigene CSS
- FXML lädt Controller via `fx:controller`, Logik in `gui.controller.*`

## Dokumentation

- API-Dokumentation: Javadoc-Kommentare in zentralen Klassen (siehe unten). Bei neuen Klassen bitte Javadoc ergänzen.
- Architekturdokumentation: Dieser README beschreibt das „Big Picture“. Detaillierte Paketbeschreibungen können als
  `package-info.java` hinzugefügt werden.

## Datenbank-Konfiguration (DB access)

Die Anwendung lädt DB-Zugangsdaten aus einer Properties-Datei in folgender Reihenfolge:

1. Extern: config/db.properties (wenn vorhanden)
2. Fallback intern: src/main/resources/db.properties

Vorgehen:

- Kopieren Sie config/db.properties.example nach config/db.properties und passen Sie die Werte (db.url, db.user,
  db.password, db.driver) an Ihre Umgebung an.
- Die Datei config/db.properties ist in .gitignore eingetragen und sollte nicht eingecheckt werden.
- Unterstützte JDBC-Treiber können per Klassenpfad eingebunden werden (siehe db.driver, z. B.
  com.pervasive.jdbc.v2.Driver).

Hinweis: Ohne gültige Zugangsdaten kann das Tool nicht auf Ihre Datenbank zugreifen. Teilen Sie keine Zugangsdaten im
Klartext in Repos/Tickets.

## Lizenz/Urheber

(c) Stephane Dongmo, Carl Schröter GmbH & Co. KG. Alle Rechte vorbehalten.

## Datenbank-Check (CSTEST erreichbar?)

Um zu prüfen, ob Sie von Ihrem Rechner aus Zugriff auf die konfigurierte Datenbank (z. B. CSTEST) haben, können Sie den
Health‑Check starten:

- Maven:
    - mvn exec:java -Dexec.mainClass=console.DbHealthCheck

Der Check verwendet die in DatabaseConfig geladenen Werte (Prio: config/db.properties, dann
src/main/resources/db.properties) und gibt „Verbindung erfolgreich“ oder eine Fehlermeldung aus. Achten Sie darauf, dass
der passende JDBC‑Treiber im Klassenpfad ist (z. B. com.pervasive.jdbc.v2.Driver für Pervasive).
