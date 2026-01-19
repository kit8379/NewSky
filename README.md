<h1 align="center"><img height="35" src="https://cdn3.emoji.gg/emojis/9330-minecraftcube.gif" alt=""> NewSky - A Revolutionary Skyblock Experience</h1>

<div align="center">
    <strong>Scalable. Async. Redis. Built for Multi-Server.</strong>
</div>

---

<h2 align="center">⚠️ Important Notice ⚠️</h2>
<h3 align="center">NewSky is currently in ALPHA stage and not recommended for production use.</h3>

🌟 **NewSky** delivers a next-generation Skyblock experience, rebuilt with performance, modularity, and scalability in
mind. Unlike traditional plugins that rely on a single server to host all islands, NewSky introduces a distributed
architecture powered by Redis and asynchronous design patterns to support multi-server environments efficiently.

### Why Multi-Server Skyblock?

Historically, most Skyblock plugins placed all islands in a single world or server instance. While this setup worked for
small to mid-sized communities, it became increasingly problematic as player counts grew. Hundreds of active islands and
players in one JVM instance led to frequent TPS drops, lag spikes, and memory issues. This not only affected gameplay
quality but also discouraged long-term player retention due to unstable performance.

NewSky addresses this by enabling **horizontal scaling**—the ability to distribute island worlds across multiple servers
in a cluster. Each server handles a subset of island worlds, reducing load and isolating lag. Redis acts as the central
message islandBroker and sync layer, allowing servers to share island metadata, player presence, teleport requests, and more
in real time.

This approach provides:

- More stable performance under higher player loads
- Separation of island world lifecycles across servers
- Reduced chance of one overloaded world affecting others
- Flexibility to expand capacity just by adding more servers

NewSky provide smoother gameplay and better scalability for modern Minecraft networks.

### 🔧 Technologies Used

- Paper
- Redis
- MySQL
- AdvancedSlimeWorldManager

---

### ✨ Features

- 🏝️ Dynamic Island Creation & Management (via Redis-powered server distribution)
- 🌐 Cross-Server Compatibility (island data sync, teleport, player presence)
- 💾 Redis-Backed Caching with MySQL Persistence
- 📦 Async world loading/unloading with support for AdvancedSlimeWorldManager
- 🧑‍🤝‍🧑 Coop, Member, Ban, and Ownership management
- ⚙️ Fully configurable commands, messages, and permissions
- ⛏️ Modular architecture for future feature extensions
- 🧭 Command-based island teleportation with async handling
- 🖥️ Multi-server heartbeat tracking and island routing

---

### 📦 Dependencies

Required:

