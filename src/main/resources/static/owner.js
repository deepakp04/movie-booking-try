const API_BASE = '/api/owner';

document.addEventListener('DOMContentLoaded', () => {
    if (checkOwnerAuth()) {
        loadMyTheatre();
        loadMyScreens();
        loadMovieOptions();
        loadMyShows();
    }
});

function checkOwnerAuth() {
    const token = localStorage.getItem('accessToken');
    if (!token) {
        window.location.href = '/auth.html';
        return false;
    }

    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const role = payload.role || payload.roles || '';

        if (!role.includes('THEATRE_OWNER') && !role.includes('ADMIN')) {
            alert('Access Denied. Theatre owner privileges required.');
            handleLogout();
            return false;
        }

        const emailDisplay = document.getElementById('ownerEmailDisplay');
        if (emailDisplay && payload.sub) {
            emailDisplay.innerText = payload.sub;
        }
        return true;
    } catch (e) {
        console.error('Invalid token format:', e);
        handleLogout();
        return false;
    }
}

async function ownerApiCall(endpoint, method = 'GET', body = null) {
    hideAlert();
    const token = localStorage.getItem('accessToken');
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };

    try {
        const response = await fetch(`${API_BASE}${endpoint}`, {
            method,
            headers,
            body: body ? JSON.stringify(body) : null
        });

        if (response.status === 401 || response.status === 403) {
            showAlert('Session expired or unauthorized. Please log in again.', 'error');
            setTimeout(handleLogout, 1500);
            return null;
        }

        const result = await response.json();

        if (!response.ok || (result.success !== undefined && !result.success)) {
            const errorMsg = result.message || result.error || `HTTP ${response.status} Error`;
            showAlert(errorMsg, 'error');
            throw new Error(errorMsg);
        }

        return result;
    } catch (err) {
        if (err.message && err.message !== 'Failed to fetch') throw err;
        showAlert('Network error. Please check your connection.', 'error');
        throw err;
    }
}

function switchTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.add('hidden'));
    document.querySelectorAll('.nav-tab').forEach(el => el.classList.remove('active'));

    const target = document.getElementById(tabId);
    if (target) target.classList.remove('hidden');
    if (event && event.currentTarget) event.currentTarget.classList.add('active');
}

function showAlert(message, type = 'error') {
    const alertBox = document.getElementById('ownerAlert');
    if (!alertBox) return;
    alertBox.className = `alert alert-${type}`;
    alertBox.textContent = message;
    alertBox.classList.remove('hidden');
}

function hideAlert() {
    const alertBox = document.getElementById('ownerAlert');
    if (alertBox) alertBox.classList.add('hidden');
}

function handleLogout() {
    localStorage.clear();
    window.location.href = '/auth.html';
}

// --- MY THEATRE ---
async function loadMyTheatre() {
    try {
        const res = await ownerApiCall('/theatre');
        if (!res) return;
        const t = res.data;
        const card = document.getElementById('theatreInfoCard');
        card.innerHTML = `
            <h3 style="color: var(--primary-gold);">${t.name}</h3>
            <p style="color: var(--text-muted); margin-top: 4px;">${t.address}, ${t.cityName}</p>
            <p style="margin-top: 8px; font-size: 0.85rem; color: var(--text-muted);">
                ${t.screens ? t.screens.length : 0} screen(s) configured.
            </p>
        `;
    } catch (_) {
        const card = document.getElementById('theatreInfoCard');
        if (card) card.innerHTML = `<p style="color: var(--text-muted);">No theatre is currently assigned to your account. Contact an administrator.</p>`;
    }
}

// --- SCREENS ---
async function loadMyScreens() {
    try {
        const res = await ownerApiCall('/screens');
        if (res && res.data) mtPopulateScreens(res.data);
        if (!res) return;
        const tbody = document.getElementById('screensTableBody');
        const screenSelect = document.getElementById('showScreenSelect');
        tbody.innerHTML = '';
        if (screenSelect) screenSelect.innerHTML = '<option value="" disabled selected>Select Screen</option>';

        res.data.forEach(s => {
            tbody.innerHTML += `
                <tr>
                    <td>${s.id}</td>
                    <td>${s.name}</td>
                    <td>${s.totalSeats > 0 ? s.totalSeats : 'layout not drawn'}</td>
                </tr>
            `;
            if (screenSelect) {
                screenSelect.innerHTML += `<option value="${s.id}">${s.name} (${s.totalSeats} seats)</option>`;
            }
        });
    } catch (_) {}
}

