[English](README.md) | [简体中文](README_CN.md)

# Auth Server Down Fix

Auth Server Down Fix 是一个适用于 Minecraft 1.21.1 的服务端 NeoForge 模组。当在线模式服务器无法连接 Mojang 会话验证服务时，它可以让已经完成客户端正版验证的玩家继续登录服务器。

客户端仍然必须成功完成 Mojang 的 `joinServer` 请求。本模组不支持离线模式客户端，也不会绕过无效会话。

## 工作原理

当服务器调用 `hasJoinedServer` 时因验证服务不可用而失败，模组可以通过配置的玩家资料查询 API 或静态名称与 UUID 对应关系获得玩家 UUID，然后让玩家继续进入原版服务器登录流程。

每次服务器启动时，不安全登录模式默认关闭。必须由权限等级为 4 的管理员手动开启：

```mcfunction
/authserverdownfix enableUnsafeLogin true
```

关闭不安全登录模式：

```mcfunction
/authserverdownfix enableUnsafeLogin false
```

查看当前状态：

```mcfunction
/authserverdownfix status
```

管理静态 fallback profiles：

```mcfunction
/authserverdownfix fallbackProfiles add Notch
/authserverdownfix fallbackProfiles remove Notch
/authserverdownfix fallbackProfiles list
```

`add` 指令会请求当前配置选择的玩家资料查询 API，并把返回的玩家名称与 UUID 对应关系写入 `fallbackProfiles`。

## 安全边界

本模组仅安装在服务端。客户端必须能够连接 Mojang，并完成正常的客户端正版验证。

UUID 查询只能确认玩家名称对应的 UUID，不能等同于验证服务器成功返回的实时 `hasJoinedServer` 结果。因此，不安全登录模式需要管理员明确开启，并且默认在每次服务器启动时关闭。

## 验证服务器恢复检测

第一次发生验证服务不可用后，模组会每隔五分钟检查一次验证服务。

验证服务恢复后，或者在下一次定时检查前有玩家成功完成正常在线验证时，模组会：

- 停止恢复检测任务。
- 广播验证服务器连接已经恢复。
- 根据配置安排通过 UUID fallback 登录的玩家退出服务器。
- 广播踢出倒计时，以及实际被踢出的玩家名称列表。

玩家退出服务器后会从不安全登录玩家列表中移除。

## 安装

1. 为 Minecraft 1.21.1 安装 NeoForge。
2. 将模组 jar 放入专用服务器的 `mods` 目录。
3. 启动服务器，生成 `config/authserverdownfix-common.toml`。
4. 根据需要配置 UUID 查询行为。

客户端不需要安装本模组。

## 配置

主要配置文件：

```text
config/authserverdownfix-common.toml
```

### UUID 查询

默认依次请求以下两个官方玩家资料查询 API：

```text
https://api.mojang.com/users/profiles/minecraft/{name}
https://api.minecraftservices.com/minecraft/profile/lookup/name/{name}
```

只使用自定义 API：

```toml
useCustomProfileApis = true
customProfileApis = ["https://example.com/profile/{name}"]
```

支持的玩家名称占位符：

```text
{name}
<name>
%s
```

API 返回格式必须与官方玩家资料 API 相同：

```json
{
  "id": "069a79f444e94726a5befca90e38aaf5",
  "name": "Notch"
}
```

可以配置使用带横线或不带横线 UUID 的静态 fallback：

```toml
fallbackProfiles = [
  "Notch=069a79f444e94726a5befca90e38aaf5"
]
```

### 恢复与踢出

```toml
# 默认值为 60。设置为 0 时禁用自动踢出。
unsafePlayerKickDelaySeconds = 60
```

以下广播消息可以配置：

```toml
unsafeLoginEnabledBroadcast = "..."
unsafeLoginDisabledBroadcast = "..."
authenticationRecoveredBroadcast = "..."
unsafePlayerKickCountdownBroadcast = "... {seconds} ..."
unsafePlayersKickedBroadcast = "... {players} ..."
```

### 手动强制不安全模式启动

如需服务器启动时强制开启不安全登录模式，请手动创建：

```text
config/authserverdownfix-unsafe.toml
```

内容：

```toml
forceUnsafeLoginOnStartup = true
```

该文件不会由模组自动生成。启动后仍可使用管理员指令关闭不安全登录模式。

## 构建

```bash
./gradlew build
```

生成的 jar 位于 `build/libs/`。
