# 🌙 SmartAFK

Intelligent AFK management system that reduces server lag and improves player experience.

[![Modrinth](https://img.shields.io/modrinth/dt/smartafk?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/smartafk)
[![GitHub](https://img.shields.io/github/v/release/diork681-boop/SmartAFK?logo=github&label=GitHub)](https://github.com/diork681-boop/SmartAFK)

---

## ✨ Features

- ⏰ **Auto-AFK Detection** — Automatically detects inactive players
- 🌍 **AFK World** — Teleports AFK players to a separate world (reduces lag)
- 🔄 **Auto-Return** — Brings players back when they're active again
- 👢 **AFK Kick** — Kicks players after extended AFK (configurable)
- 🛡️ **Damage Protection** — AFK players won't take damage
- 📋 **Tab Prefix** — Shows [AFK] in player list
- 🎯 **Smart Detection** — Tracks movement, chat, commands, block breaking, and more

---

## 📝 Commands

- `/afk` — Toggle AFK mode
- `/afkstatus` — List all AFK players
- `/afkreload` — Reload configuration

---

## 🔑 Permissions

- `smartafk.afk` — Use /afk command (default: everyone)
- `smartafk.status` — View AFK players list (default: everyone)
- `smartafk.reload` — Reload config (default: OP)
- `smartafk.bypass` — Bypass AFK kick (default: OP)

---

## 🎮 Supported Versions

**Minecraft:** 1.13 — 1.21+

**Platforms:** Paper, Spigot, Bukkit, Purpur, Pufferfish

---

## 📦 Installation

1. Download the plugin from [Modrinth](https://modrinth.com/plugin/smartafk)
2. Put it in your `plugins` folder
3. Restart the server
4. Configure in `plugins/SmartAFK/config.yml`

---

## ⚙️ Configuration

```yaml
# AFK timeout in seconds
afk-timeout: 300

# Kick timeout (0 = disabled)
kick-timeout: 1800

# AFK World
afk-world:
  enabled: true
  world-name: "world_afk"

💡 Why SmartAFK?

Without AFK management:

    ❌ AFK players load chunks around them
    ❌ Mobs spawn near AFK players
    ❌ Redstone keeps running
    ❌ Server lags with many AFK players

With SmartAFK:

    ✅ AFK players moved to empty world
    ✅ No chunk loading from AFK players
    ✅ Server runs smoothly
    ✅ Slots freed up after AFK kick
