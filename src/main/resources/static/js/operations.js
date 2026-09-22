/**
 * Operations & Compliance Reporting — Frontend
 *
 * Shared between admin.html and owner.html.
 * Detects which portal by checking the API base path.
 */

// Detect API base: admin uses /api/admin, owner uses /api/owner
const OPS_API_BASE = window.location.pathname.includes('owner.html') ? '/api/owner' : '/api/admin';

// ================= INITIALIZATION =================
// opsInit() is called from admin.js/owner.js when the Operations tab is first activated.

let opsInitialized = false;

function opsInit() {
    if (opsInitialized) return;
    opsInitialized = true;
    opsLoadShowsDropdown();
    opsLoadIncidentsList();
    opsLoadReportsList();

    // For admin, also load filter options and theatres dropdown
    if (OPS_API_BASE === '/api/admin') {
        opsLoadTheatresDropdown();
        opsLoadFilterOptions();
    } else {
        // For owner, populate filter data from shows for conflict check dropdowns
        opsLoadOwnerFilterData();
    }

    // Set default date range (last 30 days)
    const today = new Date();
    const thirtyDaysAgo = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000);
    const dateFrom = document.getElementById('opsDateFrom');
    const dateTo = document.getElementById('opsDateTo');
    if (dateFrom) dateFrom.value = thirtyDaysAgo.toISOString().split('T')[0];
    if (dateTo) dateTo.value = today.toISOString().split('T')[0];
    // Also set default date filters for show report
    const filterFrom = document.getElementById('opsFilterDateFrom');
    const filterTo = document.getElementById('opsFilterDateTo');
    if (filterFrom && !filterFrom.value) filterFrom.value = thirtyDaysAgo.toISOString().split('T')[0];
    if (filterTo && !filterTo.value) filterTo.value = today.toISOString().split('T')[0];
    // Also set default date filters for ticket holders section
    const thFilterFrom = document.getElementById('opsTHFilterDateFrom');
    const thFilterTo = document.getElementById('opsTHFilterDateTo');
    if (thFilterFrom && !thFilterFrom.value) thFilterFrom.value = thirtyDaysAgo.toISOString().split('T')[0];
    if (thFilterTo && !thFilterTo.value) thFilterTo.value = today.toISOString().split('T')[0];
    // Set default dates for screen utilisation
    const sevenDaysAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000);
    const utilFrom = document.getElementById('opsUtilDateFrom');
    const utilTo = document.getElementById('opsUtilDateTo');
    if (utilFrom && !utilFrom.value) utilFrom.value = sevenDaysAgo.toISOString().split('T')[0];
    if (utilTo && !utilTo.value) utilTo.value = today.toISOString().split('T')[0];
}

// ================= SECTION NAVIGATION =================

function opsShowSection(sectionId) {
    document.querySelectorAll('.ops-section').forEach(el => el.classList.add('hidden'));
    const section = document.getElementById(sectionId);
    if (section) section.classList.remove('hidden');
}

// ================= CARD NAVIGATION =================

function opsOpenCard(cardId) {
    // Hide the card grid
    const grid = document.getElementById('opsCardGrid');
    if (grid) grid.classList.add('hidden');
    // Hide all card views
    document.querySelectorAll('.ops-card-view').forEach(el => el.classList.add('hidden'));
    // Show the requested card
    const card = document.getElementById(cardId);
    if (card) card.classList.remove('hidden');
}

function opsBackToCards() {
    // Hide all card views
    document.querySelectorAll('.ops-card-view').forEach(el => el.classList.add('hidden'));
    // Show the card grid
    const grid = document.getElementById('opsCardGrid');
    if (grid) grid.classList.remove('hidden');
}

// ================= API HELPER =================

async function opsApiCall(endpoint, method = 'GET', body = null) {
    const token = localStorage.getItem('accessToken');
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };

    const response = await fetch(`${OPS_API_BASE}/operations${endpoint}`, {
        method,
        headers,
        body: body ? JSON.stringify(body) : null
    });

    if (response.status === 401 || response.status === 403) {
        alert('Session expired. Please log in again.');
        window.location.href = '/auth.html';
        return null;
    }

    // For Excel export (blob)
    if (response.headers.get('Content-Type')?.includes('spreadsheet')) {
        return response;
    }

    let result;
    try {
        result = await response.json();
    } catch (e) {
        throw new Error(`HTTP ${response.status}: Non-JSON response`);
    }
    if (!response.ok || (result.success !== undefined && !result.success)) {
        throw new Error(result.message || `HTTP ${response.status}`);
    }
    return result;
}

// ================= DROPDOWN LOADERS =================

async function opsLoadShowsDropdown() {
    try {
        const result = await opsApiCall('/shows?scope=all');
        if (!result) return;

        // Cached so the city -> theatre -> screen -> movie cascade can work out
        // which movies actually run on a chosen screen without another call.
        opsShowsCache = result.data || [];

        opsPopulateShowSelect('opsShowSelect', opsShowsCache, false);
        opsPopulateShowSelect('opsTHShowSelect', opsShowsCache, true);
    } catch (e) {
        console.error('Failed to load shows:', e);
    }
}

/**
 * Build a show picker. Shows are listed chronologically and the label spells out
 * movie + theatre + screen + exact time, which is what makes several different
 * movies running on the same screen on the same day tellable apart. The ticket
 * holder picker additionally groups the options by day.
 */
function opsPopulateShowSelect(selectId, shows, groupByDate) {
    const select = document.getElementById(selectId);
    if (!select) return;
    select.innerHTML = '';

    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.disabled = true;
    placeholder.selected = true;
    placeholder.textContent = shows.length
        ? 'Select Show'
        : 'No shows match the current filters';
    select.appendChild(placeholder);

    const sorted = shows.slice().sort((a, b) => new Date(a.startTime || 0) - new Date(b.startTime || 0));

    const addOption = (parent, show) => {
        const opt = document.createElement('option');
        opt.value = show.id;
        opt.textContent = opsShowLabel(show);
        parent.appendChild(opt);
    };

    if (!groupByDate) {
        sorted.forEach(show => addOption(select, show));
        return;
    }

    let currentDay = null;
    let group = null;
    sorted.forEach(show => {
        const day = show.startTime
            ? new Date(show.startTime).toLocaleDateString([], { day: '2-digit', month: 'short', year: 'numeric' })
            : 'Unknown date';
        if (day !== currentDay) {
            currentDay = day;
            group = document.createElement('optgroup');
            group.label = day;
            select.appendChild(group);
        }
        addOption(group, show);
    });
}

function opsShowLabel(show) {
    const when = show.startTime
        ? new Date(show.startTime).toLocaleString([], {
            day: '2-digit', month: 'short', hour: 'numeric', minute: '2-digit'
        })
        : '';
    return [show.movieTitle || 'Movie', show.theatreName, show.screenName, when]
        .filter(Boolean).join(' • ');
}

