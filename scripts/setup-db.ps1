$ErrorActionPreference = 'Stop'

$mysql = 'C:\Program Files\MariaDB 12.3\bin\mysql.exe'
$sharedConfig = 'D:\MC\server\shared\config.toml'
$configDir = 'D:\MC\server\[25565] 代理端\plugins\kaelorvyntitle'
$configPath = Join-Path $configDir 'config.properties'

if (-not (Test-Path -LiteralPath $mysql)) {
    throw "未找到 mysql：$mysql"
}
if (-not (Test-Path -LiteralPath $sharedConfig)) {
    throw "未找到共享配置：$sharedConfig"
}

$shared = Get-Content -LiteralPath $sharedConfig -Raw
$match = [regex]::Match($shared, '(?m)^pass\s*=\s*"([^"]+)"')
if (-not $match.Success) {
    throw '无法从共享配置读取 MariaDB 管理员凭据'
}

$alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'.ToCharArray()
$password = -join (1..24 | ForEach-Object { $alphabet | Get-Random })

$env:MYSQL_PWD = $match.Groups[1].Value
try {
    $sql = "CREATE DATABASE IF NOT EXISTS kaeltitle CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    $sql += " CREATE USER IF NOT EXISTS 'kaeltitle'@'localhost' IDENTIFIED BY '$password';"
    $sql += " ALTER USER 'kaeltitle'@'localhost' IDENTIFIED BY '$password';"
    $sql += " GRANT ALL PRIVILEGES ON kaeltitle.* TO 'kaeltitle'@'localhost';"
    $sql += " FLUSH PRIVILEGES;"
    & $mysql -uroot -e $sql
    if ($LASTEXITCODE -ne 0) {
        throw 'MariaDB 建库/建账号失败'
    }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Force -Path $configDir | Out-Null
$content = "database.host=localhost`n" +
    "database.port=3306`n" +
    "database.name=kaeltitle`n" +
    "database.user=kaeltitle`n" +
    "database.password=$password`n" +
    "cooldown-hours=24`n" +
    "title-max=8`n" +
    "nickname-max=8`n" +
    "owner-uuids=f9a88b70-142a-3dcb-9f91-a151ec729f38`n" +
    "admin-uuids=713fa79b-c7c9-391e-9596-9292cec87f02,003602f4-453e-3f98-aff4-fa5e5c857d5e`n" +
    "color.owner=6`n" +
    "color.admin=d`n" +
    "color.default=b`n" +
    "title.owner=服主`n" +
    "title.admin=管理员`n" +
    "title.default=玩家`n"
[System.IO.File]::WriteAllText($configPath, $content, [System.Text.UTF8Encoding]::new($false))

Write-Output "kaeltitle 数据库和专用账号已创建。"
Write-Output "插件配置已写入：$configPath"
