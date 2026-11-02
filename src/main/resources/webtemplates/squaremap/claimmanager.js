// DynoClaims - squaremap adapter.
// squaremap's own web UI is a bundled Vite app with no global to hook into, so this builds a
// Leaflet map straight from squaremap's tiles and reproduces its coordinate math
// (CRS.Simple + a scale derived from the world's max zoom) so the claim grid lines up with the tiles.
(function () {
    "use strict";
    const CHUNK_SIZE = 16;
    let scale = 1;

    const toLatLng = (worldX, worldZ) => L.latLng(-worldZ * scale, worldX * scale);

    DynoCore.start({
        async createMap(session) {
            const id = session.mapWorld;
            const res = await fetch(`tiles/${id}/settings.json`, { cache: "no-store" });
            if (!res.ok) {
                throw new Error(`squaremap has no map data for "${id}" (HTTP ${res.status}). ` +
                    `Has squaremap rendered this world yet? If its folder name is different, set web.squaremap-world-id in the DynoClaims config.`);
            }
            const ws = await res.json();
            scale = 1 / Math.pow(2, ws.zoom.max);

            const map = L.map("map", {
                crs: L.CRS.Simple,
                center: toLatLng(session.x, session.z),   // open on the player's position
                zoom: ws.zoom.def,
                minZoom: 0,
                maxZoom: ws.zoom.max + ws.zoom.extra,
                attributionControl: false,
                preferCanvas: true,
                noWrap: true,
            });
            L.tileLayer(`tiles/${id}/{z}/{x}_{y}.png`, {
                tileSize: 512, minNativeZoom: 0, maxNativeZoom: ws.zoom.max, noWrap: true,
            }).addTo(map);
            return map;
        },

        chunkBounds(cx, cz) {
            return [toLatLng(cx * CHUNK_SIZE, cz * CHUNK_SIZE), toLatLng((cx + 1) * CHUNK_SIZE, (cz + 1) * CHUNK_SIZE)];
        },

        worldToChunk(latlng) {
            return [Math.floor(latlng.lng / scale / CHUNK_SIZE), Math.floor(-latlng.lat / scale / CHUNK_SIZE)];
        },
    });
})();