async function opsLoadTheatresDropdown() {
    try {
        const result = await opsApiCall('/theatres');
        if (!result) return;

        const theatres = result.data || [];
        // Used only if /filter-options fails, so the Theatre Report and theatre
        // filter still have something to show.
        opsTheatreFallback = theatres.map(t => ({
            id: t.id, name: t.name, cityName: t.cityName, cityId: t.cityId
        }));
        // opsTheatreSelect is deliberately absent: it belongs to the Theatre Report
        // and is narrowed by its city picker, so filling it with every theatre here
        // would silently undo that narrowing depending on call order.
        const selects = ['incTheatre', 'opsUtilTheatre'];
        selects.forEach(id => {
            const select = document.getElementById(id);
            if (!select) return;
            while (select.options.length > 1) select.remove(1);
            theatres.forEach(t => {
                const opt = document.createElement('option');
                opt.value = t.id;
                opt.textContent = t.name;
                select.appendChild(opt);
            });
        });
        // Keep the Theatre Report picker in step with the fallback list.
        opsPopulateTheatreReportCities();
    } catch (e) {
        console.error('Failed to load theatres:', e);
    }
}

// ================= FILTER OPTIONS (Admin only) =================

let opsFilterData = { theatres: [], movies: [], screens: [], cities: [] };

// Show list shared by the pickers and the cascading filters.
let opsShowsCache = [];

// Theatre list from /theatres, used only when /filter-options is unavailable.
let opsTheatreFallback = [];

function opsTheatreList() {
    const fromFilters = (opsFilterData && opsFilterData.theatres) || [];
    return fromFilters.length ? fromFilters : opsTheatreFallback;
}

// Which <select> ids belong to each Operations filter group.
const OPS_FILTER_GROUPS = {
    showReport: {
        city: 'opsFilterCity', theatre: 'opsFilterTheatre',
        screen: 'opsFilterScreen', movie: 'opsFilterMovie',
        dateFrom: 'opsFilterDateFrom', dateTo: 'opsFilterDateTo'
    },
    ticketHolders: {
        city: 'opsTHFilterCity', theatre: 'opsTHFilterTheatre',
        screen: 'opsTHFilterScreen', movie: 'opsTHFilterMovie',
        dateFrom: 'opsTHFilterDateFrom', dateTo: 'opsTHFilterDateTo'
    }
};

async function opsLoadFilterOptions() {
    try {
        const result = await opsApiCall('/filter-options');
        if (!result || !result.data) return;
        opsFilterData = result.data;
        populateFilterDropdowns();
    } catch (e) {
        console.error('Failed to load filter options:', e);
    }
}

function populateFilterDropdowns() {
    // Every filter group is filled through the same city -> theatre -> screen
    // -> movie cascade, so the Show Report and Ticket Holders sections can never
    // disagree about what is selectable.
    opsCascadeOpsFilters('showReport', 'init');
    opsPopulateTHFilterDropdowns();
    opsPopulateTheatreReportCities();
    // Also populate conflict check dropdowns
    populateConflictDropdowns();
}

/**
 * Fill the Ticket Holder filter dropdowns (Theatre / Screen / Movie).
 * Shared by admin (filter-options) and owner (derived from its own shows).
 */
/**
 * Rebuild one <select> from a list, keeping the current choice only while it is
 * still valid after narrowing and otherwise falling back to the placeholder.
 */
function opsFillSelect(selectId, items, valueFn, labelFn, placeholder) {
    const select = document.getElementById(selectId);
    if (!select) return;
    const previous = select.value;

    select.innerHTML = '';
    const first = document.createElement('option');
    first.value = '';
    first.textContent = placeholder;
    select.appendChild(first);

    items.forEach(item => {
        const opt = document.createElement('option');
        opt.value = valueFn(item);
        opt.textContent = labelFn(item);
        select.appendChild(opt);
    });

    select.value = items.some(i => String(valueFn(i)) === String(previous)) ? previous : '';
}

/**
 * Narrow one Operations filter group down the City -> Theatre -> Screen -> Movie
 * chain. Changing a parent clears the children it invalidates, an empty child
 * list says so instead of looking broken, and a selection that no longer exists
 * is dropped rather than silently ignored.
 */
function opsCascadeOpsFilters(group, changed) {
    const ids = OPS_FILTER_GROUPS[group];
    if (!ids) return;

    const data = opsFilterData || { theatres: [], screens: [], movies: [], cities: [] };
    const theatres = opsTheatreList();
    const cityEl = document.getElementById(ids.city);
    const theatreEl = document.getElementById(ids.theatre);
    const screenEl = document.getElementById(ids.screen);
    const movieEl = document.getElementById(ids.movie);

    if (changed === 'city' && theatreEl) theatreEl.value = '';
    if ((changed === 'city' || changed === 'theatre') && screenEl) screenEl.value = '';
    if ((changed === 'city' || changed === 'theatre' || changed === 'screen') && movieEl) movieEl.value = '';

    const cityId = cityEl ? cityEl.value : '';

    // City -> Theatre
    if (theatreEl) {
        const narrowedTheatres = theatres
            .filter(t => !cityId || String(t.cityId) === String(cityId));
        opsFillSelect(ids.theatre, narrowedTheatres, t => t.id, t => t.name,
            cityId && narrowedTheatres.length === 0 ? 'No theatres in this city' : 'All Theatres');
    }

    const theatreId = theatreEl ? theatreEl.value : '';

    // Theatre (or city) -> Screen
    if (screenEl) {
        const theatresInCity = cityId
            ? new Set(theatres
                .filter(t => String(t.cityId) === String(cityId))
                .map(t => String(t.id)))
            : null;
        const screens = (data.screens || []).filter(s => {
            if (theatreId) return String(s.theatreId) === String(theatreId);
            if (theatresInCity) return theatresInCity.has(String(s.theatreId));
            return true;
        });
        opsFillSelect(ids.screen, screens, s => s.id, s => s.name + ' — ' + s.theatreName,
            screens.length === 0 ? 'No screens match' : 'All Screens');
    }

    const screenId = screenEl ? screenEl.value : '';

    // Screen -> Movie: only movies actually scheduled on the chosen screen
    if (movieEl) {
        let movies = (data.movies || []).slice();
        if (screenId) {
            const onScreen = new Set((opsShowsCache || [])
                .filter(s => String(s.screenId) === String(screenId))
                .map(s => s.movieTitle));
            const narrowed = movies.filter(m => onScreen.has(m.title));
            if (narrowed.length > 0) movies = narrowed;
        }
        opsFillSelect(ids.movie, movies, m => m.title, m => m.title, 'All Movies');
    }
}

/**
 * Single onchange entry point for every Operations filter control: narrow the
 * chain first, then refresh the show list, so a stale child or a show list that
 * no longer matches the filters can never be left on screen.
 */
function opsOnFilterChange(group, changed) {
    opsCascadeOpsFilters(group, changed);
    if (group === 'showReport') {
        opsApplyShowFilters();
    } else {
        opsApplyTHFilters();
    }
}

/**
 * Fill the Ticket Holder filter group. Kept as a named function because it is
 * also the entry point for the owner portal, which has no /filter-options call.
 */
function opsPopulateTHFilterDropdowns() {
    opsCascadeOpsFilters('ticketHolders', 'init');
}

function opsPopulateTheatreReportCities() {
    if (!document.getElementById('opsTheatreReportCity')) return;
    opsFillSelect('opsTheatreReportCity', opsFilterData.cities || [],
        c => c.id, c => c.name, 'All Cities');
    opsNarrowTheatreReport();
}

