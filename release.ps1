# =============================================================================
# === VIAS EXPORT TOOL - PROFI RELEASE-SKRIPT ===
# =============================================================================
# Version 4.0 - Mit automatischer Versionierung, Archivierung und Deployment
# =============================================================================

# --- 1. KONFIGURATION ---
$projectDir = $PSScriptRoot
$pomFile = Join-Path $projectDir "pom.xml"
$changelogFile = Join-Path $projectDir "changelog.txt"
$jpackageInputDir = "C:\jpackage_input"
$jpackageLibsDir = Join-Path $jpackageInputDir "libs"
$logoFileSource = Join-Path $projectDir "src\main\resources\images\logo.ico"
$logoFileDest = Join-Path $jpackageLibsDir "logo.ico"
$destLocal = "C:\jpackage_output"
$destPublic = "X:\FREIE ZONE\Dongmo, Stephane\Vias-Tool\jpackage_output"
$versionFilePublic = Join-Path $destPublic "version.txt"
$changelogFilePublic = Join-Path $destPublic "changelog.txt"
$archiveDirPublic = Join-Path $destPublic "archive"

# --- 2. INFORMATIONEN SAMMELN ---
Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host "=== Starte den Release-Prozess fuer das VIAS Export Tool ===" -ForegroundColor Magenta
Write-Host "==========================================================" -ForegroundColor Magenta

try {
    [xml]$pom = Get-Content $pomFile
    $currentVersion = $pom.project.version
    $versionParts = $currentVersion.Split('.')
    $newPatchVersion = [int]$versionParts[2] + 1
    $suggestedVersion = "$($versionParts[0]).$($versionParts[1]).$($newPatchVersion)"
} catch {
    Write-Host "[FEHLER] pom.xml konnte nicht gelesen werden. Breche ab." -ForegroundColor Red; exit
}

# NEU: Version wird vorgeschlagen
Write-Host "Aktuelle Version ist '$($currentVersion)'." -ForegroundColor Yellow
$newVersion = Read-Host -Prompt "Bitte neue Versionsnummer bestaetigen oder eingeben (Vorschlag: $($suggestedVersion))"
if (-not $newVersion) { $newVersion = $suggestedVersion }

Write-Host "Bitte beschreibe die Aenderungen (beende mit 'ENDE' in einer neuen Zeile):" -ForegroundColor Yellow
$commitLines = @()
while (($line = Read-Host) -ne 'ENDE') { $commitLines += "- [Aenderung] $($line)" }

Write-Host "Wohin soll veroeffentlicht werden? [1] Lokal, [2] Public, [3] Beides" -ForegroundColor Yellow
$targetChoice = Read-Host

# --- 3. DATEIEN AKTUALISIEREN (LOKAL) ---
Write-Host "----------------------------------------------------------"
Write-Host "--> Aktualisiere Projektdateien..." -ForegroundColor Cyan
(Get-Content $pomFile) -replace "<version>$($currentVersion)</version>", "<version>$($newVersion)</version>" | Set-Content $pomFile
$date = Get-Date -Format "yyyy-MM-dd"
$newChangelogHeader = "====================`nVersion $newVersion ($date)`n===================="
$newChangelogContent = $newChangelogHeader, $commitLines, "", (Get-Content $changelogFile -ErrorAction SilentlyContinue)
Set-Content -Path $changelogFile -Value $newChangelogContent
Write-Host "[OK] Lokale Dateien auf Version $($newVersion) aktualisiert." -ForegroundColor Green

# --- 4. ANWENDUNG BAUEN ---
Write-Host "----------------------------------------------------------"
Write-Host "--> Baue die Anwendung mit Maven..." -ForegroundColor Cyan
cd $projectDir
mvn clean package
if ($LASTEXITCODE -ne 0) { Write-Host "[FEHLER] Maven Build fehlgeschlagen." -ForegroundColor Red; exit }
Write-Host "[OK] Maven Build erfolgreich abgeschlossen." -ForegroundColor Green

