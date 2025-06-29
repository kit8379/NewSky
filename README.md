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

### ✨ Features (So Far)

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
- ✅ MySQL Server
- ✅ [AdvancedSlimePaper Core 1.21.4](https://infernalsuite.com/download/asp/)
- ✅ [AdvancedSlimePaper Plugin 1.21.4](https://infernalsuite.com/download/asp/)

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
# Whether the plugin is in debug mode. More detailed logs will be printed if this is set to true.
debug: false

# The settings for the plugin.
# The plugin uses MySQL/MariaDB to store the island data.
MySQL:
  host: "localhost"
  port: 3306
  database: "newsky"
  username: "root"
  password: "password"
  properties: "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8"
  prefix: "newsky_"

  # The settings for the HikariCP connection pool.
  # Usually no need to change these values.
  max-pool-size: 10
  connection-timeout: 30000
  cache-prep-statements: true
  prep-stmt-cache-size: 250
  prep-stmt-cache-sql-limit: 2048

# The settings for the Redis server.
# The plugin uses Redis to communicate between servers in the broker and to store the island cache data.
redis:
  host: "localhost"
  port: 6379
  password: ""
  # The below two settings are usually not needed to change.
  # The database index to use for centralized cache.
  # The channel to use for pub/sub communication between servers.
  # If you have different groups of servers under the same broker, you can change the database and channel to separate them.
  database: 0
  channel:
    cache: "newsky-cache-channel-0"
    island: "newsky-island-channel-0"


# The settings for the server.
server:
  # The name of this server.
  # Must be equal to the server name in the BungeeCord / Velocity config.
  name: "server1"
  # Whether the server is the lobby server.
  # If the server is the lobby server, the plugin will not load islands on this server.
  lobby: false
  # The interval that the server send heartbeat to the other server.
  # Normally, the server will add/remove itself from the active server list when the server starts/stops.
  # But if the server is down unexpectedly, the other servers will not know about it.
  # So this heartbeat system will send heartbeat to the other servers periodically to let them know that this server is still active.
  # Usually no need to change this value.
  # The default value is 5 seconds.
  heartbeat-interval: 5
  # The selector type. Can be "random" or "round-robin".
  # "random" means the plugin will randomly select a server from the server list.
  # "round-robin" means the plugin will select the next server in the list.
  # "mspt" means the plugin will select the server with the lowest average MSPT (milliseconds per tick).
  # This selector will apply for both island server and lobby server selection logic.
  selector: "round-robin"
  # If the selector is "mspt", this is the interval that the plugin updates the MSPT data.
  # If you are not using "mspt" selector, this value will be ignored.
  # The default value is 10 seconds.
  mspt-update-interval: 10

# The fallback lobby configuration for when players need to be teleported out of an island.
# E.G. lock island, delete island, unload island, etc.
lobby:
  # The list of server names of the lobby servers.
  # These must match the server names defined in your BungeeCord / Velocity config.
  # The plugin will select one of these servers to teleport the player based on the selector type above.
  server-names:
    - "lobby"
  # The world name of the lobby.
  # Only one lobby world is supported.
  # All the servers you listed above must have this world.
  world-name: "world"
  location:
    x: 0.0
    y: 100.0
    z: 0.0
    yaw: 0.0
    pitch: 0.0


# The settings for the island.
island:
  # The template world name of the new island.
  # The plugin will create a new island based on this template.
  # The template world store in the template folder in the plugin folder.
  template: "default"

  # The max size of the island. 100 means the island is 100x100.
  size: 100

  # The default spawn point of the island.
  spawn:
    x: 0
    y: 132
    z: 0
    yaw: 180
    pitch: 0

  # The interval that the server checks and unloads the inactive islands.
  # The default value is 300 seconds (5 minutes).
  island-unload-interval: 300

  # The interval that the plugin update the island level.
  # The default value is 120 seconds (2 minutes).
  level-update-interval: 120

  # The gamerules to apply to island worlds during loading.
  gamerules:
    keepInventory: true
    doImmediateRespawn: false

# The settings for the command
command:
  # The mode when the player uses the /island command.
  # The mode can be "island", "help".
  # The "help" mode means the player will see the help message of the island command.
  # The "island" mode means the player will directly teleport to default home if they have island or directly create a new island if they don't have one.
  # The default mode is "help".
  base-command-mode: "island"
```

---

### ✅ Completed Milestones

- Redis-based command synchronization
- Fully async database + Redis centralized caching system
- Cross-server teleport handling
- Local + distributed island world operation

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
- Translating `messages.yml` to your native language

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
