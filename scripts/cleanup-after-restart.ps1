$ErrorActionPreference = 'Stop'

$extraPaths = @(
    'D:\MC\server\[25565] 代理端\plugins\VelocityTitle-0.2.2.jar',
    'D:\MC\server\[25565] 代理端\plugins\velocity_title',
    'D:\MC\server\[25565] 代理端\data\velocitytitle.mv.db',
    'D:\MC\server\[25565] 代理端\data\velocitytitle.trace.db',
    'D:\MC\server\[25567]大厅\plugins\HangNick-Universal-1.0.0.jar',
    'D:\MC\server\[25567]大厅\plugins\HangNick',
    'D:\MC\server\[25579] 小游戏服\plugins\[玩家称号]PlayerTitle-2.9.3.jar',
    'D:\MC\server\[25579] 小游戏服\plugins\PlayerTitle'
)
foreach ($path in $extraPaths) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Recurse -Force
        Write-Output "已删除残留：$path"
    }
}

$pending = 'C:\Users\Administrator\Documents\Codex\project-0012-KaelorvynTitle-跨服称号插件\logs\pending-delete.txt'
if (Test-Path -LiteralPath $pending) {
    foreach ($path in Get-Content -LiteralPath $pending) {
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
            Write-Output "已删除：$path"
        }
    }
    Remove-Item -LiteralPath $pending -Force
}
Write-Output '清理完成。'
