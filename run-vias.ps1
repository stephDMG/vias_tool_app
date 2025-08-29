# --- Configuration des chemins ---
$projectPath = "C:\Users\stephane.dongmo\IdeaProjects\CarlSchroeter.vias"
$javafxBin   = "C:\JavaLib\javafx-sdk-24.0.1\bin"
$libsPath    = "$projectPath\libs"
$targetPath  = "$projectPath\target\classes"

# Füge JavaFX zum PATH hinzu
$env:PATH = "$javafxBin;$env:PATH"

$modulePath = "`"$libsPath`""
$addModules = "javafx.controls,javafx.fxml"

Write-Host "🚀 VIAS GUI wird gestartet..." -ForegroundColor Green

Prüfe ob erforderliche Pfade existieren
if (-not (Test-Path $targetPath)) {
    Write-Host "❌ FEHLER: Target-Pfad nicht gefunden: $targetPath" -ForegroundColor Red
    Write-Host "Bitte fuehren Sie zuerst 'mvn compile' aus." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $libsPath)) {
    Write-Host "❌ FEHLER: Libs-Pfad nicht gefunden: $libsPath" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Starte VIAS Export Tool..." -ForegroundColor Cyan



java `
    --module-path $modulePath `
    --add-modules $addModules `
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED `
    --add-opens java.desktop/sun.awt=ALL-UNNAMED `
    -classpath "$targetPath;$libsPath/*" `
    gui.MainLauncher

# Prüfe Exit-Code
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Anwendung wurde mit Fehler beendet (Code: $LASTEXITCODE)" -ForegroundColor Red
} else {
    Write-Host "✅ Anwendung erfolgreich beendet." -ForegroundColor Green
}