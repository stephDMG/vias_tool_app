# =============================================================================
# === VIAS EXPORT TOOL - PRO RELEASE SCRIPT (UNC + ACL + Per-User) ============
# =============================================================================
# Clean ASCII-only to avoid encoding issues on Windows PowerShell
# Requires: JDK with jpackage, Maven, PowerShell 5+
# =============================================================================

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# --- 1. CONFIG ---------------------------------------------------------------
$projectDir = $PSScriptRoot
$pomFile = Join-Path $projectDir 'pom.xml'
$changelogFile = Join-Path $projectDir 'changelog.txt'

$jpackageInputDir = 'C:\jpackage_input'
$jpackageLibsDir = Join-Path $jpackageInputDir 'libs'
$logoFileSource = Join-Path $projectDir 'src\main\resources\images\logo.ico'
$logoFileDest = Join-Path $jpackageLibsDir 'logo.ico'

$destLocal = 'C:\jpackage_output'

# UNC path (note: $ in share name -> keep single quotes)
$destPublic = '\\Debresrv10\csdatenwelt$\EDV\Client_WIN_11\Software\VIAS_TOOL_PUBLIC\jpackage_output'
$versionFilePublic = Join-Path $destPublic 'version.txt'
$changelogFilePublic = Join-Path $destPublic 'changelog.txt'
$archiveDirPublic = Join-Path $destPublic 'archive'

# --- 1a. HELPER FUNCTIONS (ACL via SID) --------------------------------------
# Use language-neutral SID for Authenticated Users: *S-1-5-11
$principals = @('*S-1-5-11')

function Grant-Rights
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$RightsSpec  # '(RX)' or '(OI)(CI)(RX)'
    )
    if (-not (Test-Path $Path))
    {
        return
    }
    foreach ($id in $principals)
    {
        # icacls expects a single token: *S-1-5-11:(RX)  or  *S-1-5-11:(OI)(CI)(RX)
        $grantSpec = ('{0}:{1}' -f $id, $RightsSpec)
        & icacls $Path /grant $grantSpec | Out-Null
    }
}

function Grant-Read
{
    param([string]$Path)   Grant-Rights -Path $Path   -RightsSpec '(RX)'
}
function Grant-ReadRecur
{
    param([string]$Folder)
    if (Test-Path $Folder)
    {
        & icacls $Folder /inheritance:e | Out-Null
        Grant-Rights -Path $Folder -RightsSpec '(OI)(CI)(RX)'
    }
}

# --- 1b. Ensure UNC availability and folders --------------------------------
Write-Host 'Checking UNC reachability...' -ForegroundColor Cyan
if (-not (Test-Path (Split-Path $destPublic -Parent)))
{
    Write-Host '[ERROR] UNC base path not reachable:' -ForegroundColor Red
    Write-Host "  $destPublic" -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $destPublic))
{
    New-Item -ItemType Directory -Force -Path $destPublic | Out-Null
}
if (-not (Test-Path $destLocal))
{
    New-Item -ItemType Directory -Force -Path $destLocal  | Out-Null
}

# Ensure folder ACL (inheritance + RX for Authenticated Users)
Grant-ReadRecur $destPublic

# --- 2. VERSIONING -----------------------------------------------------------
Write-Host '==========================================================' -ForegroundColor Magenta
Write-Host '=== Starting release process for VIAS Export Tool      ===' -ForegroundColor Magenta
Write-Host '==========================================================' -ForegroundColor Magenta

try
{
    [xml]$pom = Get-Content $pomFile -Encoding UTF8
    $currentVersion = $pom.project.version
    $versionParts = $currentVersion.Split('.')

    $major = [int]$versionParts[0]
    $minor = [int]$versionParts[1]
    $patch = [int]$versionParts[2]

    if ($patch -eq 29)
    {
        $newMajor = $major
        $newMinor = $minor + 1
        $newPatch = 0
        $suggestedVersion = "$newMajor.$newMinor.$newPatch"
        Write-Host 'Patch cycle reached 30 -> increasing minor version.' -ForegroundColor Yellow
    }
    elseif ($patch -lt 29)
    {
        $newMajor = $major
        $newMinor = $minor
        $newPatch = $patch + 1
        $suggestedVersion = "$newMajor.$newMinor.$newPatch"
    }
    else
    {
        Write-Host "Unexpected patch version ($patch). Using fallback." -ForegroundColor Yellow
        $suggestedVersion = "$major.$minor.0"
    }
}
catch
{
    Write-Host '[ERROR] Failed to read pom.xml. Aborting.' -ForegroundColor Red
    exit 1
}