document.getElementById('addScreenForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('screenName').value.trim();

    try {
        await ownerApiCall('/screens', 'POST', { name, totalSeats: 0 });
        showAlert('Screen added successfully!', 'success');
        document.getElementById('addScreenForm').reset();
        loadMyScreens();
        loadMyTheatre();
    } catch (_) {}
});

// --- MOVIES (read-only dropdown source) ---
async function loadMovieOptions() {
    try {
        const res = await ownerApiCall('/movies');
        if (!res) return;
        const movieSelect = document.getElementById('showMovieSelect');
        if (!movieSelect) return;
        movieSelect.innerHTML = '<option value="" disabled selected>Select Movie</option>';
        res.data.forEach(m => {
            movieSelect.innerHTML += `<option value="${m.id}">${m.title} (${m.cbfcRating})</option>`;
        });
    } catch (_) {}
}

// --- SHOWS ---
async function loadMyShows() {
    try {
        const scopeEl = document.getElementById('showScopeSelect');
        const scope = scopeEl ? scopeEl.value : 'upcoming';
        const res = await ownerApiCall(`/shows?scope=${scope}`);
        if (!res) return;
        const tbody = document.getElementById('showsTableBody');
        tbody.innerHTML = '';
        
        const now = new Date();

        res.data.forEach(s => {
            const showDateTime = s.startTime ? new Date(s.startTime) : null;
            if (!showDateTime) return;
            
            // Skip past shows for upcoming scope - they should only appear in "past" scope
            if (scope === 'upcoming' && showDateTime <= now) {
                return;
            }
            
            // Skip future shows for past scope
            if (scope === 'past' && showDateTime > now) {
                return;
            }

            const start = showDateTime.toLocaleString();
            const isPast = showDateTime <= now;
            
            tbody.innerHTML += `
                <tr>
                    <td>${s.movieTitle || ''}</td>
                    <td>${s.screenName || ''}</td>
                    <td>${start}</td>
                    <td>${s.format || ''}</td>
                    <td>₹${s.basePrice}</td>
                    <td>
                        ${isPast ? '<span style="color: var(--text-muted);">Completed</span>' 
                                 : `<button class="btn-danger-sm" onclick="cancelShow(${s.id})">Cancel</button>`}
                    </td>
                </tr>
            `;
        });
    } catch (_) {}
}

document.getElementById('addShowForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const screenId = parseInt(document.getElementById('showScreenSelect').value, 10);
    const movieId = parseInt(document.getElementById('showMovieSelect').value, 10);
    const language = document.getElementById('showLanguage').value;
    const format = document.getElementById('showFormat').value;
    const startTime = document.getElementById('showStartTime').value;
    const basePrice = parseFloat(document.getElementById('showPrice').value);
    const hasCaptions = document.getElementById('showCaptions').checked;

    if (!screenId || !movieId) {
        showAlert('Please select both a screen and a movie.', 'error');
        return;
    }
    const tierPrices = collectTierPrices();
    if (showTiers.length > 0 && tierPrices.length !== showTiers.length) {
        showAlert('Enter a price for every seat tier on this screen.', 'error');
        return;
    }
    if (showTiers.length === 0 && isNaN(basePrice)) {
        showAlert('Enter a ticket price.', 'error');
        return;
    }
    
    // Parse reserved seat codes (comma-separated)
    const payload = {
        screenId, movieId, language, format, startTime, hasCaptions,
        basePrice: isNaN(basePrice) ? null : basePrice,
        tierPrices: collectTierPrices()
    };
    const reservedSeatsInput = document.getElementById('showReservedSeats').value.trim();
    if (reservedSeatsInput) {
        const reservedSeatCodes = reservedSeatsInput
            .split(',')
            .map(s => s.trim())
            .filter(s => s.length > 0);
        if (reservedSeatCodes.length > 0) {
            payload.reservedSeatCodes = reservedSeatCodes;
        }
    }

    try {
        await ownerApiCall('/shows', 'POST', payload);
        showAlert('Show scheduled successfully!', 'success');
        document.getElementById('addShowForm').reset();
        loadMyShows();
    } catch (_) {}
});

async function cancelShow(id) {
    if (!confirm('Cancel this show?')) return;
    try {
        await ownerApiCall(`/shows/${id}`, 'DELETE');
        showAlert('Show cancelled.', 'success');
        loadMyShows();
    } catch (_) {}
}


// The scope <select> in owner.html calls loadShows(); the owner portal's loader
// is named loadMyShows, so alias it rather than duplicating the markup.
function loadShows() {
    return loadMyShows();
}


