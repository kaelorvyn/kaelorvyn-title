$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$OutputDir = Join-Path $ProjectRoot 'outputs'
$VelocityJar = Join-Path $OutputDir 'KaelorvynTitle-Velocity-1.0.0.jar'
$PaperJar = Join-Path $OutputDir 'KaelorvynTitle-Paper-1.0.0.jar'
$ProxyPlugins = 'D:\MC\server\[25565] 代理端\plugins'
$PaperServers = @(
    '[25566]登入',
    '[25567]大厅',
    '[25568]lifesteal生存',
    '[25578] 击退战争',
    '[25579] 小游戏服',
    '[25580] 驱魔传',
    '[21001]天坑乱斗',
    '[21002] 职业战争',
    '[21032] 竞技场',
    '[21033] 空岛战争',
    '[21044] Alley竞技',
    '[25571] 起床战争',
    '[25572] 搭路练习'
)
$NeedPacketEvents = @(
    '[21001]天坑乱斗',
    '[21002] 职业战争',
    '[21032] 竞技场',
    '[21033] 空岛战争',
    '[21044] Alley竞技',
    '[25572] 搭路练习',
    '[25578] 击退战争',
    '[25579] 小游戏服',
    '[25580] 驱魔传'
)
$PacketEventsSource = 'D:\MC\server\[25568]lifesteal生存\plugins\packetevents-spigot-2.13.0.jar'

if (-not (Test-Path -LiteralPath $VelocityJar)) { throw "缺少 Velocity jar：$VelocityJar" }
if (-not (Test-Path -LiteralPath $PaperJar)) { throw "缺少 Paper jar：$PaperJar" }

Copy-Item -LiteralPath $VelocityJar -Destination (Join-Path $ProxyPlugins 'KaelorvynTitle-Velocity-1.0.0.jar') -Force
Write-Output "已部署 Velocity：$(Join-Path $ProxyPlugins 'KaelorvynTitle-Velocity-1.0.0.jar')"

foreach ($server in $PaperServers) {
    $target = Join-Path 'D:\MC\server' (Join-Path $server 'plugins')
    if (-not (Test-Path -LiteralPath $target)) {
        Write-Warning "跳过不存在的目录：$target"
        continue
    }
    Copy-Item -LiteralPath $PaperJar -Destination (Join-Path $target 'KaelorvynTitle-Paper-1.0.0.jar') -Force
    Write-Output "已部署 Paper：$target"
}

foreach ($server in $NeedPacketEvents) {
    $target = Join-Path 'D:\MC\server' (Join-Path $server 'plugins')
    if (-not (Test-Path -LiteralPath $target)) {
        continue
    }
    $dest = Join-Path $target 'packetevents-spigot-2.13.0.jar'
    if (-not (Test-Path -LiteralPath $dest)) {
        Copy-Item -LiteralPath $PacketEventsSource -Destination $dest -Force
        Write-Output "已补装 PacketEvents：$dest"
    }
}

$oldTitlePlugins = @(
    (Join-Path $ProxyPlugins 'VelocityTitle-0.2.2.jar'),
    (Join-Path $ProxyPlugins 'velocity_title'),
    'D:\MC\server\[25565] 代理端\data\velocitytitle.mv.db',
    'D:\MC\server\[25565] 代理端\data\velocitytitle.trace.db',
    'D:\MC\server\[25567]大厅\plugins\HangNick-Universal-1.0.0.jar',
    'D:\MC\server\[25567]大厅\plugins\HangNick',
    'D:\MC\server\[25579] 小游戏服\plugins\[玩家称号]PlayerTitle-2.9.3.jar',
    'D:\MC\server\[25579] 小游戏服\plugins\PlayerTitle'
)
$pendingDelete = @()
foreach ($path in $oldTitlePlugins) {
    if (-not (Test-Path -LiteralPath $path)) {
        continue
    }
    try {
        Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction Stop
        Write-Output "已删除旧称号插件/数据：$path"
    } catch {
        $pendingDelete += $path
        Write-Warning "文件被运行中的服务器占用，重启后再删：$path"
    }
}
if ($pendingDelete.Count -gt 0) {
    $pendingFile = Join-Path $ProjectRoot 'logs\pending-delete.txt'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pendingFile) | Out-Null
    $pendingDelete | Set-Content -LiteralPath $pendingFile -Encoding UTF8
    Write-Output "待删除清单已写入：$pendingFile"
}

foreach ($tabConfig in @(
    'D:\MC\server\[25566]登入\plugins\TAB\config.yml',
    'D:\MC\server\[25567]大厅\plugins\TAB\config.yml'
)) {
    if (-not (Test-Path -LiteralPath $tabConfig)) {
        continue
    }
    Copy-Item -LiteralPath $tabConfig -Destination "$tabConfig.bak-kael" -Force
    $lines = Get-Content -LiteralPath $tabConfig
    $section = ''
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^tablist-name-formatting:') {
            $section = 'tablist'
            continue
        }
        if ($line -match '^scoreboard-teams:') {
            $section = 'teams'
            continue
        }
        if ($line -match '^\S') {
            $section = ''
        }
        if (($section -eq 'tablist' -or $section -eq 'teams') -and $line -match '^\s+enabled:\s*true') {
            $lines[$i] = $line -replace 'enabled:\s*true', 'enabled: false'
        }
    }
    [System.IO.File]::WriteAllLines($tabConfig, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Output "已关闭 TAB 名称格式化/队伍管理：$tabConfig"
}

$velocitabConfig = 'D:\MC\server\[25565] 代理端\plugins\velocitab\config.yml'
if (Test-Path -LiteralPath $velocitabConfig) {
    Copy-Item -LiteralPath $velocitabConfig -Destination "$velocitabConfig.bak-kael" -Force
    $lines = Get-Content -LiteralPath $velocitabConfig
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^fallback_enabled:') {
            $lines[$i] = 'fallback_enabled: false'
        }
        if ($lines[$i] -match '^send_scoreboard_packets:') {
            $lines[$i] = 'send_scoreboard_packets: false'
        }
    }
    [System.IO.File]::WriteAllLines($velocitabConfig, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Output "已关闭 Velocitab Tab 管理/队伍包：$velocitabConfig"
}

Write-Output "部署完成。重启代理端和各子服后生效。"
