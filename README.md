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
message broker and sync layer, allowing servers to share island metadata, player presence, teleport requests, and more
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
