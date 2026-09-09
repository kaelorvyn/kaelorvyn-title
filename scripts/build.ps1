$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$TempRoot = 'C:\Users\Administrator\Documents\Codex\kt-build-tmp'
$WorkspaceRoot = 'C:\Users\Administrator\Documents\Codex'

if (-not $TempRoot.StartsWith($WorkspaceRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "临时目录不在工作区内：$TempRoot"
}
if (Test-Path -LiteralPath $TempRoot) {
    [System.IO.Directory]::Delete($TempRoot, $true)
}
New-Item -ItemType Directory -Force -Path $TempRoot | Out-Null

Copy-Item -LiteralPath (Join-Path $ProjectRoot 'settings.gradle') -Destination $TempRoot -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'build.gradle') -Destination $TempRoot -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'gradle.properties') -Destination $TempRoot -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'paper') -Destination $TempRoot -Recurse -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'velocity') -Destination $TempRoot -Recurse -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'viaproxy') -Destination $TempRoot -Recurse -Force

$wrapperSource = 'C:\Users\Administrator\Documents\Codex\kg-build-tmp2'
if (-not (Test-Path -LiteralPath (Join-Path $wrapperSource 'gradlew.bat'))) {
    $wrapperSource = 'C:\Users\Administrator\Documents\Codex\project-0009-KaelorvynGuard-反作弊插件'
}
Copy-Item -LiteralPath (Join-Path $wrapperSource 'gradlew.bat') -Destination $TempRoot -Force
Copy-Item -LiteralPath (Join-Path $wrapperSource 'gradlew') -Destination $TempRoot -Force
Copy-Item -LiteralPath (Join-Path $wrapperSource 'gradle') -Destination $TempRoot -Recurse -Force

$env:JAVA_HOME = 'D:\Java\jdk-25'
Push-Location $TempRoot
try {
    & .\gradlew.bat :paper:build :velocity:build :viaproxy:build --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw 'Gradle 构建失败'
    }
} finally {
    Pop-Location
}

$outputDir = Join-Path $ProjectRoot 'outputs'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
Copy-Item -LiteralPath (Join-Path $TempRoot 'paper\build\libs\KaelorvynTitle-Paper-1.0.0.jar') -Destination $outputDir -Force
Copy-Item -LiteralPath (Join-Path $TempRoot 'velocity\build\libs\KaelorvynTitle-Velocity-1.0.0.jar') -Destination $outputDir -Force
Copy-Item -LiteralPath (Join-Path $TempRoot 'viaproxy\build\libs\KaelorvynTitle-ViaProxy-1.0.0.jar') -Destination $outputDir -Force

Write-Output "构建完成："
Write-Output (Join-Path $outputDir 'KaelorvynTitle-Paper-1.0.0.jar')
Write-Output (Join-Path $outputDir 'KaelorvynTitle-Velocity-1.0.0.jar')
Write-Output (Join-Path $outputDir 'KaelorvynTitle-ViaProxy-1.0.0.jar')