/** City -> Theatre for the Theatre Report. */
function opsNarrowTheatreReport() {
    const cityEl = document.getElementById('opsTheatreReportCity');
    if (!cityEl) return;
    const cityId = cityEl.value;
    const theatres = opsTheatreList()
        .filter(t => !cityId || String(t.cityId) === String(cityId));
    opsFillSelect('opsTheatreSelect', theatres, t => t.id,
        t => t.cityName ? `${t.name} (${t.cityName})` : t.name,
        theatres.length === 0 ? 'No theatres in this city' : 'Select Theatre');
}

function populateConflictDropdowns() {
    // Populate conflict screen dropdown from filter data
    const conflictScreen = document.getElementById('opsConflictScreen');
    if (conflictScreen) {
        while (conflictScreen.options.length > 1) conflictScreen.remove(1);
        (opsFilterData.screens || []).forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.id || s.name;
            opt.textContent = s.name + ' — ' + s.theatreName;
            conflictScreen.appendChild(opt);
        });
    }
    // Populate conflict movie dropdown from filter data
    const conflictMovie = document.getElementById('opsConflictMovie');
    if (conflictMovie) {
        while (conflictMovie.options.length > 1) conflictMovie.remove(1);
        (opsFilterData.movies || []).forEach(m => {
            const opt = document.createElement('option');
            opt.value = m.id || m.title;
            opt.textContent = m.title;
            opt.setAttribute('data-duration', '150'); // default
            conflictMovie.appendChild(opt);
        });
        conflictMovie.setAttribute('onchange', 'opsOnConflictMovieChange()');
    }
}

async function opsLoadOwnerFilterData() {
    try {
        const result = await opsApiCall('/shows?scope=all');
        if (!result) return;
        const shows = result.data || [];
        // Extract unique screens and movies from shows
        const screenMap = {};
        const movieMap = {};
        shows.forEach(s => {
            if (s.screenName && s.theatreName) {
                const key = s.screenName + '@' + s.theatreName;
                if (!screenMap[key]) {
                    screenMap[key] = {
                        id: s.screenId,
                        name: s.screenName,
                        theatreName: s.theatreName,
                        theatreId: s.theatreId,
                        cityId: s.cityId
                    };
                }
            }
            if (s.movieTitle) {
                if (!movieMap[s.movieTitle]) movieMap[s.movieTitle] = { id: s.movieId, title: s.movieTitle };
            }
        });
        // Kept so the screen -> movie narrowing has a show list to work from.
        opsShowsCache = shows;

        opsFilterData = {
            theatres: [],
            cities: [],
            screens: Object.values(screenMap),
            movies: Object.values(movieMap)
        };
        // Same cascade entry points the admin portal uses, so the owner's Show
        // Report and Ticket Holders filters are populated identically (narrowed
        // to Screen -> Movie, since an owner only ever has one theatre).
        opsCascadeOpsFilters('showReport', 'init');
        opsPopulateTHFilterDropdowns();
        opsPopulateTheatreReportCities();
        populateConflictDropdowns();
    } catch (e) {
        console.error('Failed to load owner filter data:', e);
    }
}

/**
 * Read one filter group's selections and return the shows that match.
 * Filtering happens client-side because the (small) show list has to be fetched
 * anyway, and it keeps city/theatre/screen/movie narrowing in a single place.
 */
async function opsFilteredShows(group) {
    const ids = OPS_FILTER_GROUPS[group];
    if (!ids) return [];
    const val = id => (id ? (document.getElementById(id)?.value || '') : '');

    const dateFrom = val(ids.dateFrom);
    const dateTo = val(ids.dateTo);
    const cityId = val(ids.city);
    const theatreId = val(ids.theatre);
    const screenId = val(ids.screen);
    const movieTitle = val(ids.movie);

    // An impossible range is called out rather than quietly returning nothing.
    if (dateFrom && dateTo && dateFrom > dateTo) {
        opsShowFilterWarning(group,
            `Start date (${dateFrom}) cannot be after end date (${dateTo}). Pick a valid range.`);
        return [];
    }
    opsShowFilterWarning(group, '');

    const result = await opsApiCall('/shows?scope=all');
    if (!result) return [];
    opsShowsCache = result.data || [];

    return opsShowsCache
        .filter(s => !cityId || String(s.cityId) === String(cityId))
        .filter(s => !theatreId || String(s.theatreId) === String(theatreId))
        .filter(s => !screenId || String(s.screenId) === String(screenId))
        .filter(s => !movieTitle || s.movieTitle === movieTitle)
        .filter(s => !dateFrom || (s.startTime && s.startTime.slice(0, 10) >= dateFrom))
        .filter(s => !dateTo || (s.startTime && s.startTime.slice(0, 10) <= dateTo));
}

/**
 * Inline warning placed directly under a filter row. Created on demand so the
 * markup does not need an error slot in every section.
 */
function opsShowFilterWarning(group, message) {
    const ids = OPS_FILTER_GROUPS[group];
    if (!ids) return;
    const anchor = document.getElementById(ids.screen) || document.getElementById(ids.movie);
    if (!anchor) return;
    const row = anchor.closest('.analytics-filters');
    if (!row || !row.parentElement) return;

    let box = row.parentElement.querySelector(`[data-ops-warning="${group}"]`);
    if (!box) {
        box = document.createElement('div');
        box.className = 'alert alert-error hidden';
        box.setAttribute('data-ops-warning', group);
        box.style.marginBottom = '12px';
        row.insertAdjacentElement('afterend', box);
    }
    if (message) {
        box.textContent = '⚠️ ' + message;
        box.classList.remove('hidden');
    } else {
        box.textContent = '';
        box.classList.add('hidden');
    }
}

async function opsApplyShowFilters() {
    try {
        const shows = await opsFilteredShows('showReport');
        opsPopulateShowSelect('opsShowSelect', shows, false);
    } catch (e) {
        console.error('Failed to filter shows:', e);
    }
}

async function opsApplyTHFilters() {
    try {
        const shows = await opsFilteredShows('ticketHolders');
        opsPopulateShowSelect('opsTHShowSelect', shows, true);
    } catch (e) {
        console.error('Failed to filter TH shows:', e);
    }
}

