# Compila o Clopes Tuner.
#
#   .\build.ps1
#   .\build.ps1 -Toolchain "C:\caminho\para\toolchain"
#
# Usa JAVA_HOME/ANDROID_HOME quando existem; caso contrario procura um toolchain
# local (jdk21 + android-sdk + gradle-8.9). Gera o APK de debug e copia para
# ClopesTuner.apk na raiz do projeto.
param(
    [string]$Toolchain = $env:CLOPES_TOOLCHAIN
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Find-Toolchain {
    $candidates = @()
    if ($Toolchain) { $candidates += $Toolchain }
    $candidates += (Join-Path $root "..\ClopesCast\CastDoClopes\toolchain")
    $candidates += (Join-Path $root "toolchain")
    if ($env:CLOPES_TOOLCHAIN) { $candidates += $env:CLOPES_TOOLCHAIN }

    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        if (Test-Path (Join-Path $candidate "gradle-8.9\bin\gradle.bat")) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

$found = Find-Toolchain
if (-not $found) {
    throw "toolchain nao encontrado. Use -Toolchain <pasta> ou defina CLOPES_TOOLCHAIN."
}

$jdk = if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
    $env:JAVA_HOME
} else {
    Join-Path $found "jdk21"
}
$sdk = if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $found "android-sdk"
}
$gradle = Join-Path $found "gradle-8.9\bin\gradle.bat"

foreach ($path in @($jdk, $sdk, $gradle)) {
    if (-not (Test-Path $path)) { throw "toolchain ausente: $path" }
}

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$jdk\bin;$env:PATH"

# local.properties com caminho do SDK (barra escapada para o .properties)
$sdkEscaped = $sdk -replace '\\', '\\\\'
"sdk.dir=$sdkEscaped" | Set-Content (Join-Path $root "local.properties") -Encoding ASCII

Write-Output "1/2 testes unitarios"
& $gradle -p $root testDebugUnitTest --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "testes falharam" }

Write-Output "2/2 montando o APK"
& $gradle -p $root assembleDebug --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "build falhou" }

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$final = Join-Path $root "ClopesTuner.apk"
Copy-Item $apk $final -Force

$size = [math]::Round((Get-Item $final).Length / 1MB, 2)
Write-Output ""
Write-Output "pronto: $final ($size MB)"
