param(
    [string]$Version = '1.0.0',
    [string]$OutputDirectory = '',
    [switch]$Installer,
    [switch]$SkipTests,
    [switch]$SkipPythonBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$targetRoot = Join-Path $projectRoot 'target'
$distributionRoot = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    Join-Path $targetRoot 'distribution'
} elseif ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
}
$stagingRoot = Join-Path $targetRoot 'desktop-staging'
$runtimeRoot = Join-Path $targetRoot 'stock-lens-runtime'
$python = Join-Path $projectRoot '.venv-akshare\Scripts\python.exe'
$jpackageCommand = Get-Command jpackage -ErrorAction SilentlyContinue
$jlinkCommand = Get-Command jlink -ErrorAction SilentlyContinue
$portableReadme = Join-Path $projectRoot 'PACKAGED-README.txt'
$portableStart = Join-Path $projectRoot 'packaging\Start Stock Lens.cmd'
$portableDiagnose = Join-Path $projectRoot 'packaging\Diagnose Startup.cmd'

function Remove-BuildDirectory([string]$Path) {
    $resolvedTarget = [System.IO.Path]::GetFullPath($targetRoot)
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $resolvedPath.StartsWith($resolvedTarget + [System.IO.Path]::DirectorySeparatorChar)) {
        throw "Refusing to remove a path outside target: $resolvedPath"
    }
    if (Test-Path -LiteralPath $resolvedPath) {
        Remove-Item -LiteralPath $resolvedPath -Recurse -Force
    }
}

if (-not $jpackageCommand) {
    throw 'jpackage was not found. Install JDK 17+ and make sure its bin directory is on PATH.'
}
if (-not $jlinkCommand) {
    throw 'jlink was not found. Install JDK 17+ and make sure its bin directory is on PATH.'
}
if (-not (Test-Path -LiteralPath $python)) {
    throw 'AKShare virtual environment was not found. Run scripts/setup-akshare.ps1 first.'
}
$resolvedTargetRoot = [System.IO.Path]::GetFullPath($targetRoot)
if (-not $distributionRoot.StartsWith($resolvedTargetRoot + [System.IO.Path]::DirectorySeparatorChar)) {
    throw "OutputDirectory must be inside target: $distributionRoot"
}

