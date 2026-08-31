param(
    [string]$PythonExecutable = $env:AKSHARE_PYTHON
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$venvDirectory = Join-Path $projectRoot '.venv-akshare'

if ([string]::IsNullOrWhiteSpace($PythonExecutable)) {
    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCommand) {
        $pythonCommand = Get-Command py -ErrorAction SilentlyContinue
    }
    if (-not $pythonCommand) {
        throw 'Python 3 was not found. Install Python 3.10+ or set AKSHARE_PYTHON.'
    }
    $PythonExecutable = $pythonCommand.Source
}

$pythonName = [System.IO.Path]::GetFileNameWithoutExtension($PythonExecutable)
if ($pythonName -eq 'py') {
    & $PythonExecutable -3 -m venv $venvDirectory
} else {
    & $PythonExecutable -m venv $venvDirectory
}

$venvPython = Join-Path $venvDirectory 'Scripts\python.exe'
if (-not (Test-Path $venvPython)) {
    $venvPython = Join-Path $venvDirectory 'bin\python'
}

& $venvPython -m pip install --upgrade pip
& $venvPython -m pip install -r (Join-Path $projectRoot 'requirements-akshare.txt')
Write-Host "AKShare environment is ready: $venvPython"
