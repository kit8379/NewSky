<h1 align="center"><img height="35" src="https://emoji.gg/emoji/9330-minecraftcube"> NewSky - A Revolutionary Skyblock Experience</h1>
<div align="center">

</div>

🌟 **NewSky** brings an unparalleled Skyblock experience to Minecraft servers, combining innovative features with intuitive design for both players and server administrators.

⚙️ **Current Stage:** ALPHA. The plugin is under active development with basic functionalities such as island creation, management, and Redis-based caching already implemented.

🚀 **Backbone:** NewSky uses cutting-edge technologies and design patterns to ensure high performance, scalability, and reliability.

------------

### Technologies Used:
- SpigotAPI
- MySQL
- Redis
- Java

### Features:
- 🏝️ Advanced Island Management
- 🌐 Cross-server functionality with Redis
- 🔒 Enhanced Security Features
- 🚀 Optimized for High Performance
- 🛠️ Easy-to-use API for Developers

#### Plugin Hooks
- Multiverse-Core
- VoidGen

------------

### To-DO List
1. Remove the dependency of using Multiverse-Core and VoidGen.
2. Allow to store world in common share path or distributed storage system so as to load island dynmically across servers. 
(The create island feature has already implemented to dynmically create on across servers already. But the island world is currently stay in that server only. It is not hard to add this function but I have no time to implement it in recent months.)
3. Add support to using AdvancedSlimeWorldManager instead of normal Bukkit world.
4. Implement command permission and finalise the display messages that can be customize in messages.yml

------------

### Installation:
1. Download the latest version of NewSky from the [releases page](#).
2. Place the NewSky.jar file into your server's `plugins` folder.
3. Restart your server. NewSky will generate a default configuration file.
4. Configure the plugin to your needs.

### Default Configuration:

```yaml
# NewSky configuration file
DB:
  host: "127.0.0.1"
  port: 3306
  database: "newsky"
  username: "test"
  password: "test"
  properties: "?autoReconnect=true&useSSL=false"
  # Don't touch below settings if you don't know what you're doing
  max-pool-size: 10
  connection-timeout: 30000
  cache-prep-statements: "true"
  prep-stmt-cache-size: "250"
  prep-stmt-cache-sql-limit: "2048"

redis:
  host: "127.0.0.1"
  port: 6379
  password: "your_redis_password"
  database: 0

server:
  name: "server1" # Unique name for your server
  mode: "island" # lobby or island

debug: false # Enable debug mode

```

### Contributing:

🌟 Contributions are welcome! Whether you're a developer, a writer, or just a Minecraft enthusiast, you can help by:

- Submitting pull requests
- Reporting bugs and suggesting features
- Helping with documentation and the wiki
- Sharing NewSky with others

💖 Support: If you enjoy using NewSky and want to support its development, consider starring the repository or contributing directly to the project.
