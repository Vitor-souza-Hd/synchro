param([string]$Username = 'jinka070')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME com um JDK 17 ou superior.' }
    # Use an installed Maven, or the distribution already downloaded by the wrapper.
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($maven) {
        $mavenPath = $maven.Source
    } else {
        $cachedMaven = Get-ChildItem -LiteralPath "$env:USERPROFILE/.m2/wrapper/dists" -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
            Sort-Object FullName | Select-Object -Last 1
        $mavenPath = if ($cachedMaven) { $cachedMaven.FullName } else { Join-Path $PSScriptRoot 'mvnw.cmd' }
    }
    & $mavenPath -q "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/lastfm-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o importador.' }
    $classpath = (Join-Path $PSScriptRoot 'target/classes') + ';' + (Get-Content target/lastfm-classpath.txt -Raw).Trim()
    & "$env:JAVA_HOME/bin/java.exe" -cp $classpath org.example.synchro.importer.LastFmImportApplication `
        --spring.profiles.active=lastfm-import "--lastfm.import.username=$Username"
    if ($LASTEXITCODE -ne 0) { throw 'Importação interrompida. Execute novamente para retomar da última página salva.' }
} finally {
    Pop-Location
}