Write-Host "Current version: $currentVersion" -ForegroundColor Cyan
Write-Host "Suggested version: $suggestedVersion" -ForegroundColor Green

$newVersion = Read-Host -Prompt 'Confirm new version or enter your own (Enter = suggested)'
if ( [string]::IsNullOrWhiteSpace($newVersion))
{
    $newVersion = $suggestedVersion
    Write-Host "Using version $newVersion" -ForegroundColor Green
}
if ($newVersion -notmatch '^\d+\.\d+\.\d+$')
{
    Write-Host '[ERROR] Invalid version format. Expected: X.Y.Z' -ForegroundColor Red
    exit 1
}

Write-Host "Describe changes (finish with 'ENDE' on a new line):" -ForegroundColor Yellow
$commitLines = @()
while (($line = Read-Host) -ne 'ENDE')
{
    # ASCII only to avoid encoding issues
    $commitLines += "- [Aenderung] $line"
}

Write-Host 'Publish to where? [1] Local, [2] Public, [3] Both' -ForegroundColor Yellow
$targetChoice = Read-Host

# --- 3. UPDATE FILES (local) -------------------------------------------------
Write-Host '----------------------------------------------------------'
Write-Host '--> Updating project files...' -ForegroundColor Cyan

# Replace version in pom.xml (raw text replace)
$pomText = Get-Content $pomFile -Raw -Encoding UTF8
$pomText = $pomText.Replace("<version>$currentVersion</version>", "<version>$newVersion</version>")
Set-Content -Path $pomFile -Value $pomText -Encoding UTF8

# Prepend changelog
$date = Get-Date -Format 'yyyy-MM-dd'
$newHeader = "====================`r`nVersion $newVersion ($date)`r`n===================="
$existingChangelog = Get-Content $changelogFile -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
$newChangelog = $newHeader + "`r`n" + ($commitLines -join "`r`n") + "`r`n`r`n" + $existingChangelog
Set-Content -Path $changelogFile -Value $newChangelog -Encoding UTF8

Write-Host "[OK] Local files updated to version $newVersion." -ForegroundColor Green