/* =====================================================================
   MAINTENANCE - seat tiers + layout designer.
   Mirrors the admin portal exactly. Both talk to the same backend service,
   which scopes every call to this owner's theatre, so a screen belonging to
   anyone else is simply not found.

   Cells are built with createElement/textContent rather than innerHTML, so a
   tier name containing a quote or an angle bracket cannot break the markup.
   ===================================================================== */

let mtScreenId = null;
let mtTiers = [];
let mtGrid = [];
let mtTool = null;
let mtEditable = true;
let mtPainting = false;

function mtPopulateScreens(screens) {
    const sel = document.getElementById('mtScreenSelect');
    if (!sel) return;
    sel.innerHTML = '<option value="" disabled selected>Select Screen</option>';
    (screens || []).forEach(s => {
        const o = document.createElement('option');
        o.value = s.id;
        o.textContent = `${s.name} (${s.totalSeats} seats)`;
        sel.appendChild(o);
    });
}

async function mtOnScreenChange() {
    const id = parseInt(document.getElementById('mtScreenSelect').value, 10);
    if (!id) return;
    mtScreenId = id;
    document.getElementById('mtPanels').classList.remove('hidden');
    await mtLoadLayout();
}

async function mtLoadLayout() {
    if (!mtScreenId) return;
    try {
        const res = await ownerApiCall(`/screens/${mtScreenId}/seats`);
        if (!res) return;
        const d = res.data;

        mtTiers = d.tiers || [];
        mtEditable = d.editable !== false;

        const banner = document.getElementById('mtLockBanner');
        if (!mtEditable) {
            banner.textContent = '🔒 ' + (d.lockReason || 'This layout is locked.');
            banner.classList.remove('hidden');
        } else {
            banner.classList.add('hidden');
        }

        mtRenderTierTable();
        mtRenderPalette();

        const seats = d.seats || [];
        if (seats.length > 0) {
            const rowLabels = [...new Set(seats.map(s => s.rowLabel))].sort();
            const cols = Math.max(...seats.map(s => s.colIndex)) + 1;
            mtGrid = rowLabels.map(() =>
                Array.from({ length: cols }, () => ({ type: 'SEAT', tierId: null })));

            seats.forEach(s => {
                const r = rowLabels.indexOf(s.rowLabel);
                if (r >= 0 && s.colIndex < cols) {
                    mtGrid[r][s.colIndex] = { type: s.seatType, tierId: s.tierId };
                }
            });
            document.getElementById('mtRows').value = rowLabels.length;
            document.getElementById('mtCols').value = cols;
        } else {
            mtGrid = [];
        }
        mtRenderGrid();
    } catch (err) {
        console.error('[MT LOAD LAYOUT]', err);
    }
}

function mtRenderTierTable() {
    const tbody = document.getElementById('mtTierTableBody');
    tbody.innerHTML = '';

    if (mtTiers.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 5;
        td.style.color = 'var(--text-muted)';
        td.style.fontStyle = 'italic';
        td.textContent = 'No tiers yet. Add at least one before drawing the layout.';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }

    mtTiers.forEach(t => {
        const tr = document.createElement('tr');

        const swatch = document.createElement('td');
        const chip = document.createElement('span');
        chip.style.cssText = `display:inline-block;width:20px;height:20px;border-radius:3px;
                              background:${t.colorHex};border:1px solid #444;`;
        swatch.appendChild(chip);

        const name = document.createElement('td');
        const strong = document.createElement('strong');
        strong.textContent = t.name;
        name.appendChild(strong);

        const order = document.createElement('td');
        order.textContent = t.displayOrder;

        const count = document.createElement('td');
        count.textContent = t.seatCount;

        const actions = document.createElement('td');
        const edit = document.createElement('button');
        edit.className = 'btn-secondary-sm';
        edit.textContent = '✏️';
        edit.onclick = () => mtEditTier(t.id);
        const del = document.createElement('button');
        del.className = 'btn-danger-sm';
        del.textContent = '🗑️';
        del.onclick = () => mtDeleteTier(t.id);
        actions.appendChild(edit);
        actions.appendChild(del);

        tr.append(swatch, name, order, count, actions);
        tbody.appendChild(tr);
    });
}

