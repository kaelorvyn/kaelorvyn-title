# KaelorvynTitle 跨服称号插件

- Velocity 端：数据存储、`/ktitle` `/knick` 命令、24 小时昵称冷却、跨服广播
- ViaProxy 端：把不符合国际版规则的网易原名持久化映射为 `NeteaseN`，在握手发送给 Velocity 前完成改名
- Paper 端：PacketEvents 重写 PlayerInfo 显示名 + 计分板队伍前后缀
- 显示格式：`[称号]中文名(原英文名)`，无昵称时 `[称号] 原英文名`
- 不符合国际版账号规则的网易玩家显示为 `[称号]网易昵称（NeteaseN）`；其昵称必须在网易游戏内修改
- 生存服检测到 SurvivalSplit 时，会在第三段尾缀追加 `[战斗]`（红色）、
  `[和平]`（绿色）或 `[无分流]`（白色，legacy/未装模组），其他子服不显示
- 每次进服都会向代理端补问一次 REQ，退出重进后称号与尾缀也会恢复
- 代理端在玩家连接子服后 10 秒主动推送 SYNC（`ServerPostConnectEvent`），
  子服 REQ 仅作 15 秒兜底
- 驱魔传服务器自动读取玩家 `kit_1` ~ `kit_8` 职业标签，方括号称号显示为
  职业（轻甲战士/重甲战士/法师/盾卫士/浪人/医师/寻宝者/武器匠）；未选职业时
  继续显示原称号，其他子服不受影响
- 读取 SurvivalSplit 档案时每次从当前插件实例动态反射，避免 SurvivalSplit
  重载后旧 Method 失效导致尾缀为空
- 服主金色 `&6`，管理员紫色 `&d`，普通玩家青色 `&b`
- 限制：称号最多 8 字，昵称最多 8 字

ViaProxy 映射文件默认位于 `D:\MC\server\[25565] 代理端\netease-players.tsv`，每行记录一个
合法内部名和 Base64 编码的网易原名。ViaProxy 首次启动插件前需要重启 ViaProxy，插件目录为
ViaProxy 启动工作目录下的 `plugins`。

## 构建

```powershell
.\scripts\build.ps1
```

产物在 `outputs/`。

## 数据库

```powershell
.\scripts\setup-db.ps1
```

创建 MariaDB `kaeltitle` 库和专用账号。

## 部署

```powershell
.\scripts\deploy.ps1
```

部署后重启代理端和各子服生效。

代理端停止期间运行 `.\scripts\cleanup-after-restart.ps1`，删除被旧进程占用的
`VelocityTitle-0.2.2.jar`，再启动代理端。
