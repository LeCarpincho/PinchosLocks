<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21+-green?style=for-the-badge&logo=minecraft" alt="Minecraft">
  <img src="https://img.shields.io/badge/Paper-Supported-blue?style=for-the-badge" alt="Paper">
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License">
  <img src="https://img.shields.io/github/v/release/LeCarpincho/PinchosLocks?style=for-the-badge" alt="Release">
</p>

<h1 align="center">🔒 Pinchos Locks</h1>

<p align="center">
  <b>A premium lock & security system for Minecraft servers</b><br>
  Protect chests, doors, and more with tiered locks and lockpicks!
</p>

---

## ✨ Features

- **🔐 Tiered Lock System** - Bronze, Silver, and Gold locks with increasing security
- **🗝️ Key System** - Create and share keys with trusted players
- **🔧 Lockpicking** - Minigame-style lockpicking with skill-based progression
- **👥 Trust System** - Grant access to friends without giving them keys
- **🛡️ Full Protection** - Blocks protected from explosions, pistons, hoppers, and mobs
- **🎨 Customizable** - Full configuration with MiniMessage color support
- **🌍 Multi-language** - English and Spanish included, easily add more

## 📦 Installation

1. Download the latest release from [Releases](https://github.com/LeCarpincho/PinchosLocks/releases)
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in `plugins/PinchosLocks/config.yml`

## 🎮 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/lock` | Place a lock on the block you're looking at | `pinchoslocks.use` |
| `/lock info` | View lock information | `pinchoslocks.info` |
| `/lock remove` | Remove your lock | `pinchoslocks.use` |
| `/lock trust <player>` | Trust a player | `pinchoslocks.trust` |
| `/lock untrust <player>` | Untrust a player | `pinchoslocks.trust` |
| `/lock trustlist` | List trusted players | `pinchoslocks.trust` |
| `/lock key` | Get a key for your lock | `pinchoslocks.key` |
| `/lock give <player> <tier> [amount]` | Give locks (Admin) | `pinchoslocks.admin` |
| `/lock reload` | Reload configuration | `pinchoslocks.admin` |
| `/lockpick give <player> <tier> [amount]` | Give lockpicks (Admin) | `pinchoslocks.admin` |

## 🔑 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `pinchoslocks.use` | Basic lock features | `true` |
| `pinchoslocks.tier.bronze` | Use Bronze locks | `true` |
| `pinchoslocks.tier.silver` | Use Silver locks | `false` |
| `pinchoslocks.tier.gold` | Use Gold locks | `false` |
| `pinchoslocks.lockpick.basic` | Use Basic lockpicks | `true` |
| `pinchoslocks.lockpick.advanced` | Use Advanced lockpicks | `true` |
| `pinchoslocks.lockpick.master` | Use Master lockpicks | `true` |
| `pinchoslocks.bypass` | Bypass all locks | `op` |
| `pinchoslocks.admin` | Admin commands | `op` |

## 🔧 Lock Tiers

| Tier | Difficulty | Pickable By |
|------|------------|-------------|
| 🥉 Bronze | Easy (25) | Basic, Advanced, Master |
| 🥈 Silver | Medium (50) | Advanced, Master |
| 🥇 Gold | Hard (85) | Master only |

## 🔨 Lockpick Tiers

| Tier | Success Bonus | Durability | Can Pick |
|------|---------------|------------|----------|
| Basic | +0% | 5 uses | Bronze only |
| Advanced | +15% | 15 uses | Bronze, Silver |
| Master | +35% | 30 uses | All locks |

## 📋 Requirements

- **Minecraft:** 1.21+
- **Server:** Paper, Spigot, or Bukkit
- **Java:** 21+

## 🤝 Contributing

Contributions are welcome! Feel free to:
- Report bugs via [Issues](https://github.com/LeCarpincho/PinchosLocks/issues)
- Submit pull requests
- Suggest new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/LeCarpincho">MrSingu</a>
</p>