async function mtCreateTier() {
    if (!mtScreenId) return;
    const name = document.getElementById('mtTierName').value.trim();
    if (!name) { showAlert('Tier name is required.', 'error'); return; }
    try {
        await ownerApiCall(`/screens/${mtScreenId}/tiers`, 'POST', {
            name,
            displayOrder: parseInt(document.getElementById('mtTierOrder').value, 10) || 0,
            colorHex: document.getElementById('mtTierColor').value
        });
        document.getElementById('mtTierName').value = '';
        showAlert('Tier created.', 'success');
        mtLoadLayout();
    } catch (err) { console.error('[MT CREATE TIER]', err); }
}

async function mtEditTier(tierId) {
    const t = mtTiers.find(x => x.id === tierId) || {};
    const name = prompt('Tier name:', t.name || '');
    if (name === null) return;
    const order = prompt('Display order:', t.displayOrder ?? 0);
    if (order === null) return;
    const color = prompt('Colour (hex, e.g. #C9A227):', t.colorHex || '#7A7A7A');
    if (color === null) return;
    try {
        await ownerApiCall(`/tiers/${tierId}`, 'PUT', {
            name, displayOrder: parseInt(order, 10) || 0, colorHex: color
        });
        showAlert('Tier updated.', 'success');
        mtLoadLayout();
    } catch (err) { console.error('[MT EDIT TIER]', err); }
}

async function mtDeleteTier(tierId) {
    const t = mtTiers.find(x => x.id === tierId) || {};
    if (!confirm(`Delete tier "${t.name || tierId}"?`)) return;
    try {
        await ownerApiCall(`/tiers/${tierId}`, 'DELETE');
        showAlert('Tier deleted.', 'success');
        mtLoadLayout();
    } catch (err) { console.error('[MT DELETE TIER]', err); }
}

function mtRenderPalette() {
    const wrap = document.getElementById('mtPalette');
    wrap.innerHTML = '';

    const makeBtn = (label, bg, tool) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.textContent = label;
        b.style.cssText = `padding:6px 10px;border-radius:4px;cursor:pointer;font-size:0.75rem;
                           border:2px solid transparent;background:${bg};color:#111;font-weight:600;`;
        b.onclick = () => {
            mtTool = tool;
            [...wrap.children].forEach(c => c.style.borderColor = 'transparent');
            b.style.borderColor = '#fff';
        };
        wrap.appendChild(b);
        return b;
    };

    mtTiers.forEach((t, i) => {
        const b = makeBtn(t.name, t.colorHex, { kind: 'TIER', tierId: t.id });
        if (i === 0) { mtTool = { kind: 'TIER', tierId: t.id }; b.style.borderColor = '#fff'; }
    });
    makeBtn('Pathway', '#3a3a3a', { kind: 'PATHWAY' }).style.color = '#ddd';
}

function mtBuildGrid() {
    const rows = parseInt(document.getElementById('mtRows').value, 10) || 0;
    const cols = parseInt(document.getElementById('mtCols').value, 10) || 0;
    if (rows < 1 || cols < 1) { showAlert('Rows and columns must be at least 1.', 'error'); return; }
    if (rows > 26) { showAlert('Maximum 26 rows (A-Z).', 'error'); return; }

    const firstTier = mtTiers.length ? mtTiers[0].id : null;
    mtGrid = Array.from({ length: rows }, () =>
        Array.from({ length: cols }, () => ({ type: 'SEAT', tierId: firstTier })));
    mtRenderGrid();
}

function mtRenderGrid() {
    const host = document.getElementById('mtGrid');
    host.innerHTML = '';

    if (!mtGrid.length) {
        const p = document.createElement('p');
        p.style.cssText = 'color:var(--text-muted);font-style:italic;';
        p.textContent = 'No layout yet. Set rows and columns, then click Build Grid.';
        host.appendChild(p);
        return;
    }

    mtGrid.forEach((row, r) => {
        const rowEl = document.createElement('div');
        rowEl.style.cssText = 'display:flex;align-items:center;gap:4px;margin-bottom:4px;';

        const label = document.createElement('span');
        label.style.cssText = 'width:22px;color:var(--primary-gold);font-size:0.7rem;font-weight:700;';
        label.textContent = String.fromCharCode(65 + r);
        rowEl.appendChild(label);

        let seatNo = 0;
        row.forEach((cell, c) => {
            const isSeat = cell.type === 'SEAT';
            if (isSeat) seatNo++;

            const tier = mtTiers.find(t => t.id === cell.tierId);
            const bg = isSeat ? (tier ? tier.colorHex : '#7A7A7A') : 'transparent';

            const el = document.createElement('div');
            el.style.cssText = `width:24px;height:24px;border-radius:3px;font-size:0.6rem;
                display:flex;align-items:center;justify-content:center;
                background:${bg};color:#111;font-weight:600;
                border:1px ${isSeat ? 'solid #333' : 'dashed #444'};
                cursor:${mtEditable ? 'pointer' : 'not-allowed'};user-select:none;`;
            el.textContent = isSeat ? seatNo : '';
            el.title = isSeat
                ? `${String.fromCharCode(65 + r)}${seatNo}${tier ? ' - ' + tier.name : ''}`
                : 'Pathway';

            if (mtEditable) {
                el.onmousedown = (e) => { e.preventDefault(); mtPainting = true; mtPaint(r, c); };
                el.onmouseenter = () => { if (mtPainting) mtPaint(r, c); };
            }
            rowEl.appendChild(el);
        });
        host.appendChild(rowEl);
    });

    const seats = mtGrid.flat().filter(c => c.type === 'SEAT').length;
    const paths = mtGrid.flat().length - seats;
    document.getElementById('mtSummary').textContent =
        `${seats} sellable seat(s), ${paths} pathway cell(s). Screen capacity updates on save.`;
}

