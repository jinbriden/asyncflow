param(
    [ValidateSet('all', 'smoke', 'regression', 'reliability')]
    [string]$Suite = 'all'
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
Push-Location $projectDir
try {
    if ($Suite -eq 'all') {
        & mvn verify
    } else {
        & mvn test "-P$Suite"
    }
    if ($LASTEXITCODE -ne 0) { throw "Maven verification failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
