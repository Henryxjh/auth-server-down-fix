[English](README.md) | [简体中文](README_CN.md)

# Auth Server Down Fix

Auth Server Down Fix is a server-side NeoForge mod for Minecraft 1.21.1. It allows authenticated players to join an online-mode server when the server cannot reach Mojang's session authentication service.

The client must still successfully complete Mojang's `joinServer` request. This mod does not support offline-mode clients and does not bypass invalid sessions.

## How It Works

When the server's `hasJoinedServer` request fails because the authentication service is unavailable, the mod can obtain the player's UUID from a configured profile lookup API or a static name-to-UUID mapping. The recovered profile is then passed back into the normal server login flow.

Unsafe login mode is disabled every time the server starts. A permission-level-4 administrator must enable it before fallback login is allowed:

```mcfunction
/authserverdownfix enableUnsafeLogin true
```

Disable it again with:

```mcfunction
/authserverdownfix enableUnsafeLogin false
```

Check the current state with:

```mcfunction
/authserverdownfix status
```

Manage static fallback profiles with:

```mcfunction
/authserverdownfix fallbackProfiles add Notch
/authserverdownfix fallbackProfiles remove Notch
/authserverdownfix fallbackProfiles list
```

The `add` command queries the currently configured profile lookup API set and writes the returned name-to-UUID pair to `fallbackProfiles`.

## Security Boundary

This mod is server-side only. Clients must be able to contact Mojang and complete normal client-side authentication.

Fallback UUID lookup identifies the UUID associated with a player name, but it is not equivalent to a successful live `hasJoinedServer` response. Unsafe login mode is therefore explicit, temporary, and disabled on every server startup by default.

## Authentication Recovery

After the first authentication-service failure, the mod checks the service every five minutes.

When the service becomes available again, or when a player successfully completes normal online authentication before the next check, the mod:

- Stops the recovery check task.
- Broadcasts that authentication service connectivity has recovered.
- Optionally schedules players who joined through UUID fallback to be disconnected.
- Broadcasts the disconnect countdown and the names of players actually disconnected.

Players are removed from the unsafe-player list when they leave the server.

## Installation

1. Install NeoForge for Minecraft 1.21.1.
2. Place the mod jar in the dedicated server's `mods` directory.
3. Start the server to generate `config/authserverdownfix-common.toml`.
4. Configure the UUID lookup behavior as needed.

The mod is not required on clients.

## Configuration

Main configuration file:

```text
config/authserverdownfix-common.toml
```

### UUID Lookup

By default, the mod tries these official profile lookup APIs:

```text
https://api.mojang.com/users/profiles/minecraft/{name}
https://api.minecraftservices.com/minecraft/profile/lookup/name/{name}
```

To use only custom APIs:

```toml
useCustomProfileApis = true
customProfileApis = ["https://example.com/profile/{name}"]
```

Supported player-name placeholders:

```text
{name}
<name>
%s
```

API responses must use the official profile format:

```json
{
  "id": "069a79f444e94726a5befca90e38aaf5",
  "name": "Notch"
}
```

Static fallback mappings can be configured with dashed or undashed UUIDs:

```toml
fallbackProfiles = [
  "Notch=069a79f444e94726a5befca90e38aaf5"
]
```

### Recovery And Disconnects

```toml
# Default: 60. Set to 0 to disable automatic disconnection.
unsafePlayerKickDelaySeconds = 60
```

The following broadcasts are configurable:

```toml
unsafeLoginEnabledBroadcast = "..."
unsafeLoginDisabledBroadcast = "..."
authenticationRecoveredBroadcast = "..."
unsafePlayerKickCountdownBroadcast = "... {seconds} ..."
unsafePlayersKickedBroadcast = "... {players} ..."
```

### Manual Unsafe Startup

To force unsafe login mode on at startup, manually create:

```text
config/authserverdownfix-unsafe.toml
```

With:

```toml
forceUnsafeLoginOnStartup = true
```

This file is intentionally not generated automatically. Unsafe login mode can still be disabled using the administrator command.

## Building

```bash
./gradlew build
```

The built jar is written to `build/libs/`.
