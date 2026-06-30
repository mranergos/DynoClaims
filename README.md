# DynoClaims

DynoClaims lets your Towny players claim land for their town straight from your Dynmap web map, instead of walking to every chunk in-game and running `/town claim` one at a time.

A player runs a single command, gets a link to a claim page, and from there can click, drag-paint, or draw-and-fill an outline to select chunks. The page shows a live preview (how many chunks are claimable, how many are rejected and why) before anything is actually claimed, so nothing happens by surprise.

## Features

- **One command to start** — `/webclaim` generates a secure, short-lived link tied to the player who ran it
- **Real Dynmap map** — the claim page embeds your actual Dynmap renderer, not a separate or fake map
- **Click, brush, or outline-fill selection** — pick chunks one at a time, drag to paint several at once, or draw a rough outline and let it fill the unclaimed chunks inside
- **Preview before claiming** — see exactly how many chunks will succeed and how many will be rejected (and why) before confirming
- **Multi-pass claiming** — large connected selections claim correctly in one go, growing outward from the existing town the same way Towny's own adjacency rules expect
- **Built on Towny's real API** — no shortcuts or guesswork; claim logic mirrors Towny's own internal claiming behavior

## Requirements

- A Paper (or Paper-based) Minecraft server, version 1.21.x
- [Towny](https://github.com/TownyAdvanced/Towny) installed and working
- [Dynmap](https://github.com/webbukkit/dynmap) installed and working
- Java 21

## Installation
or if you want checkout the video https://youtu.be/_svDzJNKIbg

1. Download the latest `DynoClaims.jar` from Releases (or build it yourself — see below) and drop it into your server's `plugins/` folder.
2. Copy `claimmanager.html` and `claimmanager.js` from this repo's `web/` folder into `plugins/dynmap/web/` on your server.
3. Open `plugins/dynmap/web/claimmanager.js` and edit these two lines near the top to match your server:
   ```js
   const API_BASE = "http://yourserver:8124"; // your server's address, plugin API port
   const WORLD_NAME = "yourworldname";        // the world your towns are claimed in
   ```
4. Start (or restart) your server. You should see a console line like:
   ```
   [DynoClaims] DynoClaims web API listening on port 8124
   ```
5. In-game, as a player who already owns a town, run:
   ```
   /webclaim
   ```
   Click the `[ CLICK HERE TO COPY LINK ]` button in chat, then paste the link into a browser.

## Configuration

`plugins/DynoClaims/config.yml`:

```yaml
web:
  port: 8124                  # port the plugin's own API listens on
  token-ttl-seconds: 300      # how long a /webclaim link stays valid
  allowed-origin: "*"         # CORS origin allowed to call the API
  public-base-url: "http://yourserver:8123"  # where claimmanager.html is hosted (your Dynmap port)
```

## Building from source

```bash
git clone https://github.com/yourname/DynoClaims.git
cd DynoClaims
./gradlew shadowJar
```

The built jar will be in `build/libs/DynoClaims-<version>.jar`.

## How it works

Dynmap's web page has no concept of which Minecraft player is viewing it — it's just a public, anonymous map. DynoClaims bridges that gap with a short-lived token: `/webclaim` generates one tied to the player's identity, and the claim page sends that token back to a small API the plugin runs alongside the server. The API checks the token, looks up the player's town through Towny's API, and only then allows claim actions — all the actual claiming happens through Towny's own logic, not a reimplementation of it.

## Known limitations

- Only supports Towny for now. Towns & Nations and Lands plugin support are planned but not yet implemented.
- The claim page's coordinate math is tuned to a specific Dynmap tile/projection setup; if tiles or chunk alignment look off on your server, the scale/offset constants near the top of `claimmanager.js` may need adjusting for your map's configuration.
- Claim cost/economy integration is not yet wired up (currently always reports a cost of 0).
