# Publica uma nova versao do Clopes Tuner em um comando.
#
#   .\publish.ps1 -Version 0.1.1 -Message "feat: novo icone"
#
# O que ele faz, em ordem:
#   1. regera os icones do launcher a partir de icons\icone-app.png
#   2. atualiza versionName/versionCode no app\build.gradle.kts
#   3. roda os testes e monta o APK (build.ps1)
#   4. commit + push no GitHub
#   5. cria a release com o APK anexado
#
# Opcoes:
#   -Version 0.1.1     versao nova (sem isso, mantem a atual)
#   -Message "texto"    mensagem do commit
#   -Icon caminho       PNG quadrado do icone (padrao icons\icone-app.png)
#   -SkipBuild          nao compila (util para testar o script)
#   -NoRelease          so faz commit/push, sem release
#   -NoPush             faz tudo, mas nao envia para o GitHub
param(
    [string]$Version = "",
    [string]$Message = "",
    [string]$Icon = "icons\icone-app.png",
    [switch]$SkipBuild,
    [switch]$NoRelease,
    [switch]$NoPush
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Resolve-Tool($name, $fallback) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if (Test-Path $fallback) { return $fallback }
    throw "$name nao encontrado. Instale ou adicione ao PATH."
}

$git = Resolve-Tool "git" "C:\Program Files\Git\cmd\git.exe"
$gh = Resolve-Tool "gh" "C:\Program Files\GitHub CLI\gh.exe"
$gradleFile = Join-Path $root "app\build.gradle.kts"

# ---------------------------------------------------------------- 1. icones
$iconPath = Join-Path $root $Icon
if (Test-Path $iconPath) {
    Add-Type -AssemblyName System.Drawing
    $source = [System.Drawing.Image]::FromFile($iconPath)
    $sizes = @{
        "mipmap-mdpi"    = 48
        "mipmap-hdpi"    = 72
        "mipmap-xhdpi"   = 96
        "mipmap-xxhdpi"  = 144
        "mipmap-xxxhdpi" = 192
    }
    foreach ($folder in $sizes.Keys) {
        $dir = Join-Path $root "app\src\main\res\$folder"
        [void][System.IO.Directory]::CreateDirectory($dir)
        $size = $sizes[$folder]
        $bmp = New-Object System.Drawing.Bitmap $size, $size
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($source, 0, 0, $size, $size)
        $g.Dispose()
        $bmp.Save((Join-Path $dir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
    }
    $source.Dispose()
    Write-Output "1/5 icones regerados a partir de $Icon"
} else {
    Write-Output "1/5 icone nao encontrado em $Icon (mantendo os atuais)"
}

# --------------------------------------------------------------- 2. versao
$content = Get-Content $gradleFile -Raw
if ($Version) {
    $currentCode = [int]([regex]::Match($content, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
    $newCode = $currentCode + 1
    $content = [regex]::Replace($content, 'versionCode\s*=\s*\d+', "versionCode = $newCode")
    $content = [regex]::Replace($content, 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`"")
    Set-Content $gradleFile -Value $content -Encoding UTF8
    Write-Output "2/5 versao $Version (versionCode $newCode)"
} else {
    $currentVersion = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    Write-Output "2/5 versao mantida em $currentVersion"
}
$currentVersion = [regex]::Match((Get-Content $gradleFile -Raw), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value

# ---------------------------------------------------------------- 3. build
if ($SkipBuild) {
    Write-Output "3/5 build pulado (-SkipBuild)"
} else {
    Write-Output "3/5 compilando"
    & (Join-Path $root "build.ps1")
    if ($LASTEXITCODE -ne 0) { throw "build falhou" }
}

# ------------------------------------------------------------ 4. git push
& $git -C $root add -A
$staged = & $git -C $root diff --cached --name-only
if (-not $staged) {
    Write-Output "4/5 nada para commitar"
} else {
    if (-not $Message) {
        $Message = if ($Version) { "chore: release $Version" } else { "chore: atualizacao" }
    }
    & $git -C $root commit -q -m $Message
    if ($NoPush) {
        Write-Output "4/5 commit local feito (-NoPush)"
    } else {
        & $git -C $root push
        Write-Output "4/5 enviado para o GitHub"
    }
}

# -------------------------------------------------------------- 5. release
if ($NoRelease -or $NoPush) {
    Write-Output "5/5 release pulada"
    return
}

$tag = "v$currentVersion"
$apk = Join-Path $root "ClopesTuner.apk"
$existing = & $gh release view $tag --repo ClopesX/clopes-tuner --json tagName 2>$null
if ($LASTEXITCODE -eq 0 -and $existing) {
    & $gh release upload $tag $apk --repo ClopesX/clopes-tuner --clobber
    Write-Output "5/5 APK reenviado para a release $tag"
} else {
    & $gh release create $tag $apk --repo ClopesX/clopes-tuner --title "Clopes Tuner $currentVersion" --notes "Versao $currentVersion"
    Write-Output "5/5 release $tag criada"
}