function opsResetShowFilters() {
    ['opsFilterCity', 'opsFilterTheatre', 'opsFilterScreen', 'opsFilterMovie',
     'opsFilterDateFrom', 'opsFilterDateTo'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    opsShowFilterWarning('showReport', '');
    opsCascadeOpsFilters('showReport', 'init');
    opsLoadShowsDropdown();
}

function opsResetTHFilters() {
    ['opsTHFilterCity', 'opsTHFilterTheatre', 'opsTHFilterScreen', 'opsTHFilterMovie',
     'opsTHFilterDateFrom', 'opsTHFilterDateTo'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    const status = document.getElementById('opsTHFilterStatus');
    if (status) status.value = 'CONFIRMED';

    opsShowFilterWarning('ticketHolders', '');
    opsCascadeOpsFilters('ticketHolders', 'init');

    // Drop the loaded rows and clear the result area
    opsTHHolders = [];
    const container = document.getElementById('opsTicketHoldersResult');
    if (container) {
        container.classList.add('hidden');
        container.innerHTML = '';
    }

    opsLoadShowsDropdown();
}

// ================= SHOW REPORT =================

async function opsLoadShowReport() {
    const showId = document.getElementById('opsShowSelect')?.value;
    if (!showId) return;

    const container = document.getElementById('opsShowReportResult');
    container.classList.remove('hidden');
    container.innerHTML = '<p style="color: var(--text-muted);">Loading...</p>';

    try {
        const result = await opsApiCall(`/reports/shows/${showId}`);
        if (!result) return;

        const r = result.data;
        container.innerHTML = `
            <div style="margin-bottom: 20px;">
                <h4 style="color: var(--text-primary); margin-bottom: 8px;">${r.movieTitle}</h4>
                <p style="color: var(--text-muted); font-size: 13px;">
                    ${r.movieLanguage || ''} • ${r.movieFormat || ''} ${r.cbfcRating ? '• ' + r.cbfcRating : ''}<br>
                    ${r.theatreName}, ${r.cityName} — ${r.screenName}<br>
                    ${r.showStartTime ? new Date(r.showStartTime).toLocaleString() : ''}
                </p>
            </div>
            <div class="kpi-grid" style="grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); margin-bottom: 20px;">
                <div class="kpi-card"><div class="kpi-value">${r.confirmedBookings}</div><div class="kpi-label">Confirmed Bookings</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.confirmedTickets}</div><div class="kpi-label">Tickets Sold</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.cancelledBookings}</div><div class="kpi-label">Cancelled</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.expiredBookings}</div><div class="kpi-label">Expired</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.occupancyPercentage}%</div><div class="kpi-label">Occupancy</div></div>
                <div class="kpi-card"><div class="kpi-value">₹${Number(r.totalRevenue).toLocaleString()}</div><div class="kpi-label">Revenue</div></div>
            </div>
            <div style="display: flex; gap: 8px; margin-bottom: 16px;">
                <button class="btn btn-primary btn-sm" onclick="opsGenerateReport('SHOW_REPORT', ${showId})">Generate Report</button>
            </div>
            ${r.ticketHolders && r.ticketHolders.length > 0 ? `
                <h4 style="color: var(--text-primary); margin-bottom: 8px;">Ticket Holders (${r.ticketHolders.length})</h4>
                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>Seat</th>
                                <th>Attendee Name</th>
                                <th>Phone</th>
                                <th>DOB</th>
                                <th>Booked For</th>
                                <th>Booker</th>
                                <th>Booker Email</th>
                                <th>Tier</th>
                                <th>Price</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${r.ticketHolders.map((h, idx) => `
                                <tr>
                                    <td>${idx + 1}</td>
                                    <td><strong>${h.seatCode}</strong></td>
                                    <td>${h.attendeeName || h.customerName || ''}</td>
                                    <td>${h.attendeePhone || h.customerPhone || ''}</td>
                                    <td>${h.attendeeDob || '—'}</td>
                                    <td>${h.bookingForSelf === true ? 'Self' : h.bookingForSelf === false ? 'Others' : '—'}</td>
                                    <td>${h.customerName || ''}</td>
                                    <td>${h.customerEmail || ''}</td>
                                    <td>${h.seatTier || 'Standard'}</td>
                                    <td>₹${Number(h.ticketPrice).toLocaleString()}</td>
                                    <td><span class="badge badge-${h.bookingStatus === 'CONFIRMED' ? 'success' : 'warning'}">${h.bookingStatus}</span></td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            ` : '<p style="color: var(--text-muted);">No confirmed ticket holders for this show.</p>'}
            <p style="color: var(--text-muted); font-size: 11px; margin-top: 12px;">Generated at: ${r.generatedAt} by ${r.generatedBy}</p>
        `;
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
}

// ================= THEATRE REPORT =================

async function opsLoadTheatreReport() {
    const theatreId = OPS_API_BASE === '/api/admin'
        ? document.getElementById('opsTheatreSelect')?.value
        : null; // Owner's theatre is auto-resolved server-side

    const dateFrom = document.getElementById('opsDateFrom')?.value || '';
    const dateTo = document.getElementById('opsDateTo')?.value || '';

    const container = document.getElementById('opsTheatreReportResult');
    container.classList.remove('hidden');
    container.innerHTML = '<p style="color: var(--text-muted);">Loading...</p>';

    try {
        let endpoint;
        if (OPS_API_BASE === '/api/admin') {
            if (!theatreId) { container.innerHTML = '<p style="color: #ff5252;">Please select a theatre.</p>'; return; }
            endpoint = `/reports/theatres/${theatreId}?dateFrom=${dateFrom}&dateTo=${dateTo}`;
        } else {
            endpoint = `/reports/theatre?dateFrom=${dateFrom}&dateTo=${dateTo}`;
        }

        const result = await opsApiCall(endpoint);
        if (!result) return;

        const r = result.data;
        container.innerHTML = `
            <div style="margin-bottom: 20px;">
                <h4 style="color: var(--text-primary); margin-bottom: 4px;">${r.theatreName}</h4>
                <p style="color: var(--text-muted); font-size: 13px;">
                    ${r.theatreAddress}, ${r.cityName}<br>
                    Period: ${r.dateFrom} to ${r.dateTo}
                </p>
            </div>
            <div class="kpi-grid" style="grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); margin-bottom: 20px;">
                <div class="kpi-card"><div class="kpi-value">${r.totalShows}</div><div class="kpi-label">Total Shows</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.totalSeatCapacity?.toLocaleString()}</div><div class="kpi-label">Total Capacity</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.confirmedTickets?.toLocaleString()}</div><div class="kpi-label">Confirmed Tickets</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.cancelledBookings}</div><div class="kpi-label">Cancelled</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.expiredBookings}</div><div class="kpi-label">Expired</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.occupancyPercentage}%</div><div class="kpi-label">Occupancy</div></div>
                <div class="kpi-card"><div class="kpi-value">₹${Number(r.totalRevenue).toLocaleString()}</div><div class="kpi-label">Revenue</div></div>
            </div>
            <div style="margin-bottom: 16px;">
                <button class="btn btn-primary btn-sm" onclick="opsGenerateReport('THEATRE_REPORT', null, ${OPS_API_BASE === '/api/admin' ? theatreId : 'null'})">Generate Report</button>
            </div>
            ${r.showBreakdown && r.showBreakdown.length > 0 ? `
                <h4 style="color: var(--text-primary); margin-bottom: 8px;">Show Breakdown</h4>
                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Movie</th>
                                <th>Screen</th>
                                <th>Date</th>
                                <th>Format</th>
                                <th>Capacity</th>
                                <th>Sold</th>
                                <th>Occupancy</th>
                                <th>Revenue</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${r.showBreakdown.map(s => `
                                <tr>
                                    <td>${s.movieTitle}</td>
                                    <td>${s.screenName}</td>
                                    <td>${s.startTime ? new Date(s.startTime).toLocaleString() : ''}</td>
                                    <td>${s.format || ''}</td>
                                    <td>${s.capacity}</td>
                                    <td>${s.ticketsSold}</td>
                                    <td>${s.occupancyPercentage}%</td>
                                    <td>₹${Number(s.revenue).toLocaleString()}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            ` : '<p style="color: var(--text-muted);">No shows in this period.</p>'}
            <p style="color: var(--text-muted); font-size: 11px; margin-top: 12px;">Generated at: ${r.generatedAt} by ${r.generatedBy}</p>
        `;
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
}

