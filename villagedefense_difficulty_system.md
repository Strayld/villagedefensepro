# 🎯 Difficulty System – VillageDefensePro

## 📌 Overview
This document defines a scalable **difficulty system** for VillageDefensePro.

The goal is to:
- Keep the first implementation **simple and fast**
- Allow future expansion into a **fully advanced gameplay system**

---

## 🟢 V1 – Simple Difficulty System (Recommended)

### 🎮 Available Difficulties
- Easy
- Normal
- Hard

### ⚙️ Core Modifiers
Each difficulty applies configurable multipliers:

- 🧟 Mob Health Multiplier
- ⚔️ Mob Damage Multiplier
- ⚡ Movement Speed Multiplier (slight adjustment)
- 💰 Reward Multiplier (coins, XP, etc.)

### 💡 Implementation Notes
- Uses **multipliers only**
- No need to rewrite core systems
- Acts as a **parameter layer** on top of existing mechanics

➡️ This version is:
- Fast to implement  
- Easy to maintain  
- Fully scalable for future updates  

---

## 🔧 Configuration (IMPORTANT)

All difficulty settings MUST be configurable:

### 📍 Location
- `ArenaSetupGUI`
  - New sub-GUI: **"Gameplay"**

### ⚙️ Configurable Per Arena
Each arena can define:
- Enabled difficulties
- Mob health multiplier per difficulty
- Mob damage multiplier per difficulty
- Movement speed multiplier per difficulty
- Reward multiplier per difficulty

---

## 🔴 Advanced System (Future Expansion)

The system can evolve into a more complex gameplay layer.

### 🚀 Possible Features
- 🧠 Different mob behaviors per difficulty
- 🌊 Different wave compositions
- 👑 Difficulty-specific bosses
- 🎁 Unique rewards per difficulty
- 🗺️ Difficulty configurable per arena (already planned in V1)
- 🗳️ Pre-game difficulty voting system

---

## ⚠️ Complexity Notes

### 🟢 Simple Version
- Low complexity
- Mostly config-driven
- Minimal impact on existing systems

### 🟠 Advanced Version
- Medium to high complexity
- Requires deeper integration with:
  - Wave system
  - Mob spawning
  - Reward logic
  - Game flow

---

## 🎯 Design Philosophy

Start simple → Expand later

- Deliver value quickly
- Gather player feedback
- Iterate toward a more advanced system

---

## ✅ Summary

| Feature                         | V1 | Future |
|--------------------------------|----|--------|
| Difficulty levels              | ✅ | ✅ |
| Stat multipliers               | ✅ | ✅ |
| Per-arena configuration        | ✅ | ✅ |
| Gameplay changes               | ❌ | ✅ |
| Unique bosses / waves          | ❌ | ✅ |
| Voting system                  | ❌ | ✅ |

---