# --- 4. BUILD APP ------------------------------------------------------------
Write-Host '----------------------------------------------------------'
Write-Host '--> Building with Maven...' -ForegroundColor Cyan
Push-Location $projectDir
mvn clean package
if ($LASTEXITCODE -ne 0)
{
    Write-Host '[ERROR] Maven build failed.' -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location
Write-Host '[OK] Maven build completed.' -ForegroundColor Green

# --- 5. PREPARE JPACKAGE -----------------------------------------------------
Write-Host '----------------------------------------------------------'
Write-Host '--> Preparing installer creation...' -ForegroundColor Cyan

if (-not (Test-Path $jpackageInputDir))
{
    New-Item -ItemType Directory -Force -Path $jpackageInputDir | Out-Null
}
if (-not (Test-Path $jpackageLibsDir))
{
    New-Item -ItemType Directory -Force -Path $jpackageLibsDir  | Out-Null
}

Copy-Item -Path (Join-Path $projectDir 'target\vias-export-tool.jar') -Destination $jpackageInputDir -Force
Copy-Item -Path $logoFileSource -Destination $logoFileDest -Force

# jpackage args
$jpackageArgs = @(
    '--name', 'VIAS Export Tool',
    '--app-version', $newVersion,
    '--win-upgrade-uuid', '9e1e88cd-af79-40fd-928d-7e12c76793d7',
    '--input', $jpackageInputDir,
    '--main-jar', 'vias-export-tool.jar',
    '--main-class', 'gui.MainLauncher',
    '--type', 'msi',
    '--icon', $logoFileDest,
    '--win-shortcut',
    '--win-menu',
    '--win-per-user-install'
)

if ($targetChoice -eq '1' -or $targetChoice -eq '3')
{
    Write-Host 'Creating local installer...' -ForegroundColor Cyan
    jpackage @jpackageArgs --dest $destLocal
    Write-Host '[OK] Local installer created.' -ForegroundColor Green
}

if ($targetChoice -eq '2' -or $targetChoice -eq '3')
{
    Write-Host 'Creating public installer...' -ForegroundColor Cyan

    # Archive previous installer (major.minor)
    if (Test-Path $destPublic)
    {
        $oldInstaller = Get-ChildItem -Path $destPublic -Filter '*.msi' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($oldInstaller)
        {
            if ($oldInstaller.Name -match 'VIAS Export Tool-(\d+)\.(\d+)\.(\d+)\.msi')
            {
                $oldMajor = $matches[1]; $oldMinor = $matches[2]
                $mm = "$oldMajor.$oldMinor"
                $specificArchiveDir = Join-Path $archiveDirPublic $mm
                if (-not (Test-Path $specificArchiveDir))
                {
                    New-Item -ItemType Directory -Force -Path $specificArchiveDir | Out-Null
                    Grant-ReadRecur $specificArchiveDir
                }
                Move-Item -Path $oldInstaller.FullName -Destination (Join-Path $specificArchiveDir $oldInstaller.Name)
                $oldChecksum = "$( $oldInstaller.FullName ).sha256"
                if (Test-Path $oldChecksum)
                {
                    Move-Item -Path $oldChecksum -Destination (Join-Path $specificArchiveDir ([IO.Path]::GetFileName($oldChecksum)))
                }
            }
            else
            {
                if (-not (Test-Path $archiveDirPublic))
                {
                    New-Item -ItemType Directory -Force -Path $archiveDirPublic | Out-Null
                }
                Move-Item -Path $oldInstaller.FullName -Destination $archiveDirPublic
            }
        }
    }

    # Create new public installer
    jpackage @jpackageArgs --dest $destPublic
    Write-Host '[OK] Public installer created.' -ForegroundColor Green

    # Make sure MSI is readable immediately (before checksum step)
    $publicInstallerPath = Join-Path $destPublic ("VIAS Export Tool-$newVersion.msi")
    if (Test-Path $publicInstallerPath)
    {
        Grant-Read $publicInstallerPath
    }

    # Update version and changelog on server (UTF-8)
    Write-Host '--> Updating release info...' -ForegroundColor Cyan
    Set-Content -Path $versionFilePublic   -Value $newVersion   -Encoding UTF8
    Set-Content -Path $changelogFilePublic -Value $newChangelog -Encoding UTF8

    # Ensure ACL on info files
    Grant-Read $versionFilePublic
    Grant-Read $changelogFilePublic
}

# --- 6. CHECKSUMS + ACL ------------------------------------------------------
Write-Host '----------------------------------------------------------'
Write-Host '--> Creating SHA-256 checksums...' -ForegroundColor Cyan

$installerName = "VIAS Export Tool-$newVersion.msi"
$localInstallerPath = Join-Path $destLocal  $installerName
$publicInstallerPath = Join-Path $destPublic $installerName

function Create-Checksum
{
    param([string]$InstallerPath)
    if (Test-Path $InstallerPath)
    {
        $checksumPath = "$InstallerPath.sha256"
        $hash = (Get-FileHash -Path $InstallerPath -Algorithm SHA256).Hash
        Set-Content -Path $checksumPath -Value $hash -Encoding ASCII
        Write-Host "[OK] Checksum created for: $InstallerPath" -ForegroundColor Green

        # If public UNC, ensure ACL on checksum and MSI
        if ( $InstallerPath.StartsWith('\\'))
        {
            Grant-Read $checksumPath
            Grant-Read $InstallerPath
        }
    }
}

if ($targetChoice -eq '1' -or $targetChoice -eq '3')
{
    Create-Checksum -InstallerPath $localInstallerPath
}
if ($targetChoice -eq '2' -or $targetChoice -eq '3')
{
    Create-Checksum -InstallerPath $publicInstallerPath
}

Write-Host '==========================================================' -ForegroundColor Magenta
Write-Host '=== Release process completed successfully! ==============' -ForegroundColor Magenta
Write-Host '==========================================================' -ForegroundColor Magenta
