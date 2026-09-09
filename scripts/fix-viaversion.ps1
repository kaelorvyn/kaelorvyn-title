$ErrorActionPreference = 'Stop'

$source = 'D:\MC\server\[25575]经验起床战争2.0.5c\[1]XP大厅服\plugins\ViaVersion-4.10.1.jar'
if (-not (Test-Path -LiteralPath $source)) {
    throw "未找到 ViaVersion-4.10.1：$source"
}

$targets = @(
    @{ Dir = 'D:\MC\server\[21001]天坑乱斗\plugins'; Remove = @('ViaVersion-4.4.1.jar') },
    @{ Dir = 'D:\MC\server\[21002] 职业战争\plugins'; Remove = @('ViaVersion-4.4.1.jar') },
    @{ Dir = 'D:\MC\server\[21032] 竞技场\plugins'; Remove = @('ViaVersion-4.4.1.jar') },
    @{ Dir = 'D:\MC\server\[21033] 空岛战争\plugins'; Remove = @('ViaVersion-4.4.1.jar') },
    @{ Dir = 'D:\MC\server\[21044] Alley竞技\plugins'; Remove = @('ViaVersion-4.4.1.jar') },
    @{ Dir = 'D:\MC\server\[25571] 起床战争\plugins'; Remove = @('ViaVersion-5.3.2.jar') },
    @{ Dir = 'D:\MC\server\[25572] 搭路练习\plugins'; Remove = @('ViaVersion.jar', 'ViaVersion-5.0.0.jar') }
)

foreach ($target in $targets) {
    if (-not (Test-Path -LiteralPath $target.Dir)) {
        Write-Warning "跳过不存在目录：$($target.Dir)"
        continue
    }
    $blocked = $false
    foreach ($name in $target.Remove) {
        $path = Join-Path $target.Dir $name
        if (Test-Path -LiteralPath $path) {
            try {
                Remove-Item -LiteralPath $path -Force -ErrorAction Stop
                Write-Output "已删除 $path"
            } catch {
                $blocked = $true
                Write-Warning "文件被占用，跳过：$path"
            }
        }
    }
    if (-not $blocked) {
        $dest = Join-Path $target.Dir 'ViaVersion-4.10.1.jar'
        Copy-Item -LiteralPath $source -Destination $dest -Force
        Write-Output "已部署 $dest"
    } else {
        Write-Output "待停止服务器后重跑本脚本：$($target.Dir)"
    }
}

Write-Output 'ViaVersion 修复完成。'
