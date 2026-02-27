<div align="center">

<!-- BANNER -->
```
██████╗ ██╗███╗   ██╗ ██████╗██╗  ██╗ ██████╗ ███████╗    ██╗      ██████╗  ██████╗██╗  ██╗███████╗
██╔══██╗██║████╗  ██║██╔════╝██║  ██║██╔═══██╗██╔════╝    ██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝
██████╔╝██║██╔██╗ ██║██║     ███████║██║   ██║███████╗    ██║     ██║   ██║██║     █████╔╝ ███████╗
██╔═══╝ ██║██║╚██╗██║██║     ██╔══██║██║   ██║╚════██║    ██║     ██║   ██║██║     ██╔═██╗ ╚════██║
██║     ██║██║ ╚████║╚██████╗██║  ██║╚██████╔╝███████║    ███████╗╚██████╔╝╚██████╗██║  ██╗███████║
╚═╝     ╚═╝╚═╝  ╚═══╝ ╚═════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝    ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝
```

<img src="https://readme-typing-svg.herokuapp.com?font=Fira+Code&weight=600&size=28&duration=3000&pause=1000&color=FFD700&center=true&vCenter=true&width=435&lines=%F0%9F%94%90+Premium+Lock+System" alt="Typing SVG" />

<br/>

<!-- BADGES -->
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-2D7A3A?style=for-the-badge&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Paper](https://img.shields.io/badge/Paper-Supported-3498DB?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io)
[![Spigot](https://img.shields.io/badge/Spigot-Supported-ED8106?style=for-the-badge)](https://spigotmc.org)
[![Java](https://img.shields.io/badge/Java-21+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org)

[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)
[![Release](https://img.shields.io/github/v/release/LeCarpincho/PinchosLocks?style=for-the-badge&color=brightgreen)](https://github.com/LeCarpincho/PinchosLocks/releases)
[![Downloads](https://img.shields.io/github/downloads/LeCarpincho/PinchosLocks/total?style=for-the-badge&color=blue)](https://github.com/LeCarpincho/PinchosLocks/releases)

<br/>

### 🛡️ Protect your treasures with style! 🛡️

*A fully customizable and configurable lock & security system*

[📥 Download](#-installation) • [📖 Wiki](#-commands) • [🐛 Issues](https://github.com/LeCarpincho/PinchosLocks/issues) • [💬 Discord](#-support)

</div>

---

## 🎯 Why Choose Pinchos Locks?

<table>
<tr>
<td width="50%">

### 🎨 100% Customizable
Every aspect of the plugin can be modified:
- **Custom messages** with MiniMessage support
- **Configurable items** (materials, names, lore)
- **Adjustable difficulties** per lock tier
- **Particle effects** toggle
- **Sound effects** customization
- **Multi-language support**

</td>
<td width="50%">

### ⚡ Lightweight & Optimized
Built with performance in mind:
- **Async operations** for zero lag
- **Efficient data storage** (JSON)
- **Minimal memory footprint**
- **Coroutine-based** architecture
- **Paper 1.21+ optimized**

</td>
</tr>
</table>

---

## ✨ Features

<div align="center">

| Feature | Description |
|:-------:|:------------|
| 🔐 | **Tiered Lock System** - Bronze, Silver, and Gold locks with increasing security |
| 🔧 | **Lockpicking Minigame** - Skill-based progression with visual feedback |
| 👥 | **Trust System** - Grant access to friends without sharing keys |
| 🛡️ | **Full Protection** - Blocks protected from explosions, pistons, hoppers, and mobs |
| 🎨 | **Particle Effects** - Beautiful visual feedback for all actions |
| 🌍 | **Multi-language** - English and Spanish included, easily add more |
| ⚙️ | **Fully Configurable** - Every message, item, and setting can be customized |

</div>

---

## 📦 Installation

```bash
# 1. Download the latest release
https://github.com/LeCarpincho/PinchosLocks/releases

# 2. Place in plugins folder
/plugins/PinchosLocks-x.x.x-release.jar

# 3. Restart your server
/restart

# 4. Configure to your liking!
/plugins/PinchosLocks/config.yml
/plugins/PinchosLocks/lang/
```

---

## 🎮 Commands

<details>
<summary><b>📋 Click to expand command list</b></summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/lock` | Place a lock on the block you're looking at | `pinchoslocks.use` |
| `/lock info` | View lock information | `pinchoslocks.info` |
| `/lock remove` | Remove your lock | `pinchoslocks.use` |
| `/lock trust <player>` | Trust a player | `pinchoslocks.trust` |
| `/lock untrust <player>` | Untrust a player | `pinchoslocks.trust` |
| `/lock trustlist` | List trusted players | `pinchoslocks.trust` |
| `/lock give <player> <tier> [amount]` | Give locks (Admin) | `pinchoslocks.admin` |
| `/lock reload` | Reload configuration | `pinchoslocks.admin` |
| `/lockpick give <player> <tier> [amount]` | Give lockpicks (Admin) | `pinchoslocks.admin` |

</details>

---

## 🔑 Permissions

<details>
<summary><b>🛡️ Click to expand permissions</b></summary>

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

</details>

---

## 🔒 Lock Tiers

<div align="center">

| Tier | Icon | Difficulty | Security Level |
|:----:|:----:|:----------:|:--------------:|
| **Bronze** | 🥉 | Easy (25) | ⭐ |
| **Silver** | 🥈 | Medium (50) | ⭐⭐ |
| **Gold** | 🥇 | Hard (85) | ⭐⭐⭐ |

</div>

---

## 🔨 Lockpick Tiers

<div align="center">

| Tier | Success Bonus | Durability | Can Pick |
|:----:|:-------------:|:----------:|:--------:|
| **Basic** | +0% | 5 uses | 🥉 Bronze |
| **Advanced** | +15% | 15 uses | 🥉🥈 Bronze, Silver |
| **Master** | +35% | 30 uses | 🥉🥈🥇 All |

</div>

---

## ⚙️ Configuration

<details>
<summary><b>📝 Example config.yml</b></summary>

```yaml
# ══════════════════════════════════════════════════════════════
#                    PINCHOS LOCKS CONFIG
# ══════════════════════════════════════════════════════════════

general:
  language: "en_EN"           # Language file to use
  debug: false                # Enable debug mode
  particles-enabled: true     # Enable particle effects
  lock-placement-cooldown: 1.0  # Seconds between placements

# Fully customizable lock tiers!
tiers:
  bronze:
    enabled: true
    material: COPPER_INGOT    # Change the item!
    custom-model-data: 1001
    difficulty: 25

  silver:
    enabled: true
    material: IRON_INGOT
    custom-model-data: 1002
    difficulty: 50

  gold:
    enabled: true
    material: GOLD_INGOT
    custom-model-data: 1003
    difficulty: 85
```

</details>

<details>
<summary><b>🌍 Example language file (lang/en_EN.yml)</b></summary>

```yaml
# All messages support MiniMessage format!
# Colors: <red>, <green>, <gold>, <gradient:red:blue>
# Formatting: <bold>, <italic>, <underlined>
# Hover/Click events supported!

prefix: "<gold>[<yellow>Locks<gold>]</yellow></gold>"

lock:
  placed: "<green>Lock placed successfully! <gray>({tier})"
  removed: "<red>Lock removed."
  access-denied: "<red>This container is locked!"

# Add your own messages, change colors, everything!
```

</details>

---

## 📋 Requirements

<div align="center">

| Requirement | Version |
|:-----------:|:-------:|
| ☕ Java | 21+ |
| 📄 Paper/Spigot | 1.21+ |
| 💾 Storage | ~5MB |

</div>

---

## 🤝 Contributing

Contributions are welcome! Feel free to:

- 🐛 Report bugs via [Issues](https://github.com/LeCarpincho/PinchosLocks/issues)
- 💡 Suggest new features
- 🔧 Submit pull requests
- 🌍 Add new translations

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

### 💖 Support the Project

If you like this plugin, consider:

⭐ **Starring** the repository

🐛 **Reporting** bugs you find

💬 **Sharing** with other server owners

---

<img src="https://readme-typing-svg.herokuapp.com?font=Fira+Code&size=14&duration=3000&pause=1000&color=888888&center=true&vCenter=true&width=435&lines=Made+with+%E2%9D%A4%EF%B8%8F+by+MrSingu;Powered+by+Kotlin+%2B+Coroutines" alt="Footer" />

<br/>

```
  ⠀⠀⢀⣀⠤⠿⢤⢖⡆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
  ⡔⢩⠂⠀⠒⠗⠈⠀⠉⠢⠄⣀⠠⠤⠄⠒⢖⡒⢒⠂⠤⢄⠀⠀⠀⠀
  ⠇⠤⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠀⠀⠈⠀⠈⠈⡨⢀⠡⡪⠢⡀⠀
  ⠈⠒⠀⠤⠤⣄⡆⡂⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠢⠀⢕⠱⠀
  ⠀⠀⠀⠀⠀⠈⢳⣐⡐⠐⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠀⢸⠀
  ⠀⠀⠀⠀⠀⠀    ⠀⠑⢤⢁⠀⠆⠀⠀⠀⠀⠀⢀⢰⠀⠀⠀⡀⢄⡜⠀  Carpincho Approved! 🦫
  ⠀⠀⠀⠀⠀⠀⠀⠀⠘⡦⠄⡷⠢⠤⠤⠤⠤⢬⢈⡇⢠⣈⣰⠎⠀⠀
  ⠀⠀⠀⠀⠀⠀⠀⠀⠀⣃⢸⡇⠀⠀⠀⠀⠀⠈⢪⢀⣺⡅⢈⠆⠀⠀
  ⠀⠀⠀⠀⠀⠀⠀⠶⡿⠤⠚⠁⠀⠀⠀⢀⣠⡤⢺⣥⠟⢡⠃⠀⠀⠀
  ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠉⠀⠀⠀⠀⠀⠀⠀⠀
```

**[LeCarpincho](https://github.com/LeCarpincho)** © 2024

</div>