document.addEventListener('mouseup', () => { mtPainting = false; });

function mtPaint(r, c) {
    if (!mtEditable || !mtTool) return;
    mtGrid[r][c] = (mtTool.kind === 'PATHWAY')
        ? { type: 'PATHWAY', tierId: null }
        : { type: 'SEAT', tierId: mtTool.tierId };
    mtRenderGrid();
}

async function mtSaveLayout() {
    if (!mtScreenId) return;
    if (!mtGrid.length) { showAlert('Build a grid first.', 'error'); return; }
    if (!mtTiers.length) { showAlert('Add at least one seat tier first.', 'error'); return; }

    const grid = mtGrid.map(row => row.map(cell => ({
        type: cell.type,
        tierId: cell.type === 'SEAT' ? cell.tierId : null
    })));

    try {
        await ownerApiCall(`/screens/${mtScreenId}/seats`, 'PUT', { grid });
        showAlert('Seat layout saved.', 'success');
        await mtLoadLayout();
        loadMyScreens();
    } catch (err) { console.error('[MT SAVE LAYOUT]', err); }
}


/* =====================================================================
   Per-show tier pricing, mirroring the admin portal.
   ===================================================================== */

let showTiers = [];

async function loadTiersForShowScreen(screenId) {
    if (!screenId) { renderShowTierPriceInputs([]); return; }
    try {
        const res = await ownerApiCall(`/screens/${screenId}/tiers`);
        renderShowTierPriceInputs((res && res.data) ? res.data : []);
    } catch (err) {
        console.error('[LOAD TIERS]', err);
        renderShowTierPriceInputs([]);
    }
}

function renderShowTierPriceInputs(tiers) {
    showTiers = tiers || [];
    const wrap = document.getElementById('showTierPrices');
    const rows = document.getElementById('showTierPriceRows');
    if (!wrap || !rows) return;

    rows.innerHTML = '';
    const baseInput = document.getElementById('showPrice');

    if (showTiers.length === 0) {
        wrap.classList.add('hidden');
        if (baseInput) baseInput.disabled = false;
        return;
    }

    wrap.classList.remove('hidden');
    // Base price is derived from the cheapest tier when tiers exist, so asking
    // for it separately would create a second source of truth.
    if (baseInput) { baseInput.value = ''; baseInput.disabled = true; }

    showTiers.forEach(t => {
        const row = document.createElement('div');
        row.className = 'form-row';
        row.style.alignItems = 'center';

        const swatch = document.createElement('span');
        swatch.style.cssText = `display:inline-block;width:16px;height:16px;border-radius:3px;
                                background:${t.colorHex};border:1px solid #3f3f46;flex-shrink:0;`;

        const label = document.createElement('span');
        label.textContent = t.name;
        label.style.cssText = 'min-width:110px;font-size:0.875rem;color:#e4e4e7;';

        const input = document.createElement('input');
        input.type = 'number';
        input.min = '1';
        input.step = '1';
        input.placeholder = '₹ price';
        input.dataset.tierId = t.id;
        input.className = 'tier-price-input';

        row.append(swatch, label, input);
        rows.appendChild(row);
    });
}

function collectTierPrices() {
    const out = [];
    document.querySelectorAll('.tier-price-input').forEach(i => {
        const v = parseFloat(i.value);
        if (!isNaN(v)) out.push({ tierId: parseInt(i.dataset.tierId, 10), price: v });
    });
    return out;
}
