param(
    [Parameter(Position = 0)]
    [string]$Task = "buildMod",
    [string]$ModsDir,
    [string]$GradleVersion = "8.10.2",
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $env:GRADLE_USER_HOME = Join-Path $repoRoot '.gradle-local'
    New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null

    $gradleRunner = '.\\gradlew.bat'

    if (-not (Test-Path $gradleRunner)) {
        $toolsDir = Join-Path $repoRoot '.tools'
        $zipPath = Join-Path $toolsDir "gradle-$GradleVersion-bin.zip"
        $extractDir = Join-Path $toolsDir "gradle-$GradleVersion"
        $bootstrapGradle = Join-Path $extractDir 'bin\\gradle.bat'

        New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

        if (-not (Test-Path $bootstrapGradle)) {
            Write-Host "Downloading Gradle $GradleVersion to $toolsDir"
            Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $zipPath
            Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
        }

        & $bootstrapGradle wrapper --gradle-version $GradleVersion
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }

    $gradleArgs = @($Task)
    if ($ModsDir) {
        $gradleArgs += "-PmodsDir=$ModsDir"
    }
    if ($ExtraArgs) {
        $gradleArgs += $ExtraArgs
    }

    & $gradleRunner @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
