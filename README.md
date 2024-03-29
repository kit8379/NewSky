<h1 align="center"><img height="35" src="https://cdn3.emoji.gg/emojis/9330-minecraftcube.gif"> NewSky - A Revolutionary Skyblock Experience</h1>
<div align="center">

</div>

<h2 align="center">⚠️ Important Notice ⚠️</h2>
<h3 align="center">NewSky is currently in ALPHA stage and not recommended for production use. A stable release is expected around June. Please wait a few more months for a more polished version.</h3>

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
/

------------

### To-DO List
1. Remove the dependency of using Multiverse-Core and VoidGen.
2. Allow to store world in common share path or distributed storage system so as to load island dynamically across servers.
   (The create island feature has already implemented to dynamically create on across servers already. But the island world is currently stay in that server only. It is not hard to add this function but I have no time to implement it in recent months.)
3. Add support to using AdvancedSlimeWorldManager instead of normal Bukkit world.
4. Implement command permission and finalise the display messages that can be customized in messages.yml

------------

### Installation:
1. Download the latest version of NewSky from the [releases page](https://github.com/kit8379/NewSky/releases/tag/alpha).
2. Place the NewSky.jar file into your server's `plugins` folder.
3. Restart your server. NewSky will generate a default configuration file.
4. Configure the plugin to your needs.

### Default Configuration:

```yaml
# The settings for the plugin.
# The plugin uses MySQL to store the island data.
DB:
   host: "localhost"
   port: 3306
   database: "newsky"
   username: "root"
   password: "password"
   properties: "?autoReconnect=true&useSSL=false"

   # The settings for the HikariCP connection pool.
   # Usually no need to change these values.
   max-pool-size: 10
   connection-timeout: 30000
   cache-prep-statements: "true"
   prep-stmt-cache-size: 250
   prep-stmt-cache-sql-limit: 2048

# The settings for the Redis server.
# The plugin uses Redis to communicate between servers in the network.
redis:
   host: "localhost"
   port: 6379
   password: ""
   database: 0

# Whether the plugin is in debug mode. More detailed logs will be printed if this is set to true.
debug: false

# The settings for the server.
server:
   # The name of the server. Must be equal to the server name in the BungeeCord / Velocity config.
   name: "server1"
   # Whether the server is the lobby server.
   # If the server is the lobby server, the plugin will not load islands on this server.
   lobby: false
   # The interval that the server send heartbeat to the each other server.
   # Usually no need to change this value. The default value is 5 seconds.
   heartbeat-interval: 5

   # Below are the settings for the world-loading mode.
   # The world-loading-mode is used to determine how the plugin should load and unload islands.
   # The plugin supports two modes: "normal" and "slime".
   world-loading-mode:
      type: "normal"

      # The settings for the normal world-loading mode.
      # This mode uses the bukkit world management system to load and unload islands.
      # Only required to be set if the world-loading-mode is set to "normal".
      normal:
         # Options: "static", "dynamic"
         # Static mode will load all islands at startup and keep them loaded in the same server
         # Dynamic mode will load and unload islands from the shared storage directory
         mode: "dynamic"
         # Path to the directory where the islands are stored
         # This path must be accessible by all servers in the network
         # Only required to be set if the above mode is set to "dynamic"
         storage-path: "/path/to/storage"

      # The settings for the slime world-loading mode.
      # This mode uses the AdvancedSlimeWorldManager to load and unload islands.
      # Only required to be set if the world-loading-mode is set to "slime".
      slime:
         data-source: "mysql" # Options: "mysql" or "mongodb"

# The settings for the island.
island:
   # The template world name of the new island.
   # The plugin will create a new island based on this template.
   # The template world store in the template folder in the plugin  folder.
   template: "skyblock"

   # The max size of the island. 100 means the island is 100x100.
   size: 100

   # The border buffer between islands. 10 means the border is 10 blocks away from the island.
   # This helps to prevent players have negative experience when they are near the end of the island area.
   # Set it to 0 if you don't want any buffer. The border will be exactly at the edge of the island.
   buffer: 10

   # The default spawn point of the island.
   spawn:
      x: 0
      y: 132
      z: 0
      yaw: 100
      pitch: 100


messages:
   debug-prefix: '[Debug] '
   cannot-leave-island-boundary: '&cYou cannot leave the island boundary!'
   island-not-found-in-server: '&cIsland not found in this server.'
   no-active-server: '&cNo active server available.'
   island-not-loaded: '&cIsland is not loaded.'
   island-already-loaded: '&cIsland is already loaded.'
   cannot-edit-island: '&cYou cannot edit this island.'
   island-pvp-disabled: '&cIsland PvP is disabled.'
   island-member-exists: '&c{name} is already a member of this island.'
   not-island-member: '&c{name} is not a member of this island.'
   only-player-can-run-command: '&cOnly players can run this command.'
   no-island: '&c{name} does not have an island.'
   no-owner: '&cThis island has no owner.'
   island-id: '&aIsland ID: {island}'
   island-owner: '&aIsland Owner: {owner}'
   island-members: '&aIsland Members: {members}'
   island-load-success: '&aSuccessfully loaded {name}''s island.'
   island-unload-success: '&aSuccessfully unloaded {name}''s island.'
   plugin-reloaded: '&aPlugin reloaded successfully.'
   admin-command-help: '&eUse /islandadmin <subcommand> for admin commands.'
   admin-unknown-sub-command: '&cUnknown subcommand: {subCommand}'
   admin-add-member-usage: '&b/islandadmin addmember <island> <player>'
   admin-remove-member-usage: '&b/islandadmin removemember <island> <player>'
   admin-create-island-usage: '&b/islandadmin create <player>'
   admin-delete-island-usage: '&b/islandadmin delete <player>'
   admin-set-home-usage: '&b/islandadmin sethome <island> <homeName>'
   admin-delete-home-usage: '&b/islandadmin delhome <island> <homeName>'
   admin-home-usage: '&b/islandadmin home <island> <homeName>'
   admin-set-warps-usage: '&b/islandadmin setwarp <island> <warpName>'
   admin-delete-warp-usage: '&b/islandadmin delwarp <island> <warpName>'
   admin-warp-usage: '&b/islandadmin warp <island> <warpName>'
   admin-info-usage: '&b/islandadmin info <island>'
   admin-load-usage: '&b/islandadmin load <island>'
   admin-unload-usage: '&b/islandadmin unload <island>'
   admin-reload-usage: '&b/islandadmin reload'
   admin-lock-usage: '&b/islandadmin lock <island>'
   admin-pvp-usage: '&b/islandadmin pvp <island>'
   admin-set-owner-usage: '&b/islandadmin setowner <island> <player>'
   admin-no-island: '&c{name} does not have an island.'
   admin-already-has-island: '&c{name} already has an island.'
   admin-add-member-success: '&aSuccessfully added {member} to {name}''s island.'
   admin-remove-member-success: '&aSuccessfully removed {member} from {name}''s island.'
   admin-create-island-success: '&aSuccessfully created an island for {name}.'
   admin-delete-warning: '&cWarning: This will permanently delete {name}''s island! Type the command again within 10 seconds to confirm.'
   admin-delete-island-success: '&aSuccessfully deleted {name}''s island.'
   admin-cannot-delete-default-home: '&cCannot delete the default home of {name}''s island.'
   admin-no-home: '&cNo home named {home} found for {name}''s island.'
   home-success: '&aSuccessfully teleported to home {home}.'
   admin-delete-home-success: '&aSuccessfully deleted home {home} for {name}''s island.'
   admin-no-warp: '&cNo warp named {warp} found for {name}''s island.'
   admin-delete-warp-success: '&aSuccessfully deleted warp {warp} for {name}''s island.'
   admin-lock-success: '&aSuccessfully locked {name}''s island.'
   admin-unlock-success: '&aSuccessfully unlocked {name}''s island.'
   admin-must-in-island-set-home: '&cYou must be in {name}''s island to set a home.'
   admin-must-in-island-set-warp: '&cYou must be in {name}''s island to set a warp.'
   admin-set-home-success: '&aSuccessfully set home {home} for {name}''s island.'
   admin-set-warp-success: '&aSuccessfully set warp {warp} for {name}''s island.'
   admin-pvp-enable-success: '&aSuccessfully enabled PvP for {name}''s island.'
   admin-pvp-disable-success: '&aSuccessfully disabled PvP for {name}''s island.'
   admin-set-owner-success: '&aSuccessfully set {name} as the owner of {owner}''s island.'
   admin-already-owner: '&c{name} is already the owner of the island.'
   player-command-help: '&eUse /island <subcommand> for island commands.'
   player-unknown-sub-command: '&cUnknown subcommand: {subCommand}'
   player-add-member-usage: '&b/island addmember <player>'
   player-remove-member-usage: '&b/island removemember <player>'
   player-create-island-usage: '&b/island create'
   player-delete-island-usage: '&b/island delete'
   player-home-usage: '&b/island home [homeName]'
   player-set-home-usage: '&b/island sethome <homeName>'
   player-delete-home-usage: '&b/island delhome <homeName>'
   player-warp-usage: '&b/island warp [warpName]'
   player-set-warp-usage: '&b/island setwarp <warpName>'
   player-delete-warp-usage: '&b/island delwarp <warpName>'
   player-info-usage: '&b/island info'
   player-lock-usage: '&b/island lock'
   player-pvp-usage: '&b/island pvp'
   player-set-owner-usage: '&b/island setowner <player>'
   player-leave-usage: '&b/island leave'
   player-no-island: '&cYou do not have an island.'
   player-already-has-island: '&cYou already have an island.'
   player-must-in-island-set-home: '&cYou must be in your island to set a home.'
   player-must-in-island-set-warp: '&cYou must be in your island to set a warp.'
   player-add-member-success: '&aSuccessfully added {member} to your island.'
   player-remove-member-success: '&aSuccessfully removed {member} from your island.'
   player-remove-member-cannot-remove-self: '&cYou cannot remove yourself from the island.'
   player-create-island-success: '&aSuccessfully created your island.'
   player-delete-warning: '&cWarning: This will permanently delete your island! Type the command again within 10 seconds to confirm.'
   player-delete-island-success: '&aSuccessfully deleted your island.'
   player-teleport-to-island-success: '&aSuccessfully teleported to your island.'
   player-set-home-success: '&aSuccessfully set home {home} on your island.'
   player-set-warp-success: '&aSuccessfully set warp {warp} on your island.'
   player-delete-home-success: '&aSuccessfully deleted home {home} from your island.'
   player-cannot-delete-default-home: '&cYou cannot delete the default home of your island.'
   player-delete-warp-success: '&aSuccessfully deleted warp {warp} from your island.'
   player-pvp-enable-success: '&aSuccessfully enabled PvP on your island.'
   player-pvp-disable-success: '&aSuccessfully disabled PvP on your island.'
   player-unlock-success: '&aSuccessfully unlocked your island.'
   player-lock-success: '&aSuccessfully locked your island.'
   player-no-home: '&cNo home named {home} found on your island.'
   player-no-warp: '&cNo warp named {warp} found on your island.'
   player-home-success: '&aSuccessfully teleported to home {home} on your island.'
   player-not-owner: '&cYou are not the owner of this island.'
   warp-success: '&aSuccessfully teleported to warp {warp}.'
   no-warp: '&cNo warp named {warp} found for {name}''s island.'
   island-locked: '&cThis island is locked.'
   player-set-owner-success: '&aSuccessfully set {name} as the owner of your island.'
   player-already-owner: '&c{name} is already the owner of the island.'
   player-cannot-leave-as-owner: '&cYou cannot leave the island as the owner. Please transfer ownership first.'
   player-leave-success: '&aSuccessfully left the island.'
```

### Contributing:

🌟 Contributions are welcome! Whether you're a developer, a writer, or just a Minecraft enthusiast, you can help by:

- Submitting pull requests
- Reporting bugs and suggesting features
- Helping with documentation and the wiki
- Sharing NewSky with others

💖 Support: If you enjoy using NewSky and want to support its development, consider starring the repository or contributing directly to the project.
