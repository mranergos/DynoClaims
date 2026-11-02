# DynoClaims

DynoClaims lets your Towny players claim land for their town straight from your **Dynmap** or **squaremap** web map, instead of walking to every chunk in-game and running `/town claim` one at a time.

A player runs one command, gets a link, and from there can click, drag-paint, or draw-and-fill an outline to select chunks. The page checks which chunks are already owned before anything is claimed, so nothing happens by surprise.

Video walkthrough: https://youtu.be/2-SuUIFFxZA

## Features

- **One command to start**: `/webclaim` gives a clickable link tied to the player who ran it. It stays active while they use it and replaces any older link.
- **Dynmap and squaremap**: the claim page is installed automatically into whichever map plugin(s) you have.
- **Three ways to select**: click chunks (click again to deselect), brush-paint by dragging (works on touch screens too), or draw an outline and fill it.
- **Towns are clearly visible**: every town is drawn with a fill, a solid border and a name label. Your own town is highlighted in bright green.
- **Check before claiming**: chunks that are already owned are dropped from your selection before you confirm.
- **Smart claiming**: big connected selections grow outward from your existing town, the way Towny's adjacency rules expect. Selections larger than the per-request limit are split automatically.
- **Clear errors**: chunks that can't be claimed turn red, and the page tells you why (for example "not adjacent to your town").
- **Near-zero config**: the server address, map port, world and squaremap world ID are detected automatically.
- **Setup check**: `/dynoclaimreload status` tells you what was found and what's missing.

## Requirements

- A Paper (or Paper-based) server, 1.21.x
- [Towny](https://github.com/TownyAdvanced/Towny)
- [Dynmap](https://github.com/webbukkit/dynmap) and/or [squaremap](https://github.com/jpenilla/squaremap)
- Java 21

## Installation

1. Put `DynoClaims-<version>.jar` in your server's `plugins/` folder (see **Building** below if you need to make it yourself).
2. If you are upgrading from an older version, **delete the old `plugins/DynoClaims/config.yml`** so a fresh one is generated. Old configs contain hard-coded addresses that override auto-detection. You do **not** need to copy any web files by hand any more.
3. Start the server.
4. Open the API port (default **12011**, TCP) in your firewall or host panel.
5. Run `/dynoclaimreload status` in the console. You should see that the API is listening, your map plugin was found, and the claim page was installed.

To test the port, open `http://YOUR-SERVER:12011/api/ping` in a browser. You should see `{"ok":true,...}`.

## Using it

1. Be a member of a town, with permission to claim (see **Permissions**).
2. Run `/webclaim` and click **[ OPEN CLAIM MAP ]**.
3. Choose chunks:
   - **Toggle Chunk**: click to select, click again to deselect
   - **Brush Select**: hold and drag to paint
   - **Draw Outline**, then **Fill Inside**: click the corners of a shape, then fill it
4. Click **Claim Selected** and confirm.

Chunks shown in red could not be claimed. The message at the top of the page gives the reasons.

## Commands and permissions

| Command | Permission | Default | What it does |
|---|---|---|---|
| `/webclaim` | `dynoclaims.use` | everyone | Get your claim-map link |
| `/dynoclaimreload` | `dynoclaims.admin` | op | Reload config, restart the API, redeploy web files, show status |
| `/dynoclaimreload status` | `dynoclaims.admin` | op | Show status only |

Who may claim from the web is controlled by `claims.permission` in the config (default `towny.command.town.claim`). Leave it blank to allow every resident of a town.

## Configuration

`plugins/DynoClaims/config.yml`. For most servers you only need the API port open and can leave everything else alone.

```yaml
web:
  port: 12011                  # port the claim API listens on (must be reachable by players)
  bind-address: "0.0.0.0"
  token-ttl-seconds: 300       # how long a link stays valid after you last used it
  map-platform: auto           # auto, dynmap or squaremap (which one /webclaim links to)

  # Optional. Leave blank to auto-detect.
  public-host: ""              # your domain or IP, no http://. Blank = address the player joined with
  web-base-url: ""             # full URL of your map site, e.g. "https://map.example.com"
  public-base-url: ""          # full URL of the claim API, only needed behind a reverse proxy / HTTPS
  world-name: ""               # blank = the world the player is standing in
  squaremap-world-id: ""       # blank = worked out from the world

  selection-color: "#000000"   # colour of your selected chunks (try "#ffcc00" on dark maps)
  allowed-origin: "*"          # which websites may call the API

claims:
  max-chunks-per-request: 400  # the web page splits bigger selections automatically
  permission: "towny.command.town.claim"   # blank = all town residents may claim
```

After changing the config, run `/dynoclaimreload`.

### HTTPS / reverse proxy

Browsers block a plain-HTTP API when the map page is served over HTTPS. If your map is on HTTPS, put the claim API behind your reverse proxy and set `web.public-base-url` (and `web.web-base-url` if needed), for example:

```yaml
web:
  web-base-url: "https://map.example.com"
  public-base-url: "https://map.example.com/dynoclaims"
```

## Troubleshooting

| Problem | Fix |
|---|---|
| "Could not reach the claim server" | Port 12011 isn't open, or the API didn't start. Check the console for a red error; if the port is in use, change `web.port` and run `/dynoclaimreload`. |
| `/webclaim` link has the wrong address | Set `web.public-host` to your domain or IP. |
| Map page doesn't load | Set `web.web-base-url` to your map's full URL, e.g. `http://yourip:8080`. |
| squaremap says "no map data" | Make sure squaremap has rendered the world, then set `web.squaremap-world-id` to the folder name under `plugins/squaremap/web/tiles/`. |
| "web folder not found" in status | Let Dynmap/squaremap finish starting once, then run `/dynoclaimreload`. |
| Players can't claim | Give them the `claims.permission` node, or blank it out. |
| Page looks outdated after an update | Hard-refresh with Ctrl+F5. |

## Building from source

Requires **JDK 21**. Newer JDKs (for example 25) can break the Kotlin compiler used here, so point Gradle at Java 21.

```bash
git clone https://github.com/yourname/DynoClaims.git
cd DynoClaims
./gradlew build
```

The jar will be at `build/libs/DynoClaims-<version>.jar`.

## How it works

A public map page has no idea which Minecraft player is looking at it. DynoClaims bridges that with a short-lived token: `/webclaim` creates one tied to the player, and the claim page sends it to a small API the plugin runs next to your server. The API checks the token, finds the player's town through Towny's API, and only then allows claim actions. All Towny and Bukkit calls run on the server's main thread.

On startup (and on `/dynoclaimreload`) the plugin copies the claim page into each installed map plugin's `web/` folder, filling in your settings. The page opens on the world the player is standing in, draws existing towns from `/api/towns`, and talks to `/api/session`, `/api/preview` and `/api/claim`.

## Known limitations

- **Towny only.** Towns & Nations and Lands are not supported yet.
- **No economy integration.** Claims made from the web do not charge Towny's claim cost; the confirm step always reports a cost of 0.
- **Claims are created directly** as Towny town blocks after Towny's own claim test passes. They do not go through Towny's full `/town claim` flow, so other plugins that listen to Towny claim events may not see them.
- **Dynmap alignment.** The Dynmap page's coordinate math is tuned to the default Dynmap projection (see `MAP_SCALE` and the chunk offset in `webtemplates/dynmap/claimmanager.js`). If the grid looks shifted on your map, those constants may need adjusting.
- **Plain HTTP by default.** Use a reverse proxy for HTTPS (see above).
