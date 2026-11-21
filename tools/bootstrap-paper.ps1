param([switch]$Offline)

# Prepare Paper development dependencies in a workspace-local Gradle cache.
$ErrorActionPreference = 'Stop'
$bootstrapWorkspace = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$bootstrapProject = Join-Path $bootstrapWorkspace 'tools/paper-bootstrap'
if (!(Test-Path -LiteralPath (Join-Path $bootstrapProject 'build.gradle'))) {
    throw 'Ouvrir PowerShell a la racine du projet Layout Debugger avant de lancer ce script.'
}

$bootstrapCache = Join-Path $bootstrapWorkspace '.gradle/paper-home'
$bootstrapLog = Join-Path $bootstrapWorkspace '.gradle/paper-bootstrap.log'
$bootstrapOldGradleHome = $env:GRADLE_USER_HOME
$bootstrapOldJavaHome = $env:JAVA_HOME
try {
    $env:GRADLE_USER_HOME = $bootstrapCache
    $bootstrapDistribution = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle/wrapper/dists/gradle-9.8.2-bin'
    $bootstrapGradle = $null
    if (Test-Path -LiteralPath $bootstrapDistribution) {
        $bootstrapGradle = Get-ChildItem -LiteralPath $bootstrapDistribution -Filter gradle.bat -Recurse -File |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (!$bootstrapGradle) { $bootstrapGradle = Join-Path $bootstrapWorkspace 'gradlew.bat' }
    $bootstrapArguments = @('-p', $bootstrapProject, '--no-daemon', '--no-configuration-cache', '--no-problems-report')
    if ($Offline) { $bootstrapArguments += '--offline' }
    $bootstrapArguments += 'paperweightUserdevSetup'
    Write-Output 'Preparation des dependances Paper 26.2 (build 135), cible Java 25...'
    $bootstrapOldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $bootstrapGradle @bootstrapArguments 2>&1 | Tee-Object -FilePath $bootstrapLog
        $bootstrapExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $bootstrapOldErrorAction }
    if ($bootstrapExit -ne 0) { throw "Preparation Paper echouee (code $bootstrapExit). Journal : $bootstrapLog" }
    Write-Output "Dependances Paper preparees. Cache : $bootstrapCache"
    Write-Output 'Ce script prepare uniquement les dependances. Construire le plugin et son agent avec ./gradlew.bat -p paper build.'
} finally {
    $env:GRADLE_USER_HOME = $bootstrapOldGradleHome
    $env:JAVA_HOME = $bootstrapOldJavaHome
}