# --- 5. INSTALLER ERSTELLEN ---
Write-Host "----------------------------------------------------------"
Write-Host "--> Bereite Installer-Erstellung vor..." -ForegroundColor Cyan
if (-not (Test-Path $jpackageInputDir)) { New-Item -ItemType Directory -Force -Path $jpackageInputDir | Out-Null }
if (-not (Test-Path $jpackageLibsDir)) { New-Item -ItemType Directory -Force -Path $jpackageLibsDir | Out-Null }
Copy-Item -Path (Join-Path $projectDir "target\vias-export-tool.jar") -Destination $jpackageInputDir
Copy-Item -Path $logoFileSource -Destination $logoFileDest
$jpackageArgs = @( "--name", "`"VIAS Export Tool`"", "--app-version", "`"$newVersion`"", "--win-upgrade-uuid", "`"9e1e88cd-af79-40fd-928d-7e12c76793d7`"", "--input", "`"$jpackageInputDir`"", "--main-jar", "`"vias-export-tool.jar`"", "--main-class", "gui.MainLauncher", "--type", "msi", "--icon", "`"$logoFileDest`"", "--win-shortcut", "--win-menu" )

if ($targetChoice -eq '1' -or $targetChoice -eq '3') {
    Write-Host "Erstelle lokalen Installer..."
    jpackage $jpackageArgs --dest "`"$destLocal`""
    Write-Host "[OK] Lokaler Installer erstellt." -ForegroundColor Green
}
if ($targetChoice -eq '2' -or $targetChoice -eq '3') {
    Write-Host "Erstelle oeffentlichen Installer..."
    # NEU: Alten Installer archivieren
    if (Test-Path $destPublic) {
        $oldInstaller = Get-ChildItem -Path $destPublic -Filter "*.msi" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($oldInstaller) {
            if (-not (Test-Path $archiveDirPublic)) { New-Item -ItemType Directory -Force -Path $archiveDirPublic | Out-Null }
            Move-Item -Path $oldInstaller.FullName -Destination $archiveDirPublic
            Write-Host "--> Alter Installer '$($oldInstaller.Name)' wurde nach '$($archiveDirPublic)' verschoben." -ForegroundColor Cyan
        }
    }
    jpackage $jpackageArgs --dest "`"$destPublic`""
    Write-Host "[OK] Oeffentlicher Installer erstellt." -ForegroundColor Green

    # NEU: version.txt und changelog.txt auf dem Server aktualisieren
    Write-Host "--> Aktualisiere Release-Informationen auf dem Server..." -ForegroundColor Cyan
    Set-Content -Path $versionFilePublic -Value $newVersion
    Copy-Item -Path $changelogFile -Destination $changelogFilePublic
    Write-Host "[OK] Release-Informationen aktualisiert." -ForegroundColor Green
}
# --- 6. PRÜFSUMME ERSTELLEN UND BEREITSTELLEN ---
Write-Host "----------------------------------------------------------"
Write-Host "--> Erstelle SHA-256 Prüfsumme..." -ForegroundColor Cyan

$installerName = "VIAS Export Tool-$($newVersion).msi"
$localInstallerPath = Join-Path $destLocal $installerName
$publicInstallerPath = Join-Path $destPublic $installerName

# Funktion zum Erstellen der Prüfsumme
function Create-Checksum($installerPath) {
    if (Test-Path $installerPath) {
        $checksumPath = "$($installerPath).sha256"
        $hash = (Get-FileHash -Path $installerPath -Algorithm SHA256).Hash
        Set-Content -Path $checksumPath -Value $hash
        Write-Host "[OK] Prüfsumme erstellt für: $($installerPath)" -ForegroundColor Green
    }
}

if ($targetChoice -eq '1' -or $targetChoice -eq '3') {
    Create-Checksum -installerPath $localInstallerPath
}
if ($targetChoice -eq '2' -or $targetChoice -eq '3') {
    Create-Checksum -installerPath $publicInstallerPath
}

Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host "=== Release-Prozess erfolgreich abgeschlossen! ===" -ForegroundColor Magenta
Write-Host "==========================================================" -ForegroundColor Magenta