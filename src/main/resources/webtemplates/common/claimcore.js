// DynoClaims claim manager - shared core (used by both the Dynmap and squaremap pages).
// The map-specific file (claimmanager.js) only provides three things:
//   createMap(session)        -> a Leaflet map showing the right world
//   chunkBounds(cx, cz)       -> [corner1, corner2] in Leaflet coordinates
//   worldToChunk(latlng)      -> [cx, cz]
// and then calls DynoCore.start(platform).
(function () {
    "use strict";

    const CFG = {
        apiBase: "{{API_BASE}}",
        apiPort: parseInt("{{API_PORT}}", 10) || 12011,
        color: "{{SELECTION_COLOR}}" || "#000000",
    };

    // Blank apiBase = same host as this page + the API port, so no IP ever has to be configured.
    const API_BASE = CFG.apiBase
        ? CFG.apiBase.replace(/\/+$/, "")
        : `${location.protocol}//${location.hostname}:${CFG.apiPort}`;

    const MIXED_CONTENT = location.protocol === "https:" && API_BASE.startsWith("http:");
    const MIXED_MSG = "This page is HTTPS but the claim API is plain HTTP, so the browser blocks it. " +
        "Put the API behind HTTPS and set web.public-base-url in the DynoClaims config.";

    const STYLE_SELECTED = { color: CFG.color, weight: 1, fillColor: CFG.color, fillOpacity: 0.35 };
    const STYLE_FAILED = { color: "#c45b4d", weight: 1, fillColor: "#c45b4d", fillOpacity: 0.5 };

    const $ = (id) => document.getElementById(id);
    const key = (x, z) => `${x},${z}`;

    let P = null;                 // platform adapter
    let map = null, renderer = null, gridLayer = null, townsLayer = null, outlineLayer = null;
    let token = null, session = null;
    let mode = "toggle";
    let outlinePoints = [];
    let brushing = false, lastBrush = null;
    let busy = false;
    let ownerByChunk = new Map(); // "x,z" -> town name, for the hover label
    const selected = new Map();   // "x,z" -> Leaflet rectangle

    // ---------------------------------------------------------------- helpers

    function setStatus(msg, isError) {
        const el = $("status");
        el.textContent = msg;
        el.className = isError ? "error" : "";
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
    }

    function colorForTown(name) {
        let hash = 0;
        for (let i = 0; i < name.length; i++) { hash = (hash << 5) - hash + name.charCodeAt(i); hash |= 0; }
        return `hsl(${Math.abs(hash) % 360}, 65%, 55%)`;
    }

    // POST bodies are sent as text/plain on purpose: that is a CORS "simple request", so the
    // browser skips the extra OPTIONS preflight round-trip on every call.
    async function api(path, body) {
        const ctrl = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), 60000);
        let res;
        try {
            res = await fetch(API_BASE + path, body === undefined
                ? { signal: ctrl.signal }
                : { method: "POST", headers: { "Content-Type": "text/plain;charset=UTF-8" }, body: JSON.stringify(body), signal: ctrl.signal });
        } catch (e) {
            throw new Error(MIXED_CONTENT ? MIXED_MSG
                : `Could not reach the claim server at ${API_BASE}. Check that port ${CFG.apiPort} is open in your server's firewall and that DynoClaims is running.`);
        } finally {
            clearTimeout(timer);
        }
        let data;
        try { data = await res.json(); }
        catch (e) { throw new Error(`The claim server sent an unexpected response (HTTP ${res.status}).`); }
        if (!data.ok) throw new Error(data.error || `Request failed (HTTP ${res.status}).`);
        return data;
    }

    // ------------------------------------------------------------- selection

    function updateClaimButton() {
        const btn = $("btn-claim");
        btn.textContent = `Claim Selected (${selected.size})`;
        btn.disabled = busy || selected.size === 0 || !(session && session.canClaim);
    }

    function addChunk(cx, cz, style) {
        const k = key(cx, cz);
        if (selected.has(k)) return false;
        const rect = L.rectangle(P.chunkBounds(cx, cz), Object.assign({ interactive: false, renderer }, style || STYLE_SELECTED));
        gridLayer.addLayer(rect);
        selected.set(k, rect);
        return true;
    }

    function removeChunk(k) {
        const rect = selected.get(k);
        if (!rect) return;
        gridLayer.removeLayer(rect);
        selected.delete(k);
    }

    function clearSelection() {
        gridLayer.clearLayers();
        selected.clear();
        updateClaimButton();
    }

    function selectedList() {
        return Array.from(selected.keys()).map((k) => k.split(",").map(Number));
    }

    // Adds chunks in small batches so a big fill doesn't freeze the tab.
    function addChunksGradually(list, onDone, batchSize = 200) {
        let i = 0;
        (function step() {
            const end = Math.min(i + batchSize, list.length);
            for (; i < end; i++) addChunk(list[i][0], list[i][1]);
            updateClaimButton();
            if (i < list.length) setTimeout(step, 0); else if (onDone) onDone();
        })();
    }

    // Chunks on the straight line between two chunks (so fast brush strokes leave no gaps).
    function lineChunks(a, b) {
        let [x0, z0] = a; const [x1, z1] = b;
        if (Math.abs(x1 - x0) + Math.abs(z1 - z0) > 200) return [b];
        const out = [];
        const dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        const sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        let err = dx - dz;
        for (;;) {
            out.push([x0, z0]);
            if (x0 === x1 && z0 === z1) break;
            const e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x0 += sx; }
            if (e2 < dx) { err += dx; z0 += sz; }
        }
        return out;
    }

    function pointInPolygon(x, z, poly) {
        let inside = false;
        for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            const [xi, zi] = poly[i], [xj, zj] = poly[j];
            if (((zi > z) !== (zj > z)) && (x < (xj - xi) * (z - zi) / (zj - zi) + xi)) inside = !inside;
        }
        return inside;
    }

    // ------------------------------------------------------------ map events

    function onMapClick(e) {
        const [cx, cz] = P.worldToChunk(e.latlng);
        if (mode === "toggle") {
            const k = key(cx, cz);
            if (selected.has(k)) removeChunk(k); else addChunk(cx, cz);
            updateClaimButton();
        } else if (mode === "outline") {
            outlinePoints.push([cx, cz]);
            drawOutlinePreview();
        }
    }

    function drawOutlinePreview() {
        if (outlineLayer) { map.removeLayer(outlineLayer); outlineLayer = null; }
        if (outlinePoints.length < 2) return;
        const latlngs = outlinePoints.map(([cx, cz]) => {
            const b = P.chunkBounds(cx, cz);
            const lat1 = Array.isArray(b[0]) ? b[0][0] : b[0].lat, lng1 = Array.isArray(b[0]) ? b[0][1] : b[0].lng;
            const lat2 = Array.isArray(b[1]) ? b[1][0] : b[1].lat, lng2 = Array.isArray(b[1]) ? b[1][1] : b[1].lng;
            return [(lat1 + lat2) / 2, (lng1 + lng2) / 2];
        });
        outlineLayer = L.polyline(latlngs, { color: "#c45b4d", dashArray: "4 4" }).addTo(map);
    }

    // Brush uses pointer events so it also works with touch, and interpolates between samples.
    function paintAt(e) {
        const c = P.worldToChunk(map.mouseEventToLatLng(e));
        (lastBrush ? lineChunks(lastBrush, c) : [c]).forEach(([x, z]) => addChunk(x, z));
        lastBrush = c;
        updateClaimButton();
    }
    function stopBrush() { brushing = false; lastBrush = null; }

    function onHover(e) {
        const [cx, cz] = P.worldToChunk(e.latlng);
        const owner = ownerByChunk.get(key(cx, cz));
        $("hover").textContent = `Chunk ${cx}, ${cz}` + (owner ? ` - ${owner}` : "");
    }

    // ------------------------------------------------------------ server I/O

    // Corner coordinates [lat,lng] of a chunk, whatever shape the platform returns them in.
    function chunkCorners(cx, cz) {
        const b = P.chunkBounds(cx, cz);
        const lat1 = Array.isArray(b[0]) ? b[0][0] : b[0].lat, lng1 = Array.isArray(b[0]) ? b[0][1] : b[0].lng;
        const lat2 = Array.isArray(b[1]) ? b[1][0] : b[1].lat, lng2 = Array.isArray(b[1]) ? b[1][1] : b[1].lng;
        return { A: [lat1, lng1], B: [lat1, lng2], C: [lat2, lng2], D: [lat2, lng1] };
    }

    function injectTownStyles() {
        if ($("dyno-town-style")) return;
        const st = document.createElement("style");
        st.id = "dyno-town-style";
        st.textContent = ".town-label{background:rgba(10,12,8,.75);border:1px solid rgba(255,255,255,.35);" +
            "color:#fff;font:600 12px 'Segoe UI',system-ui,sans-serif;padding:2px 6px;box-shadow:none;" +
            "pointer-events:none}.town-label:before{display:none}";
        document.head.appendChild(st);
    }

    async function loadTownBorders() {
        try {
            const data = await api(`/api/towns?world=${encodeURIComponent(session.world)}`);
            townsLayer.clearLayers();
            ownerByChunk = new Map();

            // 1) who owns which chunk
            for (const t of data.towns) {
                for (const [z, x0, len] of t.runs) {
                    for (let i = 0; i < len; i++) ownerByChunk.set(key(x0 + i, z), t.town);
                }
            }

            for (const t of data.towns) {
                const mine = t.town === session.town;
                const color = mine ? "#7fff3f" : colorForTown(t.town);

                // 2) soft fill, one rectangle per run
                for (const [z, x0, len] of t.runs) {
                    const a = P.chunkBounds(x0, z)[0], b = P.chunkBounds(x0 + len - 1, z)[1];
                    townsLayer.addLayer(L.rectangle([a, b], {
                        stroke: false, fillColor: color, fillOpacity: mine ? 0.45 : 0.35, interactive: false, renderer
                    }));
                }

                // 3) solid outline: only the edges that touch a chunk of another town (or nobody)
                const edges = [];
                let sumX = 0, sumZ = 0, count = 0;
                for (const [z, x0, len] of t.runs) {
                    for (let i = 0; i < len; i++) {
                        const x = x0 + i;
                        sumX += x; sumZ += z; count++;
                        const own = (nx, nz) => ownerByChunk.get(key(nx, nz)) === t.town;
                        if (own(x, z - 1) && own(x, z + 1) && own(x - 1, z) && own(x + 1, z)) continue;
                        const c = chunkCorners(x, z);
                        if (!own(x, z - 1)) edges.push([c.A, c.B]);
                        if (!own(x, z + 1)) edges.push([c.D, c.C]);
                        if (!own(x - 1, z)) edges.push([c.A, c.D]);
                        if (!own(x + 1, z)) edges.push([c.B, c.C]);
                    }
                }
                townsLayer.addLayer(L.polyline(edges, { color, weight: mine ? 3 : 2, opacity: 1, interactive: false, renderer }));

                // 4) name label on the town chunk closest to the middle of the town
                const mx = sumX / count, mz = sumZ / count;
                let best = null, bestDist = Infinity;
                for (const [z, x0, len] of t.runs) {
                    const x = Math.max(x0, Math.min(x0 + len - 1, Math.round(mx)));
                    const d = (x - mx) * (x - mx) + (z - mz) * (z - mz);
                    if (d < bestDist) { bestDist = d; best = [x, z]; }
                }
                const c = chunkCorners(best[0], best[1]);
                townsLayer.addLayer(L.circleMarker([(c.A[0] + c.C[0]) / 2, (c.A[1] + c.C[1]) / 2], {
                    radius: 0, stroke: false, fill: false, interactive: false
                }).bindTooltip(t.town, { permanent: true, direction: "center", className: "town-label" }));
            }
        } catch (e) {
            setStatus(e.message, true);
        }
    }

    async function fetchOwned(list) {
        const data = await api("/api/preview", { token, world: session.world, chunks: list });
        return new Set(data.owned.map((c) => key(c[0], c[1])));
    }

    async function fillOutline() {
        if (outlinePoints.length < 3) { setStatus("Draw at least 3 points before filling.", true); return; }
        let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
        outlinePoints.forEach(([x, z]) => {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        });
        const inside = [];
        for (let x = minX; x <= maxX; x++) {
            for (let z = minZ; z <= maxZ; z++) {
                if (pointInPolygon(x + 0.5, z + 0.5, outlinePoints)) inside.push([x, z]);
            }
        }
        if (inside.length > 20000) { setStatus("That outline is too big (over 20,000 chunks). Use a smaller area.", true); return; }

        setStatus("Checking which chunks are free...");
        try {
            const owned = await fetchOwned(inside);
            const free = inside.filter(([x, z]) => !owned.has(key(x, z)));
            outlinePoints = [];
            if (outlineLayer) { map.removeLayer(outlineLayer); outlineLayer = null; }
            addChunksGradually(free, () =>
                setStatus(`Selected ${free.length} free chunk(s). ${inside.length - free.length} skipped (already owned).`));
        } catch (e) {
            setStatus(e.message, true);
        }
    }

    async function previewClaim() {
        const list = selectedList();
        try {
            const owned = await fetchOwned(list);
            owned.forEach((k) => removeChunk(k));
            updateClaimButton();

            const claimable = list.length - owned.size;
            if (claimable === 0) { setStatus("Nothing to claim - those chunks are already owned.", true); return; }

            $("confirm-claimable").textContent = claimable;
            $("confirm-owned").textContent = owned.size;
            $("confirm-note").textContent = claimable > session.maxChunks
                ? `Large selection: it will be claimed in ${Math.ceil(claimable / session.maxChunks)} steps.` : "";
            $("confirm-box").style.display = "block";
        } catch (e) {
            setStatus(e.message, true);
        }
    }

    async function confirmClaim() {
        $("confirm-box").style.display = "none";
        busy = true; updateClaimButton();

        let remaining = selectedList();
        let claimedTotal = 0, reasons = [];
        const max = session.maxChunks;

        try {
            // Big blobs have to grow outward from the town, and the server only sees one batch at a
            // time, so retry whatever failed for a couple of rounds while progress is still being made.
            for (let round = 0; round < 3 && remaining.length > 0; round++) {
                const failed = [];
                reasons = [];
                for (let i = 0; i < remaining.length; i += max) {
                    setStatus(`Claiming... ${claimedTotal} done`);
                    const d = await api("/api/claim", { token, world: session.world, chunks: remaining.slice(i, i + max) });
                    claimedTotal += d.claimed;
                    failed.push(...d.failedChunks);
                    reasons = reasons.concat(d.reasons || []);
                }
                const progressed = failed.length < remaining.length;
                remaining = failed;
                if (!progressed) break;
            }
        } catch (e) {
            setStatus(e.message, true);
        }

        clearSelection();
        remaining.forEach(([x, z]) => addChunk(x, z, STYLE_FAILED));
        busy = false; updateClaimButton();

        if (remaining.length === 0) {
            setStatus(`Claimed ${claimedTotal} chunk(s).`);
        } else {
            const why = [...new Set(reasons.map((r) => r.reason))].slice(0, 3).join(" | ");
            setStatus(`Claimed ${claimedTotal}. ${remaining.length} could not be claimed (shown in red)${why ? ": " + why : "."}`, true);
        }
        loadTownBorders();
    }

    // ------------------------------------------------------------------ modes

    function setMode(newMode) {
        mode = newMode;
        ["outline", "toggle", "brush"].forEach((m) => $("btn-" + m).classList.toggle("active", m === newMode));
        if (map) {
            if (newMode === "brush") map.dragging.disable(); else map.dragging.enable();
            map.getContainer().style.touchAction = newMode === "brush" ? "none" : "";
        }
    }

    function wireButtons() {
        $("btn-outline").addEventListener("click", () => {
            outlinePoints = [];
            if (outlineLayer) { map.removeLayer(outlineLayer); outlineLayer = null; }
            setMode("outline");
            setStatus("Click points on the map to draw an outline, then press Fill Inside.");
        });
        $("btn-fill").addEventListener("click", fillOutline);
        $("btn-toggle").addEventListener("click", () => { setMode("toggle"); setStatus("Click chunks to select them. Click a selected chunk to deselect it."); });
        $("btn-brush").addEventListener("click", () => { setMode("brush"); setStatus("Click and drag to paint chunks."); });
        $("btn-clear").addEventListener("click", () => {
            clearSelection();
            outlinePoints = [];
            if (outlineLayer) { map.removeLayer(outlineLayer); outlineLayer = null; }
            setStatus("Selection cleared.");
        });
        $("btn-claim").addEventListener("click", previewClaim);
        $("confirm-cancel").addEventListener("click", () => { $("confirm-box").style.display = "none"; });
        $("confirm-go").addEventListener("click", confirmClaim);
    }

    // ------------------------------------------------------------------ start

    async function start(platform) {
        P = platform;
        try {
            if (MIXED_CONTENT) throw new Error(MIXED_MSG);

            token = new URLSearchParams(location.search).get("token");
            if (!token) throw new Error("This link has no token. Run /webclaim in-game to get a fresh link.");

            session = await api(`/api/session?token=${encodeURIComponent(token)}`);
            $("hud-welcome").innerHTML = `Welcome, <span class="you">${escapeHtml(session.player)}</span>`;
            $("hud-town").textContent = `Town: ${session.town}  \u2022  Nation: ${session.nation || "none"}`;
            $("hud-world").textContent = `World: ${session.world}`;

            injectTownStyles();
            map = await P.createMap(session);
            renderer = L.canvas ? L.canvas({ padding: 0.5 }) : undefined;
            townsLayer = L.layerGroup().addTo(map);
            gridLayer = L.layerGroup().addTo(map);

            map.on("click", onMapClick);
            map.on("mousemove", onHover);
            const el = map.getContainer();
            el.addEventListener("pointerdown", (e) => {
                if (mode !== "brush" || e.button !== 0) return;
                brushing = true; lastBrush = null; paintAt(e);
            });
            el.addEventListener("pointermove", (e) => { if (brushing) paintAt(e); });
            window.addEventListener("pointerup", stopBrush);
            window.addEventListener("pointercancel", stopBrush);

            setMode(mode);
            updateClaimButton();
            await loadTownBorders();
            setInterval(() => { if (!document.hidden && !busy) loadTownBorders(); }, 60000);

            if (!session.canClaim) {
                setStatus("You can look around, but you don't have permission to claim land for your town.", true);
            } else {
                setStatus("Ready. Draw an outline, brush, or click chunks to select them.");
            }
        } catch (e) {
            setStatus(e.message, true);
        }
    }

    wireButtons();
    window.DynoCore = { start, fail: (msg) => setStatus(msg, true) };
})();