// ================= TICKET HOLDERS =================

let opsTHHolders = [];   // holders for the show currently selected in the dropdown

async function opsLoadTicketHolders() {
    const showId = document.getElementById('opsTHShowSelect')?.value;
    if (!showId) return;

    const status = document.getElementById('opsTHFilterStatus')?.value || 'CONFIRMED';

    const container = document.getElementById('opsTicketHoldersResult');
    container.classList.remove('hidden');
    container.innerHTML = `
        <div class="analytics-filters" style="margin-bottom: 12px;">
            <div class="filter-group">
                <label for="opsTHSearchBox">Search</label>
                <input type="text" id="opsTHSearchBox" placeholder="Name, phone, seat or booking ID" oninput="opsRenderTHRows()" style="min-width: 220px;">
            </div>
            <div class="filter-group">
                <label for="opsTHFilterBookedFor">Booked For</label>
                <select id="opsTHFilterBookedFor" onchange="opsRenderTHRows()">
                    <option value="">All</option>
                    <option value="SELF">Self</option>
                    <option value="OTHERS">Others</option>
                </select>
            </div>
            <div class="filter-group">
                <label for="opsTHFilterTier">Tier</label>
                <select id="opsTHFilterTier" onchange="opsRenderTHRows()">
                    <option value="">All Tiers</option>
                </select>
            </div>
            <button class="btn btn-primary btn-sm" onclick="opsGenerateReport('TICKET_HOLDER_REPORT', ${showId})" style="align-self: flex-end;">Generate Report</button>
        </div>
        <div id="opsTHTableArea"><p style="color: var(--text-muted);">Loading...</p></div>
    `;

    try {
        const result = await opsApiCall(`/reports/ticket-holders?showId=${showId}&status=${status}`);
        if (!result) return;

        // Seat order reads naturally (A1, A2, B1 ...) and keeps the serial numbers stable
        opsTHHolders = (result.data || []).slice().sort(opsSeatCodeCompare);

        // Tier choices come from the tiers this show actually has
        const tierSelect = document.getElementById('opsTHFilterTier');
        if (tierSelect) {
            const tiers = [...new Set(opsTHHolders.map(h => h.seatTier || 'Standard'))].sort();
            tiers.forEach(t => {
                const opt = document.createElement('option');
                opt.value = t;
                opt.textContent = t;
                tierSelect.appendChild(opt);
            });
        }

        opsRenderTHRows();
    } catch (e) {
        const area = document.getElementById('opsTHTableArea');
        if (area) area.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
}

/** Natural seat ordering: letters first, then the numeric part. */
function opsSeatCodeCompare(a, b) {
    const sa = (a.seatCode || '').toUpperCase();
    const sb = (b.seatCode || '').toUpperCase();
    const ma = sa.match(/^([A-Z]*)(\d*)/);
    const mb = sb.match(/^([A-Z]*)(\d*)/);
    const la = ma ? ma[1] : '';
    const lb = mb ? mb[1] : '';
    if (la !== lb) return la < lb ? -1 : 1;
    const na = ma && ma[2] ? parseInt(ma[2], 10) : 0;
    const nb = mb && mb[2] ? parseInt(mb[2], 10) : 0;
    if (na !== nb) return na - nb;
    return sa < sb ? -1 : sa > sb ? 1 : 0;
}

/** Apply the search box / Booked For / Tier filters and render the holders table. */
function opsRenderTHRows() {
    const area = document.getElementById('opsTHTableArea');
    if (!area) return;

    const container = document.getElementById('opsTicketHoldersResult');
    if (container) container.classList.remove('hidden');

    const query = (document.getElementById('opsTHSearchBox')?.value || '').trim().toLowerCase();
    const bookedFor = document.getElementById('opsTHFilterBookedFor')?.value || '';
    const tier = document.getElementById('opsTHFilterTier')?.value || '';

    let rows = opsTHHolders;

    if (bookedFor === 'SELF') rows = rows.filter(h => h.bookingForSelf === true);
    else if (bookedFor === 'OTHERS') rows = rows.filter(h => h.bookingForSelf === false);

    if (tier) rows = rows.filter(h => (h.seatTier || 'Standard') === tier);

    if (query) {
        rows = rows.filter(h => [
            h.attendeeName, h.customerName, h.attendeePhone, h.customerPhone,
            h.customerEmail, h.seatCode, h.bookingId, h.transactionId, h.paymentTransactionId
        ].filter(Boolean).join(' ').toLowerCase().includes(query));
    }

    if (opsTHHolders.length === 0) {
        area.innerHTML = '<p style="color: var(--text-muted);">No ticket holders found for the selected show and status.</p>';
        return;
    }
    if (rows.length === 0) {
        area.innerHTML = `<p style="color: var(--text-muted);">No ticket holders match the current filters (${opsTHHolders.length} loaded).</p>`;
        return;
    }

    area.innerHTML = `
        <p style="color: var(--text-muted); font-size: 13px; margin-bottom: 8px;">Showing ${rows.length} of ${opsTHHolders.length} ticket holder(s)</p>
        <div class="table-wrapper">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Seat</th>
                        <th>Attendee Name</th>
                        <th>Phone</th>
                        <th>DOB</th>
                        <th>Booked For</th>
                        <th>Booker</th>
                        <th>Booker Email</th>
                        <th>Tier</th>
                        <th>Price</th>
                        <th>Booking Time</th>
                        <th>Status</th>
                        <th>Payment</th>
                    </tr>
                </thead>
                <tbody>
                    ${rows.map((h, idx) => `
                        <tr>
                            <td>${idx + 1}</td>
                            <td><strong>${h.seatCode}</strong></td>
                            <td>${h.attendeeName || h.customerName || ''}</td>
                            <td>${h.attendeePhone || h.customerPhone || ''}</td>
                            <td>${h.attendeeDob || '—'}</td>
                            <td>${h.bookingForSelf === true ? 'Self' : h.bookingForSelf === false ? 'Others' : '—'}</td>
                            <td>${h.customerName || ''}</td>
                            <td>${h.customerEmail || ''}</td>
                            <td>${h.seatTier || 'Standard'}</td>
                            <td>₹${Number(h.ticketPrice).toLocaleString()}</td>
                            <td>${h.bookingTime ? new Date(h.bookingTime).toLocaleString() : ''}</td>
                            <td><span class="badge badge-${h.bookingStatus === 'CONFIRMED' ? 'success' : 'warning'}">${h.bookingStatus}</span></td>
                            <td>${h.paymentStatus || 'N/A'}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
    `;
}

// ================= INCIDENTS =================

function opsShowCreateIncidentForm() {
    document.getElementById('opsIncidentForm')?.classList.remove('hidden');
    // Set default start time to now
    const startTime = document.getElementById('incStartTime');
    if (startTime) {
        const now = new Date();
        startTime.value = now.toISOString().slice(0, 16);
    }
}

function opsHideCreateIncidentForm() {
    document.getElementById('opsIncidentForm')?.classList.add('hidden');
}

async function opsCreateIncident() {
    const type = document.getElementById('incType')?.value;
    const severity = document.getElementById('incSeverity')?.value;
    const description = document.getElementById('incDescription')?.value;
    const startTime = document.getElementById('incStartTime')?.value;

    if (!type || !severity || !description || !startTime) {
        alert('Please fill in all required fields.');
        return;
    }

    const body = {
        type,
        severity,
        theatreId: OPS_API_BASE === '/api/admin'
            ? parseInt(document.getElementById('incTheatre')?.value)
            : null, // Owner's theatre is auto-resolved server-side
        description,
        incidentStartTime: new Date(startTime).toISOString()
    };

    try {
        await opsApiCall('/incidents', 'POST', body);
        alert('Incident created successfully.');
        opsHideCreateIncidentForm();
        opsLoadIncidentsList();
    } catch (e) {
        alert('Error creating incident: ' + e.message);
    }
}

async function opsLoadIncidentsList() {
    const container = document.getElementById('opsIncidentsList');
    if (!container) return;

    try {
        const result = await opsApiCall('/incidents');
        if (!result) return;

        const incidents = result.data || [];
        if (incidents.length === 0) {
            container.innerHTML = '<p style="color: var(--text-muted);">No incidents found.</p>';
            return;
        }

        container.innerHTML = `
            <div class="table-wrapper">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Type</th>
                            <th>Severity</th>
                            <th>Theatre</th>
                            <th>Date</th>
                            <th>Status</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${incidents.map(i => `
                            <tr>
                                <td>#${i.id}</td>
                                <td>${formatIncidentType(i.type)}</td>
                                <td><span class="badge badge-${getSeverityColor(i.severity)}">${i.severity}</span></td>
                                <td>${i.theatreName || ''}</td>
                                <td>${i.incidentStartTime ? new Date(i.incidentStartTime).toLocaleString() : ''}</td>
                                <td><span class="badge badge-${getStatusColor(i.status)}">${formatStatus(i.status)}</span></td>
                                <td>
                                    <button class="btn btn-secondary btn-sm" onclick="opsViewIncident(${i.id})">View</button>
                                    ${i.status !== 'CLOSED' ? `<button class="btn btn-secondary btn-sm" onclick="opsCloseIncident(${i.id})">Close</button>` : ''}
                                    <button class="btn btn-secondary btn-sm" onclick="opsGenerateReport('INCIDENT_REPORT', null, null, ${i.id})">Report</button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error loading incidents: ${e.message}</p>`;
    }
}

async function opsViewIncident(id) {
    try {
        const result = await opsApiCall(`/incidents/${id}`);
        if (!result) return;
        const i = result.data;
        alert(
            `Incident #${i.id}\n` +
            `Type: ${formatIncidentType(i.type)}\n` +
            `Severity: ${i.severity}\n` +
            `Status: ${formatStatus(i.status)}\n` +
            `Theatre: ${i.theatreName}\n` +
            `Screen: ${i.screenName || 'N/A'}\n` +
            `Movie: ${i.movieTitle || 'N/A'}\n` +
            `Description: ${i.description}\n` +
            `Start: ${i.incidentStartTime ? new Date(i.incidentStartTime).toLocaleString() : 'N/A'}\n` +
            `Reported: ${i.reportedTime ? new Date(i.reportedTime).toLocaleString() : 'N/A'}\n` +
            `Created by: ${i.createdByName}`
        );
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

async function opsCloseIncident(id) {
    if (!confirm('Are you sure you want to close this incident?')) return;
    try {
        await opsApiCall(`/incidents/${id}/close`, 'POST');
        alert('Incident closed.');
        opsLoadIncidentsList();
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

// ================= REPORTS =================

async function opsLoadReportsList() {
    const container = document.getElementById('opsReportsList');
    if (!container) return;

    try {
        const result = await opsApiCall('/reports');
        if (!result) return;

        const reports = result.data || [];
        if (reports.length === 0) {
            container.innerHTML = '<p style="color: var(--text-muted);">No generated reports yet.</p>';
            return;
        }

        container.innerHTML = `
            <div class="table-wrapper">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Type</th>
                            <th>Scope</th>
                            <th>Generated By</th>
                            <th>Generated At</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${reports.map(r => `
                            <tr>
                                <td>#${r.id}</td>
                                <td>${formatReportType(r.reportType)}</td>
                                <td>${r.scopeName || (r.reportScope + ' #' + r.scopeId)}</td>
                                <td>${r.generatedByUserName || ''}</td>
                                <td>${r.generatedAt ? new Date(r.generatedAt).toLocaleString() : ''}</td>
                                <td>
                                    <button class="btn btn-secondary btn-sm" onclick="opsViewReport(${r.id})">View</button>
                                    <button class="btn btn-primary btn-sm" onclick="opsExportReport(${r.id})">Export Excel</button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error loading reports: ${e.message}</p>`;
    }
}

async function opsGenerateReport(reportType, showId, theatreId, incidentId) {
    try {
        const body = { reportType, showId: showId || null, theatreId: theatreId || null, incidentId: incidentId || null };
        const result = await opsApiCall('/reports/generate', 'POST', body);
        alert('Report generated successfully. ID: ' + result.data.id);
        opsLoadReportsList();
    } catch (e) {
        alert('Error generating report: ' + e.message);
    }
}

async function opsViewReport(reportId) {
    try {
        const result = await opsApiCall(`/reports/${reportId}`);
        if (!result) return;
        const r = result.data;

        // Show a formatted view of the snapshot
        const snapshot = JSON.parse(r.snapshotData);
        const formatted = JSON.stringify(snapshot, null, 2).substring(0, 3000);
        alert(
            `Report #${r.id}\n` +
            `Type: ${formatReportType(r.reportType)}\n` +
            `Scope: ${r.scopeName || r.reportScope + ' #' + r.scopeId}\n` +
            `Generated: ${r.generatedAt ? new Date(r.generatedAt).toLocaleString() : ''}\n` +
            `By: ${r.generatedByUserName}\n\n` +
            `Snapshot data (first 3000 chars):\n${formatted}...`
        );
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

async function opsExportReport(reportId) {
    try {
        const response = await fetch(`${OPS_API_BASE}/operations/reports/${reportId}/export`, {
            headers: { 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` }
        });

        if (!response.ok) throw new Error('Export failed');

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || `report_${reportId}.xlsx`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        a.remove();
    } catch (e) {
        alert('Error exporting report: ' + e.message);
    }
}

// ================= FORMATTERS =================

function formatIncidentType(type) {
    const map = {
        'FIRE': '🔥 Fire',
        'MEDICAL_EMERGENCY': '🏥 Medical',
        'SECURITY_INCIDENT': '🔒 Security',
        'EVACUATION': '🚪 Evacuation',
        'TECHNICAL_FAILURE': '⚙️ Technical',
        'POWER_FAILURE': '⚡ Power',
        'OTHER': '📋 Other'
    };
    return map[type] || type;
}

function formatStatus(status) {
    return status?.replace(/_/g, ' ') || '';
}

function formatReportType(type) {
    const map = {
        'SHOW_REPORT': 'Show Report',
        'THEATRE_REPORT': 'Theatre Report',
        'TICKET_HOLDER_REPORT': 'Ticket Holders',
        'INCIDENT_REPORT': 'Incident Report'
    };
    return map[type] || type;
}

function getSeverityColor(severity) {
    const map = { 'LOW': 'info', 'MEDIUM': 'warning', 'HIGH': 'danger', 'CRITICAL': 'danger' };
    return map[severity] || 'info';
}

function getStatusColor(status) {
    const map = { 'OPEN': 'danger', 'UNDER_INVESTIGATION': 'warning', 'RESOLVED': 'success', 'CLOSED': 'info' };
    return map[status] || 'info';
}

// ================= CUSTOMER 360 =================

async function opsSearchCustomers() {
    const query = document.getElementById('opsCustSearchInput')?.value?.trim();
    if (!query) return;

    const container = document.getElementById('opsCustSearchResults');
    container.innerHTML = '<p style="color: var(--text-muted);">Searching...</p>';

    try {
        const result = await opsApiCall(`/customers/search?q=${encodeURIComponent(query)}`);
        if (!result) return;

        const customers = result.data || [];
        if (customers.length === 0) {
            container.innerHTML = '<p style="color: var(--text-muted);">No customers found matching "' + query + '".</p>';
            return;
        }

        container.innerHTML = customers.map(c => `
            <div class="admin-card" style="cursor: pointer; margin-bottom: 12px; border-left: 3px solid var(--primary-color);" onclick="opsLoadCustomerProfile(${c.id})">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <div>
                        <h4 style="color: var(--text-primary); margin: 0 0 4px 0;">${c.name || 'Unknown'}</h4>
                        <p style="color: var(--text-muted); font-size: 13px; margin: 0;">
                            ${c.email || ''} ${c.phone ? '| ' + c.phone : ''}
                        </p>
                    </div>
                    <div style="text-align: right;">
                        <p style="color: var(--text-primary); margin: 0; font-size: 18px; font-weight: 600;">₹${Number(c.totalSpent || 0).toLocaleString()}</p>
                        <p style="color: var(--text-muted); font-size: 12px; margin: 0;">${c.totalBookings || 0} bookings</p>
                    </div>
                </div>
            </div>
        `).join('');
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
}

async function opsLoadCustomerProfile(customerId) {
    document.getElementById('opsCustomerSearch').classList.add('hidden');
    const profileDiv = document.getElementById('opsCustomerProfile');
    profileDiv.classList.remove('hidden');
    const content = document.getElementById('opsCustProfileContent');
    content.innerHTML = '<p style="color: var(--text-muted);">Loading profile...</p>';

    try {
        const result = await opsApiCall(`/customers/${customerId}`);
        if (!result) return;

        const r = result.data;
        content.innerHTML = `
            <!-- Customer Summary -->
            <div class="admin-card" style="margin-bottom: 16px;">
                <h3 style="margin-bottom: 16px;">${r.name}</h3>
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px;">
                    <div><span style="color: var(--text-muted);">Customer ID:</span> <strong>CUST-${r.id}</strong></div>
                    <div><span style="color: var(--text-muted);">Email:</span> ${r.email || '—'}</div>
                    <div><span style="color: var(--text-muted);">Phone:</span> ${r.phone || '—'}</div>
                    <div><span style="color: var(--text-muted);">Date of Birth:</span> ${r.dateOfBirth || '—'}</div>
                    <div><span style="color: var(--text-muted);">Account Created:</span> ${r.createdAt ? new Date(r.createdAt).toLocaleDateString() : '—'}</div>
                </div>
            </div>

            <!-- Stats -->
            <div class="kpi-grid" style="grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); margin-bottom: 16px;">
                <div class="kpi-card"><div class="kpi-value">${r.totalBookings}</div><div class="kpi-label">Total Bookings</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.confirmedBookings}</div><div class="kpi-label">Confirmed</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.cancelledBookings}</div><div class="kpi-label">Cancelled</div></div>
                <div class="kpi-card"><div class="kpi-value">${r.expiredBookings}</div><div class="kpi-label">Expired</div></div>
                <div class="kpi-card"><div class="kpi-value">₹${Number(r.totalSpent || 0).toLocaleString()}</div><div class="kpi-label">Total Spent</div></div>
                <div class="kpi-card"><div class="kpi-value">₹${Number(r.avgBookingValue || 0).toLocaleString()}</div><div class="kpi-label">Avg Booking</div></div>
            </div>

            <!-- Patterns -->
            <div class="admin-card" style="margin-bottom: 16px;">
                <h4 style="margin-bottom: 8px;">📊 Behavioural Patterns</h4>
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 8px; font-size: 13px;">
                    <div><span style="color: var(--text-muted);">Favourite Theatre:</span> <strong>${r.favouriteTheatre || '—'}</strong></div>
                    <div><span style="color: var(--text-muted);">Preferred Format:</span> <strong>${r.preferredFormat || '—'}</strong></div>
                    <div><span style="color: var(--text-muted);">Preferred Time:</span> <strong>${r.preferredTimeSlot || '—'}</strong></div>
                    <div><span style="color: var(--text-muted);">Weekend Bookings:</span> <strong>${r.weekendBookings}</strong></div>
                    <div><span style="color: var(--text-muted);">Weekday Bookings:</span> <strong>${r.weekdayBookings}</strong></div>
                </div>
            </div>

            <!-- Booking History -->
            <div class="admin-card">
                <h4 style="margin-bottom: 12px;">🎟️ Booking History (${r.bookings?.length || 0})</h4>
                ${r.bookings && r.bookings.length > 0 ? `
                    <div class="table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>Booking</th>
                                    <th>Movie</th>
                                    <th>Theatre</th>
                                    <th>Show</th>
                                    <th>Seats</th>
                                    <th>Amount</th>
                                    <th>Status</th>
                                    <th>Details</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${r.bookings.map(b => `
                                    <tr>
                                        <td>#${b.bookingId}</td>
                                        <td>${b.movieTitle || '—'}</td>
                                        <td>${b.theatreName || '—'}</td>
                                        <td>${b.showStartTime ? new Date(b.showStartTime).toLocaleString() : '—'}</td>
                                        <td>${b.seatCodes || '—'}</td>
                                        <td>₹${Number(b.totalAmount || 0).toLocaleString()}</td>
                                        <td><span class="badge badge-${b.bookingStatus === 'CONFIRMED' ? 'success' : b.bookingStatus === 'CANCELLED' ? 'danger' : 'warning'}">${b.bookingStatus}</span></td>
                                        <td>
                                            ${b.attendees && b.attendees.length > 0 ? 
                                                `<button class="btn btn-secondary btn-sm" onclick="opsToggleAttendees(this)" style="font-size: 11px;">👁 View</button>
                                                <div class="ops-attendee-list hidden" style="margin-top: 8px; font-size: 12px;">
                                                    ${b.attendees.map(a => 
                                                        `<div style="padding: 4px 0; border-bottom: 1px solid var(--border-color);">
                                                            <strong>${a.seatCode}</strong> — ${a.attendeeName || 'Not specified'}
                                                            ${a.phone ? ' | ' + a.phone : ''}
                                                            ${a.dateOfBirth ? ' | DOB: ' + a.dateOfBirth : ''}
                                                            ${a.isSelf ? ' <span class="badge badge-info" style="font-size:10px;">Self</span>' : ''}
                                                        </div>`
                                                    ).join('')}
                                                </div>`
                                            : '<span style="color: var(--text-muted); font-size: 11px;">No attendees</span>'
                                        }
                                    </td>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                ` : '<p style="color: var(--text-muted);">No bookings found.</p>'}
            </div>
        `;
    } catch (e) {
        content.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
}

function opsBackToCustomerSearch() {
    document.getElementById('opsCustomerSearch').classList.remove('hidden');
    document.getElementById('opsCustomerProfile').classList.add('hidden');
}

function opsToggleAttendees(btn) {
    const list = btn.nextElementSibling;
    if (list) {
        list.classList.toggle('hidden');
        btn.textContent = list.classList.contains('hidden') ? '👁 View' : '🙈 Hide';
    }
}

// ================= SCREEN UTILISATION =================

async function opsLoadUtilisation() {
    const theatreId = OPS_API_BASE === '/api/admin'
        ? document.getElementById('opsUtilTheatre')?.value
        : null;
    const dateFrom = document.getElementById('opsUtilDateFrom')?.value || '';
    const dateTo = document.getElementById('opsUtilDateTo')?.value || '';

    const container = document.getElementById('opsUtilOverviewResult');
    if (!container) return;
    container.innerHTML = '<p style="color: var(--text-muted);">Loading...</p>';

    try {
        let endpoint;
        if (OPS_API_BASE === '/api/admin') {
            if (!theatreId) { container.innerHTML = '<p style="color: #ff5252;">Please select a theatre.</p>'; return; }
            endpoint = `/utilisation?theatreId=${theatreId}&dateFrom=${dateFrom}&dateTo=${dateTo}`;
        } else {
            endpoint = `/utilisation?dateFrom=${dateFrom}&dateTo=${dateTo}`;
        }

        const result = await opsApiCall(endpoint);
        if (!result) return;

        const r = result.data;
        if (!r.screens || r.screens.length === 0) {
            container.innerHTML = '<p style="color: var(--text-muted);">No screens found for this theatre.</p>';
            return;
        }

        const getStatusColor = (status) => {
            if (status === 'GREEN') return '#4caf50';
            if (status === 'YELLOW') return '#ff9800';
            return '#f44336';
        };

        const getStatusLabel = (status) => {
            if (status === 'GREEN') return 'Healthy';
            if (status === 'YELLOW') return 'Underutilised';
            return 'Wasted';
        };

        container.innerHTML = `
            <h4 style="color: var(--text-primary); margin-bottom: 4px;">${r.theatreName}</h4>
            <p style="color: var(--text-muted); font-size: 13px; margin-bottom: 16px;">${r.dateFrom} to ${r.dateTo}</p>
            <div style="margin-bottom: 24px;">
                ${r.screens.map(s => `
                    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px; padding: 12px; background: var(--bg-secondary); border-radius: 8px; border-left: 4px solid ${getStatusColor(s.status)};">
                        <div style="flex: 1;">
                            <div style="font-weight: 600; color: var(--text-primary);">${s.screenName} <span style="font-weight: 400; font-size: 12px; color: var(--text-muted);">(${s.totalSeats} seats)</span></div>
                            <div style="font-size: 12px; color: var(--text-muted); margin-top: 2px;">${s.showCount} shows • ${s.activeHours}h active / ${s.operationalHours}h available</div>
                        </div>
                        <div style="text-align: right; min-width: 100px;">
                            <div style="font-size: 24px; font-weight: 700; color: ${getStatusColor(s.status)};">${s.utilisationPercent}%</div>
                            <div style="font-size: 11px; color: ${getStatusColor(s.status)};">${getStatusLabel(s.status)}</div>
                        </div>
                        <div style="width: 120px; height: 8px; background: var(--bg-primary); border-radius: 4px; overflow: hidden;">
                            <div style="width: ${Math.min(s.utilisationPercent, 100)}%; height: 100%; background: ${getStatusColor(s.status)}; border-radius: 4px;"></div>
                        </div>
                    </div>
                `).join('')}
            </div>
            <p style="color: var(--text-muted); font-size: 11px;">🟢 Healthy (≥60%) • 🟡 Underutilised (30-59%) • 🔴 Wasted (&lt;30%)</p>
        `;
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
}

// ================= CONFLICT CHECK =================

async function opsCheckConflicts() {
    const screenId = document.getElementById('opsConflictScreen')?.value;
    const movieId = document.getElementById('opsConflictMovie')?.value;
    const date = document.getElementById('opsConflictDate')?.value;
    const time = document.getElementById('opsConflictTime')?.value;

    if (!screenId || !movieId || !date || !time) {
        alert('Please fill in all fields.');
        return;
    }

    const container = document.getElementById('opsConflictResult');
    container.innerHTML = '<p style="color: var(--text-muted);">Checking...</p>';

    // Get duration from the selected movie option
    const movieSelect = document.getElementById('opsConflictMovie');
    const selectedOption = movieSelect.options[movieSelect.selectedIndex];
    const duration = selectedOption?.getAttribute('data-duration') || 150;

    const startTime = `${date}T${time}:00`;

    try {
        const endpoint = `/conflicts?screenId=${screenId}&startTime=${encodeURIComponent(startTime)}&durationMinutes=${duration}`;
        const result = await opsApiCall(endpoint);
        if (!result) return;

        const r = result.data;
        container.innerHTML = `
            <div style="padding: 16px; border-radius: 8px; ${r.hasConflict ? 'background: rgba(244,67,54,0.1); border: 1px solid rgba(244,67,54,0.3);' : 'background: rgba(76,175,80,0.1); border: 1px solid rgba(76,175,80,0.3);'}">
                <p style="font-weight: 600; margin: 0 0 4px 0; color: ${r.hasConflict ? '#f44336' : '#4caf50'};">${r.message}</p>
                <p style="font-size: 12px; color: var(--text-muted); margin: 0;">${r.screenName} • ${r.proposedStart} to ${r.proposedEnd}</p>
                ${r.conflicts && r.conflicts.length > 0 ? `
                    <div style="margin-top: 12px;">
                        ${r.conflicts.map(c => `
                            <div style="padding: 8px; background: rgba(244,67,54,0.05); border-radius: 4px; margin-top: 6px; font-size: 13px;">
                                <strong>${c.movieTitle}</strong> — ${c.showStartTime} to ${c.showEndTime}
                            </div>
                        `).join('')}
                    </div>
                ` : ''}
            </div>
        `;
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
}

// Auto-fill duration when movie is selected
function opsOnConflictMovieChange() {
    const movieSelect = document.getElementById('opsConflictMovie');
    const durationInput = document.getElementById('opsConflictDuration');
    if (!movieSelect || !durationInput) return;
    const selectedOption = movieSelect.options[movieSelect.selectedIndex];
    const duration = selectedOption?.getAttribute('data-duration');
    durationInput.value = duration ? `${duration} min` : '';
}