Push-Location $projectRoot
try {
    if ($SkipTests) {
        & mvn package -DskipTests
    } else {
        & mvn package
    }
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

    $sidecarDirectory = Join-Path $targetRoot 'python-dist\akshare-sidecar'
    $sidecarExe = Join-Path $sidecarDirectory 'akshare-sidecar.exe'
    if (-not $SkipPythonBuild) {
        & $python -m pip show pyinstaller *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'PyInstaller is missing. Run: .\.venv-akshare\Scripts\python.exe -m pip install pyinstaller'
        }
        Remove-BuildDirectory (Join-Path $targetRoot 'python-build')
        Remove-BuildDirectory (Join-Path $targetRoot 'python-dist')
        & $python -m PyInstaller `
            --noconfirm `
            --clean `
            --onedir `
            --noconsole `
            --name akshare-sidecar `
            --distpath (Join-Path $targetRoot 'python-dist') `
            --workpath (Join-Path $targetRoot 'python-build') `
            --specpath (Join-Path $targetRoot 'python-build') `
            --collect-submodules akshare `
            --collect-data akshare `
            (Join-Path $projectRoot 'scripts\akshare_service.py')
        if ($LASTEXITCODE -ne 0) { throw 'AKShare sidecar build failed.' }
    }
    if (-not (Test-Path -LiteralPath $sidecarExe)) {
        throw "AKShare sidecar executable was not found: $sidecarExe"
    }

    Remove-BuildDirectory $stagingRoot
    Remove-BuildDirectory $distributionRoot
    New-Item -ItemType Directory -Path $stagingRoot,$distributionRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $targetRoot 'stock-analyzer-1.0-SNAPSHOT.jar') `
        -Destination (Join-Path $stagingRoot 'stock-analyzer.jar')
    Copy-Item -LiteralPath $sidecarDirectory -Destination (Join-Path $stagingRoot 'akshare-sidecar') -Recurse

    Remove-BuildDirectory $runtimeRoot
    $runtimeModules = @(
        'java.base', 'java.desktop', 'java.instrument', 'java.logging', 'java.management',
        'java.naming', 'java.net.http', 'java.prefs', 'java.rmi', 'java.security.jgss',
        'java.security.sasl', 'java.sql', 'java.transaction.xa', 'java.xml', 'java.xml.crypto',
        'jdk.charsets', 'jdk.crypto.ec', 'jdk.crypto.cryptoki', 'jdk.crypto.mscapi',
        'jdk.localedata', 'jdk.unsupported'
    ) -join ','
    & $jlinkCommand.Source `
        --add-modules $runtimeModules `
        --output $runtimeRoot `
        --strip-debug `
        --no-header-files `
        --no-man-pages `
        --compress=2
    if ($LASTEXITCODE -ne 0) { throw 'jlink runtime build failed.' }

    $jpackageArgs = @(
        '--type', 'app-image',
        '--name', 'Stock Lens',
        '--app-version', $Version,
        '--vendor', 'Stock Lens Community',
        '--description', 'A-share market analysis and sector leader tracking',
        '--input', $stagingRoot,
        '--dest', $distributionRoot,
        '--runtime-image', $runtimeRoot,
        '--main-jar', 'stock-analyzer.jar',
        '--java-options', '-Dfile.encoding=UTF-8',
        '--java-options', '-Dstock.app.desktop=true',
        '--arguments', '--spring.profiles.active=desktop'
    )
    & $jpackageCommand.Source @jpackageArgs
    if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image build failed.' }

    $appImage = Join-Path $distributionRoot 'Stock Lens'
    if (Test-Path -LiteralPath $portableReadme) {
        # Keep the packaged filename ASCII-safe for Windows PowerShell 5.1.
        Copy-Item -LiteralPath $portableReadme -Destination (Join-Path $appImage 'README-zh-CN.txt')
    }
    Copy-Item -LiteralPath $portableStart -Destination (Join-Path $appImage 'Start Stock Lens.cmd')
    Copy-Item -LiteralPath $portableDiagnose -Destination (Join-Path $appImage 'Diagnose Startup.cmd')
    $zipPath = Join-Path $distributionRoot "Stock-Lens-$Version-Windows-x64.zip"
    Compress-Archive -Path $appImage -DestinationPath $zipPath -CompressionLevel Optimal

    $tarPath = Join-Path $distributionRoot "Stock-Lens-$Version-Windows-x64.tar.gz"
    & tar -czf $tarPath -C $distributionRoot 'Stock Lens'
    if ($LASTEXITCODE -ne 0) { throw 'tar.gz build failed.' }

    if ($Installer) {
        $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
        $light = Get-Command light.exe -ErrorAction SilentlyContinue
        if (-not $candle -or -not $light) {
            throw 'WiX Toolset 3.x is required for the EXE installer. Install WiX and rerun with -Installer.'
        }
        $upgradeUuid = '90e49746-85d6-4d2f-8661-f302192102a3'
        $installerArgs = @(
            '--type', 'exe',
            '--name', 'Stock Lens',
            '--app-version', $Version,
            '--vendor', 'Stock Lens Community',
            '--description', 'A-share market analysis and sector leader tracking',
            '--app-image', $appImage,
            '--dest', $distributionRoot,
            '--win-per-user-install',
            '--win-dir-chooser',
            '--win-menu',
            '--win-menu-group', 'Stock Lens',
            '--win-shortcut',
            '--win-upgrade-uuid', $upgradeUuid
        )
        & $jpackageCommand.Source @installerArgs
        if ($LASTEXITCODE -ne 0) { throw 'jpackage installer build failed.' }
    }

    Write-Host ''
    Write-Host 'Distribution build complete:' -ForegroundColor Green
    Get-ChildItem -LiteralPath $distributionRoot | Select-Object Name,Length,LastWriteTime
} finally {
    Pop-Location
}
