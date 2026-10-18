param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 21 installation before running this script.' }
    $mavenArgs = @('-f', 'pom.xml', 'clean', 'verify')
    if ($SkipTests) { $mavenArgs += '-DskipTests' }
    & ./tradepulse/mvnw.cmd @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    npm.cmd ci --prefix tradepulse-angular
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    npm.cmd run build --prefix tradepulse-angular
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    if (-not $SkipTests) {
        npm.cmd test --prefix tradepulse-angular
        if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    }
} finally { Pop-Location }