- ✅ Redis Server
- ✅ MySQL/MariaDB Server
- ✅ [AdvancedSlimePaper Core 1.21.11](https://infernalsuite.com/download/asp/)
- ✅ [AdvancedSlimePaper Plugin 1.21.11](https://infernalsuite.com/download/asp/)

Optional:

- 🔁 PlaceholderAPI

---

### 🛠️ Installation

1. Download the latest version of **NewSky** from the [Releases page](https://github.com/kit8379/NewSky/releases).
2. Go to [https://infernalsuite.com/download/asp/](https://infernalsuite.com/download/asp/) and download *
   *AdvancedSlimePaper Core 1.21.4** and **AdvancedSlimePaper Plugin 1.21.4**.
3. Place **NewSky.jar** and the downloaded **AdvancedSlimePaper plugin JAR** into your server’s `plugins/` folder.
4. Make sure your **Redis** and **MySQL** servers are running and accessible from all Minecraft servers in your network.
5. Start the server once. The plugin will auto-generate configuration files like `config.yml`, `commands.yml`, and
   `messages.yml`.
6. Configure the generated `config.yml` to match your cluster's Redis/MySQL settings and server role (lobby/non-lobby),
   no need to configure AdvancedSlimePaper Plugin.
7. Copy the configured plugin folder to **all servers** that are part of your Skyblock cluster group.
8. Restart all servers. You're done!

> Note: This plugin is built for multi-server setups. You must use the same Redis/MySQL settings across all servers for
> proper sync.

---

### 📜 Configuration
The plugin uses YAML configuration files for easy customization. Key files include:
- `config.yml`: Main plugin settings (Redis, MySQL, server roles)
- `commands.yml`: Command definitions and permissions
- `messages.yml`: Customizable messages for commands and events
- `levels.yml`: Block levels configuration

config.yml:
```yaml
# ===================================================================
# NewSky Configuration
#
# Design:
# - `server`   : per-server identity (the only section that usually differs)
# - everything else: usually the same across all servers in the network
# ===================================================================

# ===================================================================
# Server Identity (per-server)
# ===================================================================
server:
   # Must match the server name in your BungeeCord / Velocity config.
   name: "server1"

   # If true, this server acts as a lobby-only node and will NOT load islands.
   lobby-only: false


# ===================================================================
# Database
# ===================================================================
# Stores persistent island/player data.
mysql:
   host: "localhost"
   port: 3306
   database: "newsky"
   username: "root"
   password: "password"

   # Whether to use SSL for the database connection.
   use-ssl: false

   # JDBC properties appended to the connection URL.
   # Format: "key1=value1&key2=value2"
   # Example: "autoReconnect=true&useUnicode=true&characterEncoding=utf8"
   properties: "autoReconnect=true&useUnicode=true&characterEncoding=utf8"

   # Table prefix for all plugin tables.
   prefix: "newsky_"

# ===================================================================
# Redis
# ===================================================================
# Used for cross-server communication and centralized caching.
redis:
   host: "localhost"
   port: 6379
   password: ""

   # Use these to separate multiple independent NewSky networks sharing the same Redis (should be identical on all servers in the same network).
   database: 0

   # Pub/Sub channels (should be identical on all servers in the same network).
   channel:
      cache: "newsky-cache-channel-0"
      island: "newsky-island-channel-0"


# ===================================================================
# Network
# ===================================================================
network:
   # Heartbeat period (seconds).
   # Other servers use this to detect whether this server is still active.
   heartbeat-interval-seconds: 5

   # Server selection strategy used when choosing an island server / lobby server.
   # Options: "random", "round-robin", "mspt"
   selector: "round-robin"

   # MSPT refresh period (seconds). Only used when selector = "mspt".
   mspt-update-interval-seconds: 10


# ===================================================================
# Lobby Fallback
# ===================================================================
# Where players are sent when they must leave an island forcibly
# (e.g., island unload/delete/lock, expelled, etc.).
lobby:
   # Names must match BungeeCord / Velocity server names.
   # Multiple servers are supported for load balancing.
   server-names:
      - "lobby"

   # Lobby world name (must exist on all lobby servers above).
   world-name: "world"

   # Fallback spawn location inside the lobby world.
   location:
      x: 0.0
      y: 100.0
      z: 0.0
      yaw: 0.0
      pitch: 0.0


# ===================================================================
# Island
# ===================================================================
island:
   # Template world folder name under the plugin template directory.
   template: "default"

   # Island size (100 means 100x100).
   size: 100

   # Default island spawn position.
   spawn:
      x: 0
      y: 132
      z: 0
      yaw: 180
      pitch: 0

   # Inactive island unload check interval (seconds).
   island-unload-interval-seconds: 300

   # Gamerules applied to island worlds when they are loaded.
   gamerules:
      keep_inventory: true
      immediate_respawn: false


# ===================================================================
# Commands
# ===================================================================
command:
   # Default behavior of `/island` when used with no arguments.
   # Options:
   # - "help"   : show help
   # - "island" : teleport to island home if exists, otherwise create a new island
   base-command-mode: "island"


# ===================================================================
# Debug
# ===================================================================
# If true, prints more detailed logs.
debug: false
```

commands.yml:
```yaml
# No color codes, no formatting, just plain text in this file.
# Color codes and formatting will be handled in messages.yml.
# The permission nodes are the default permission nodes for the commands and the name that use in plugins.yml.
# If you want to change the permission nodes, you need to manually add them to your permissions plugin.
commands:
   player:
      create:
         aliases: [ ]
         permission: "island.player.create"
         syntax: ""
         description: "Create a new island"

      delete:
         aliases: [ ]
         permission: "island.player.delete"
         syntax: ""
         description: "Delete your island"

      setowner:
         aliases: [ ]
         permission: "island.player.setowner"
         syntax: "<player>"
         description: "Set a new owner for your island"

      leave:
         aliases: [ ]
         permission: "island.player.leave"
         syntax: ""
         description: "Leave your island"

      invite:
         aliases: [ ]
         permission: "island.player.invite"
         syntax: "<player>"
         description: "Invite a player to join your island"

      accept:
         aliases: [ ]
         permission: "island.player.accept"
         syntax: ""
         description: "Accept a pending island invitation"

      reject:
         aliases: [ ]
         permission: "island.player.reject"
         syntax: ""
         description: "Reject a pending island invitation"

      removemember:
         aliases: [ ]
         permission: "island.player.removemember"
         syntax: "<player>"
         description: "Remove a member from your island"

      expel:
         aliases: [ ]
         permission: "island.player.expel"
         syntax: "<player>"
         description: "Expel a player from your island"

      coop:
         aliases: [ ]
         permission: "island.player.coop"
         syntax: "<player>"
         description: "Set a player as your island co-op partner"

      uncoop:
         aliases: [ ]
         permission: "island.player.uncoop"
         syntax: "<player>"
         description: "Remove a co-op partner from your island"

      cooplist:
         aliases: [ ]
         permission: "island.player.cooplist"
         syntax: ""
         description: "Show the list of your island’s co-op members"

      ban:
         aliases: [ ]
         permission: "island.player.ban"
         syntax: "<player>"
         description: "Ban a player from your island"

      unban:
         aliases: [ ]
         permission: "island.player.unban"
         syntax: "<player>"
         description: "Unban a player from your island"

      banlist:
         aliases: [ ]
         permission: "island.player.banlist"
         syntax: ""
         description: "Show the list of players banned from your island"

      lock:
         aliases: [ ]
         permission: "island.player.lock"
         syntax: ""
         description: "Toggle your island lock state"

      pvp:
         aliases: [ ]
         permission: "island.player.pvp"
         syntax: ""
         description: "Toggle PvP state on your island"

      sethome:
         aliases: [ ]
         permission: "island.player.sethome"
         syntax: "[homeName]"
         description: "Set a new home for your island"

      delhome:
         aliases: [ ]
         permission: "island.player.delhome"
         syntax: "<homeName>"
         description: "Delete an existing home on your island"

      home:
         aliases: [ "go" ]
         permission: "island.player.home"
         syntax: "[homeName]"
         description: "Teleport to your island home"


      setwarp:
         aliases: [ ]
         permission: "island.player.setwarp"
         syntax: "[warpName]"
         description: "Set a new warp for your island"

      delwarp:
         aliases: [ ]
         permission: "island.player.delwarp"
         syntax: "<warpName>"
         description: "Delete an existing warp on your island"

      warp:
         aliases: [ ]
         permission: "island.player.warp"
         syntax: "<player> [warpName]"
         description: "Teleport to another player's island warp"

      info:
         aliases: [ ]
         permission: "island.player.info"
         syntax: "[player]"
         description: "Show island information"

      level:
         aliases: [ ]
         permission: "island.player.level"
         syntax: ""
         description: "Show your island level"

      top:
         aliases: [ ]
         permission: "island.player.top"
         syntax: ""
         description: "Show top islands ranked by level"

      value:
         aliases: [ ]
         permission: "island.player.value"
         syntax: ""
         description: "Show the value of the block in your hand"

      lobby:
         aliases: [ ]
         permission: "island.player.lobby"
         syntax: ""
         description: "Teleport to the lobby"

      help:
         aliases: [ ]
         permission: "island.player.help"
         syntax: "[page]"
         description: "Show available island commands"

   admin:
      create:
         aliases: [ ]
         permission: "newsky.admin.create"
         syntax: "<player>"
         description: "Create an island for a player"

      delete:
         aliases: [ ]
         permission: "newsky.admin.delete"
         syntax: "<player>"
         description: "Delete a player's island"

      load:
         aliases: [ ]
         permission: "newsky.admin.load"
         syntax: "<player>"
         description: "Load another player's island"

      unload:
         aliases: [ ]
         permission: "newsky.admin.unload"
         syntax: "<player>"
         description: "Unload another player's island"

      addmember:
         aliases: [ ]
         permission: "newsky.admin.addmember"
         syntax: "<member> <owner>"
         description: "Force-add a member to another player's island"

      removemember:
         aliases: [ ]
         permission: "newsky.admin.removemember"
         syntax: "<member> <owner>"
         description: "Force-remove a member from another player's island"

      coop:
         aliases: [ ]
         permission: "newsky.admin.coop"
         syntax: "<owner> <player>"
         description: "Add a co-op partner to another player's island"

      uncoop:
         aliases: [ ]
         permission: "newsky.admin.uncoop"
         syntax: "<owner> <player>"
         description: "Remove a co-op partner from another player's island"

      ban:
         aliases: [ ]
         permission: "newsky.admin.ban"
         syntax: "<owner> <player>"
         description: "Ban a player from another player's island"

      unban:
         aliases: [ ]
         permission: "newsky.admin.unban"
         syntax: "<owner> <player>"
         description: "Unban a player from another player's island"

      lock:
         aliases: [ ]
         permission: "newsky.admin.lock"
         syntax: "<player>"
         description: "Toggle lock state of another player's island"

      pvp:
         aliases: [ ]
         permission: "newsky.admin.pvp"
         syntax: "<player>"
         description: "Toggle PvP state of another player's island"

      sethome:
         aliases: [ ]
         permission: "newsky.admin.sethome"
         syntax: "<player> <home>"
         description: "Set a home on another player's island at your location"

      delhome:
         aliases: [ ]
         permission: "newsky.admin.delhome"
         syntax: "<player> <home>"
         description: "Delete a home from another player's island"

      home:
         aliases: [ "go" ]
         permission: "newsky.admin.home"
         syntax: "<player> [home] [target]"
         description: "Teleport a player to another player's home"

      setwarp:
         aliases: [ ]
         permission: "newsky.admin.setwarp"
         syntax: "<player> <warp>"
         description: "Set a warp on another player's island at your location"

      delwarp:
         aliases: [ ]
         permission: "newsky.admin.delwarp"
         syntax: "<player> <warp>"
         description: "Delete a warp from another player's island"

      warp:
         aliases: [ ]
         permission: "newsky.admin.warp"
         syntax: "<player> [warp] [target]"
         description: "Teleport a player to another player's warp"

      lobby:
         aliases: [ ]
         permission: "newsky.admin.lobby"
         syntax: "<player>"
         description: "Teleport a player to the lobby"

      reload:
         aliases: [ ]
         permission: "newsky.admin.reload"
         syntax: ""
         description: "Reload plugin configuration files"

      help:
         aliases: [ ]
         permission: "newsky.admin.help"
         syntax: "[page]"
         description: "Show available admin commands"
```

---

### 🧭 Upcoming TODO

1. Optimize code for performance and scalability.
2. Introduce more modular features like Upgrades and Biome Changers.

---

### 🤝 Contributing

We welcome PRs and community involvement! You can help by:

- Submitting bug reports and feature suggestions
- Improving the plugin code or performance
- Writing documentation and usage examples

🔗 **GitHub**: [https://github.com/kit8379/NewSky](https://github.com/kit8379/NewSky)  
💬 **Issues**: Use the GitHub Issues tab to report bugs or suggest features  
🌟 **Show Support**: Star this repo or share with other server owners!

---

### 💖 Support

If you enjoy using NewSky, please consider:

- ⭐ Starring the GitHub repository
- 🍕 Buying the developer a coffee (https://paypal.me/legendofdestiny)
- 🗣️ Sharing feedback in the Issues tab

---

### License

This plugin is licensed under the MIT License. Feel free to fork, modify, and build upon it — but please respect the
original project.

---
