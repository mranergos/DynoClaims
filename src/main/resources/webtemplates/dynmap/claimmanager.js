// DynoClaims - Dynmap adapter. Dynmap builds its own Leaflet map; we hook into it (see claimmanager.html).
const MAP_SCALE = 8;
const CHUNK_SIZE = 16;

function DynoClaimsInit(dynmapSelf) {
    if (!dynmapSelf.map) {
        DynoCore.fail("Could not access the map. Refresh the page.");
        return;
    }
    const realMap = dynmapSelf.map;

    DynoCore.start({
        async createMap() { return realMap; },

        chunkBounds(cx, cz) {
            const realCz = cz + 2;
            const x1 = (cx * CHUNK_SIZE) / MAP_SCALE, x2 = ((cx + 1) * CHUNK_SIZE) / MAP_SCALE;
            const z1 = (realCz * CHUNK_SIZE) / MAP_SCALE, z2 = ((realCz + 1) * CHUNK_SIZE) / MAP_SCALE;
            return [[-z1, x1], [-z2, x2]];
        },

        worldToChunk(latlng) {
            const worldX = latlng.lng * MAP_SCALE;
            const worldZ = -latlng.lat * MAP_SCALE;
            return [Math.floor(worldX / CHUNK_SIZE), Math.floor(worldZ / CHUNK_SIZE) - 2];
        },
    });
}
