<#
.SYNOPSIS
  准备本地构建依赖：ModTheSpire/BaseMod 随仓（lib/），游戏本体 desktop-1.0.jar 需从游戏安装目录获取（版权原因不进仓）。
.EXAMPLE
  .\scripts\setup-libs.ps1 -GameDir "C:\Program Files (x86)\Steam\steamapps\common\SlayTheSpire"
.EXAMPLE
  .\scripts\setup-libs.ps1 -GameJar "D:\games\desktop-1.0.jar"
#>
param(
    [string]$GameDir = "",
    [string]$GameJar = ""
)

$ErrorActionPreference = "Stop"
$proj = Split-Path -Parent $PSScriptRoot
$lib = Join-Path $proj "lib"
New-Item -ItemType Directory -Force -Path $lib | Out-Null

foreach ($pair in @(
    @{ Name = "ModTheSpire.jar"; Url = "https://github.com/kiooeht/ModTheSpire/releases/latest/download/ModTheSpire.jar" },
    @{ Name = "BaseMod.jar";     Url = "https://github.com/daviscook477/BaseMod/releases/latest/download/BaseMod.jar" }
)) {
    $target = Join-Path $lib $pair.Name
    if (-not (Test-Path $target)) {
        Write-Host "下载 $($pair.Name) ..."
        Invoke-WebRequest -Uri $pair.Url -OutFile $target
    }
}

$gameJar = Join-Path $lib "desktop-1.0.jar"
if (Test-Path $gameJar) {
    Write-Host "游戏 jar 已就位：$gameJar"
} else {
    $candidates = @()
    if ($GameJar) { $candidates += $GameJar }
    if ($GameDir) { $candidates += (Join-Path $GameDir "desktop-1.0.jar") }
    if ($env:STS_GAME_JAR) { $candidates += $env:STS_GAME_JAR }
    $found = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $found) {
        Write-Error @"
未找到 desktop-1.0.jar。请用以下任一方式提供游戏本体 jar：
  1) .\scripts\setup-libs.ps1 -GameDir "<SlayTheSpire 安装目录>"
  2) .\scripts\setup-libs.ps1 -GameJar "<desktop-1.0.jar 完整路径>"
  3) 设置环境变量 STS_GAME_JAR 指向该 jar
"@
    }
    Copy-Item -Force $found $gameJar
    Write-Host "游戏 jar 已复制：$gameJar"
}
Write-Host "依赖就绪。现在可以执行：mvn test / mvn package"