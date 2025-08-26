# --- Configuration des chemins ---
$projectPath = "C:\Users\stephane.dongmo\IdeaProjects\CarlSchroeter.vias"
$javafxBin   = "C:\JavaLib\javafx-sdk-24.0.1\bin"
$libsPath    = "$projectPath\libs"
$targetPath  = "$projectPath\target\classes"

$env:PATH = "$javafxBin;$env:PATH"

$modulePath = "`"$libsPath`""
$addModules = "javafx.controls,javafx.fxml"

Write-Host "🚀  GUI erfolgreich gestartet..."
java `
    --module-path $modulePath `
    --add-modules $addModules `
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED `
    --add-opens java.desktop/sun.awt=ALL-UNNAMED `
    -classpath "$targetPath;$libsPath/*" `
    gui.VIASGuiApplication
