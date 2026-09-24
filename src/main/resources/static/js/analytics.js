/**
 * Analytics Module — Vanilla JS + Chart.js
 * Backend returns clean { labels, values } data.
 * This file handles all Chart.js rendering and filter interactions.
 */

/**
 * Which analytics API this page talks to. Each portal declares itself on
 * <body data-portal="...">, so the analytics bar can never call the *other*
 * portal's endpoints: a theatre owner hitting /api/admin/analytics/** is rejected
 * by Spring Security, and that is exactly how "HTTP 403 while loading filter
 * options" used to appear while the rest of the owner page worked fine.
 * The pathname is only a fallback for a page that does not declare itself.
 */
function resolveAnalyticsApiBase() {
    const declared = String(
        (document.body && document.body.dataset && document.body.dataset.portal) ||
        window.ANALYTICS_PORTAL || ''
    ).toUpperCase();

    if (declared.includes('OWNER')) return '/api/owner/analytics';
    if (declared.includes('ADMIN')) return '/api/admin/analytics';

    const path = String(window.location.pathname || '').toLowerCase();
    if (path.includes('owner')) return '/api/owner/analytics';
    if (path.includes('admin')) return '/api/admin/analytics';

    console.warn('[ANALYTICS] Portal could not be detected; defaulting to admin analytics.');
    return '/api/admin/analytics';
}

const ANALYTICS_API_BASE = resolveAnalyticsApiBase();
console.log('[ANALYTICS] Using API base', ANALYTICS_API_BASE);

/**
 * Renews the 15 minute access token from the stored refresh token. Returns true
 * only when a new access token was stored. Nothing in the portals used to call
 * /auth/refresh-token, so a session older than 15 minutes looked like a 403.
 */
async function refreshAccessToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return false;

    try {
        const res = await fetch('/auth/refresh-token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken })
        });
        if (!res.ok) return false;

        const result = await res.json();
        const data = result && result.data;
        if (!data || !data.accessToken) return false;

        localStorage.setItem('accessToken', data.accessToken);
        if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
        console.log('[ANALYTICS] Access token refreshed');
        return true;
    } catch (err) {
        console.warn('[ANALYTICS] Token refresh failed:', err);
        return false;
    }
}

/**
 * Fetches an analytics endpoint with the bearer token, retrying once after a
 * silent token refresh when the server answers 401. Resolves with both the
 * response and the URL that was requested, so a failure can name it.
 */
async function analyticsFetch(endpoint) {
    const url = `${ANALYTICS_API_BASE}${endpoint}`;
    const withToken = () => fetch(url, {
        headers: { 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` }
    });

    let res = await withToken();
    if (res.status === 401 && await refreshAccessToken()) {
        res = await withToken();
    }
    return { res, url };
}

/**
 * The message a rejected request deserves. A rejection carries no useful body, so
 * the status and the URL are the only evidence there is; naming both is what turns
 * an opaque "HTTP 403" into something actionable.
 */
function describeFetchFailure(url, status, fallback) {
    if (status === 401) {
        return url + ' answered 401 (unauthorized): your session has expired. Sign in again.';
    }
    if (status === 403) {
        return url + ' answered 403 (forbidden): this account cannot load analytics for this '
             + 'portal. Sign in with a theatre-owner or admin account.';
    }
    return fallback || (url + ' answered HTTP ' + status + '.');
}

// Chart instances (for cleanup on re-render)
const charts = {};

// Current filter state
let currentFilter = {
    dateFrom: defaultDateFrom(),
    dateTo: defaultDateTo(),
    movieId: null,
    theatreId: null,
    screenId: null,
    cityId: null,
    format: null,
    language: null
};

function defaultDateFrom() {
    const d = new Date();
    d.setDate(d.getDate() - 30);
    return d.toISOString().split('T')[0];
}

function defaultDateTo() {
    return new Date().toISOString().split('T')[0];
}

// ==================== INIT ====================

/**
 * Returns false when the filter options could not be loaded, so the caller can
 * leave the analytics tab un-latched and let a later visit try again.
 */
async function initAnalytics() {
    const filtersOk = await loadFilterOptions();
    applyFilters();
    return filtersOk;
}

// ==================== FILTERS ====================

// Raw filter options, kept so the cascade can rebuild a child list without
// asking the API again.
let analyticsFilterData = {
    movies: [], theatres: [], screens: [], cities: [], formats: [], languages: []
};

async function loadFilterOptions() {
    try {
        const { res, url } = await analyticsFetch('/filters');

        let result = null;
        try {
            result = await res.json();
        } catch (parseErr) {
            // Nothing parsable came back (Spring Security rejects with an empty body).
            throw new Error(describeFetchFailure(url, res.status,
                url + ' returned HTTP ' + res.status + ' while loading filter options.'));
        }

        // Previously this returned silently, which left every dropdown on the bar
        // looking broken (no options at all). A failure is now reported, naming the
        // URL that failed so a wrong API base is obvious.
        if (!res.ok || !result || result.success === false || !result.data) {
            throw new Error((result && result.message) || describeFetchFailure(url, res.status,
                url + ' returned HTTP ' + res.status + ' while loading filter options.'));
        }

        const f = result.data;
        analyticsFilterData = {
            movies: f.movies || [],
            theatres: f.theatres || [],
            screens: f.screens || [],
            cities: f.cities || [],
            formats: (f.formats || []).map(fmt => ({ id: fmt.name, name: String(fmt.name).replace('_', ' ') })),
            languages: (f.languages || []).map(lang => ({ id: lang.name, name: lang.name }))
        };

        // Empty lists say so rather than rendering a dropdown with no options.
        populateSelect('filterCity', analyticsFilterData.cities, 'All Cities', 'No cities with shows yet');
        populateSelect('filterMovie', analyticsFilterData.movies, 'All Movies', 'No movies scheduled yet');
        populateSelect('filterFormat', analyticsFilterData.formats, 'All Formats', 'No formats available');
        populateSelect('filterLanguage', analyticsFilterData.languages, 'All Languages', 'No languages available');
        reloadAnalyticsCascade();

        analyticsNotices.filters = '';
        analyticsNotices.info = f.notice || '';
        renderAnalyticsNotices();
        return true;
    } catch (err) {
        console.error('[ANALYTICS] Failed to load filter options:', err);
        // Never leave the whole bar unusable: formats and languages mirror the
        // server enums, so they stay usable while every dropdown that can only be
        // filled from the database is labelled rather than silently empty.
        populateStaticFilterOptions();
        analyticsNotices.filters = 'Filter options could not be loaded, so the dropdowns below are empty. '
            + 'Formats and languages fall back to the supported list. ' + err.message;
        renderAnalyticsNotices();
        return false;
    }
}

/**
 * Fallback used when the filter endpoint fails: static lists mirroring MovieFormat
 * and AudioLanguage on the server, plus an explicit label on every dropdown that
 * could only come from the database. The bar then still explains itself instead of
 * rendering every dropdown empty with no reason given.
 */
function populateStaticFilterOptions() {
    analyticsFilterData.formats = ['TWO_D', 'THREE_D', 'IMAX_2D', 'IMAX_3D', 'FOUR_DX']
        .map(name => ({ id: name, name: name.replace('_', ' ') }));
    analyticsFilterData.languages = ['ENGLISH', 'TAMIL', 'HINDI', 'TELUGU', 'KANNADA', 'MALAYALAM']
        .map(name => ({ id: name, name: name }));

    analyticsFilterData.movies = [];
    analyticsFilterData.theatres = [];
    analyticsFilterData.screens = [];
    analyticsFilterData.cities = [];

    populateSelect('filterFormat', analyticsFilterData.formats, 'All Formats', 'No formats available');
    populateSelect('filterLanguage', analyticsFilterData.languages, 'All Languages', 'No languages available');
    populateSelect('filterCity', [], 'All Cities', 'Could not be loaded');
    populateSelect('filterMovie', [], 'All Movies', 'Could not be loaded');

    reloadAnalyticsCascade();

    // The cascade labels an empty list as "none in this city", which would be
    // misleading when the real reason is that the request failed.
    populateSelect('filterTheatre', [], 'All Theatres', 'Could not be loaded');
    populateSelect('filterScreen', [], 'All Screens', 'Could not be loaded');
}

// ==================== FILTER BAR NOTICES ====================
//
// Two things can go wrong on the analytics bar — the filter list failing to
// load, and a backwards date range — and they must be able to be shown at the
// same time without one wiping the other.

const analyticsNotices = { filters: '', dates: '', info: '' };

function renderAnalyticsNotices() {
    const box = document.getElementById('analyticsFilterError');
    if (!box) return;

    box.innerHTML = '';
    const parts = [];
    if (analyticsNotices.info) parts.push('ℹ️ ' + analyticsNotices.info);
    if (analyticsNotices.filters) parts.push('⚠️ ' + analyticsNotices.filters);
    if (analyticsNotices.dates) parts.push('⚠️ ' + analyticsNotices.dates);

    if (parts.length === 0) {
        box.classList.add('hidden');
        return;
    }

    const text = document.createElement('span');
    text.textContent = parts.join(' ');
    box.appendChild(text);

    // Give the user a way out of a failed load without reloading the page.
    if (analyticsNotices.filters) {
        const retry = document.createElement('button');
        retry.type = 'button';
        retry.className = 'btn btn-secondary btn-sm';
        retry.style.marginLeft = '12px';
        retry.textContent = 'Retry';
        retry.onclick = () => loadFilterOptions().then(ok => { if (ok) applyFilters(); });
        box.appendChild(retry);
    }

    box.classList.remove('hidden');
}

function populateSelect(elementId, items, defaultLabel, emptyLabel) {
    const sel = document.getElementById(elementId);
    if (!sel) return;
    const previous = sel.value;
    const label = (emptyLabel && items.length === 0) ? emptyLabel : defaultLabel;

    sel.innerHTML = `<option value="">${label}</option>`;
    items.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.id || '';
        opt.textContent = item.name;
        sel.appendChild(opt);
    });

    // A previously chosen value survives only while it is still in the list.
    sel.value = items.some(i => String(i.id) === String(previous)) ? previous : '';
}

/**
 * City -> Theatre -> Screen for the analytics bar. Selecting a city narrows the
 * theatre list, selecting a theatre narrows the screens, and anything that is no
 * longer reachable is dropped instead of being sent to the API.
 */
function reloadAnalyticsCascade() {
    const cityEl = document.getElementById('filterCity');
    const theatreEl = document.getElementById('filterTheatre');
    const screenEl = document.getElementById('filterScreen');
    const data = analyticsFilterData;

    const cityId = cityEl ? cityEl.value : '';

    if (theatreEl) {
        const previousTheatre = theatreEl.value;
        const theatres = data.theatres.filter(t => !cityId || String(t.parentId) === String(cityId));
        populateSelect('filterTheatre', theatres, 'All Theatres', 'No theatres in this city');
        if (!theatres.some(t => String(t.id) === String(previousTheatre))) {
            theatreEl.value = '';
        }
    }

    if (screenEl) {
        const effectiveTheatre = theatreEl ? theatreEl.value : '';
        const theatresInCity = cityId
            ? new Set(data.theatres.filter(t => String(t.parentId) === String(cityId)).map(t => String(t.id)))
            : null;

        const screens = data.screens.filter(s => {
            if (effectiveTheatre) return String(s.parentId) === String(effectiveTheatre);
            if (theatresInCity) return theatresInCity.has(String(s.parentId));
            return true;
        });
        populateSelect('filterScreen', screens, 'All Screens', 'No screens match');
    }
}

/** Called by the City / Theatre selects in the markup. */
function onAnalyticsFilterChange(changed) {
    const theatreEl = document.getElementById('filterTheatre');
    const screenEl = document.getElementById('filterScreen');
    if (changed === 'city' && theatreEl) theatreEl.value = '';
    if ((changed === 'city' || changed === 'theatre') && screenEl) screenEl.value = '';
    reloadAnalyticsCascade();
}

/**
 * Guards against the most common "unusual" input: a backwards date range, which
 * would otherwise come back as an empty dashboard with no explanation.
 */
function validateAnalyticsDates() {
    const fromEl = document.getElementById('filterDateFrom');
    const toEl = document.getElementById('filterDateTo');
    const from = (fromEl && fromEl.value) || '';
    const to = (toEl && toEl.value) || '';

    const message = (from && to && from > to)
        ? `Start date (${from}) cannot be after end date (${to}). Pick a valid range.`
        : '';

    analyticsNotices.dates = message;

    // Mark the two date boxes as well, so the message and the fields agree.
    if (message) {
        markFieldError([fromEl, toEl], message);
    } else {
        clearFieldError([fromEl, toEl]);
    }

    renderAnalyticsNotices();
    return !message;
}

function collectFilters() {
    currentFilter.dateFrom = document.getElementById('filterDateFrom')?.value || defaultDateFrom();
    currentFilter.dateTo = document.getElementById('filterDateTo')?.value || defaultDateTo();
    currentFilter.movieId = document.getElementById('filterMovie')?.value || null;
    currentFilter.theatreId = document.getElementById('filterTheatre')?.value || null;
    currentFilter.screenId = document.getElementById('filterScreen')?.value || null;
    currentFilter.cityId = document.getElementById('filterCity')?.value || null;
    currentFilter.format = document.getElementById('filterFormat')?.value || null;
    currentFilter.language = document.getElementById('filterLanguage')?.value || null;
}

function buildQueryString() {
    const params = new URLSearchParams();
    if (currentFilter.dateFrom) params.set('dateFrom', currentFilter.dateFrom);
    if (currentFilter.dateTo) params.set('dateTo', currentFilter.dateTo);
    if (currentFilter.movieId) params.set('movieId', currentFilter.movieId);
    if (currentFilter.theatreId) params.set('theatreId', currentFilter.theatreId);
    if (currentFilter.screenId) params.set('screenId', currentFilter.screenId);
    if (currentFilter.cityId) params.set('cityId', currentFilter.cityId);
    if (currentFilter.format) params.set('format', currentFilter.format);
    if (currentFilter.language) params.set('language', currentFilter.language);
    return params.toString();
}

async function applyFilters() {
    collectFilters();
    if (!validateAnalyticsDates()) return;
    const tasks = [
        ["Dashboard", loadDashboard()],
        ["RevenueTrend", loadRevenueTrend()],
        ["RevenueBreakdown", loadRevenueBreakdown()],
        ["MoviePerformance", loadMoviePerformance()],
        ["TheatrePerformance", loadTheatrePerformance()],
        ["ScreenPerformance", loadScreenPerformance()],
        ["ShowPerformance", loadShowPerformance()],
        ["TimeSlot", loadTimeSlotPerformance()],
        ["DayOfWeek", loadDayOfWeekPerformance()],
        ["Format", loadFormatPerformance()],
        ["Language", loadLanguagePerformance()]
    ];
    for (const [name, task] of tasks) {
        try { await task; }
        catch (err) { console.error("[ANALYTICS] " + name + " failed:", err); }
    }
}

// ==================== API HELPER ====================

async function analyticsApiCall(endpoint) {
    const qs = buildQueryString();
    const separator = endpoint.includes('?') ? '&' : '?';

    // Same token handling as the filter bar: one silent refresh attempt on 401.
    const { res, url } = await analyticsFetch(endpoint + (qs ? separator + qs : ''));

    if (!res.ok) {
        const errBody = await res.text();
        console.error(`[ANALYTICS] ${endpoint} returned HTTP ${res.status}:`, errBody);
        return null;
    }

    const result = await res.json();
    if (!result.success) {
        console.warn(`[ANALYTICS] ${endpoint} success=false:`, result);
        return null;
    }
    console.log(`[ANALYTICS] ${endpoint} OK`, result.data);
    return result.data;

}

// ==================== DASHBOARD ====================

async function loadDashboard() {
    const data = await analyticsApiCall('/dashboard');
    if (!data) return;

    setText('kpiRevenue', formatCurrency(data.totalRevenue));
    setText('kpiTickets', formatNumber(data.totalTicketsSold));
    setText('kpiShows', formatNumber(data.totalShows));
    setText('kpiTheatres', formatNumber(data.totalTheatres));
    setText('kpiAvgPrice', formatCurrency(data.avgTicketPrice));
    setText('kpiOccupancy', data.avgOccupancyPct + '%');
    setText('kpiRevenuePerShow', formatCurrency(data.revenuePerShow));
}

// ==================== REVENUE TREND ====================

async function loadRevenueTrend() {
    const data = await analyticsApiCall('/revenue?granularity=daily');
    if (!data) return;

    renderLineChart('revenueTrendChart', data.labels, data.values, 'Revenue (₹)', '#6c5ce7');
}

// ==================== REVENUE BREAKDOWN ====================

async function loadRevenueBreakdown() {
    const data = await analyticsApiCall('/revenue/breakdown?dimension=movie');
    if (!data || !data.items) return;

    const labels = data.items.slice(0, 10).map(i => i.name);
    const values = data.items.slice(0, 10).map(i => i.revenue);

    renderBarChart('revenueBreakdownChart', labels, values, 'Revenue (₹)');
}

// ==================== MOVIE PERFORMANCE ====================

async function loadMoviePerformance() {
    const data = await analyticsApiCall('/movies');
    if (!data || !data.movies) return;

    const tbody = document.getElementById('moviePerformanceBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    data.movies.forEach(m => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td style="font-weight: 600;">${escapeHtml(m.title)}</td>
            <td>${formatCurrency(m.revenue)}</td>
            <td>${formatNumber(m.ticketsSold)}</td>
            <td>${formatNumber(m.showCount)}</td>
            <td>${renderOccupancyBar(m.occupancyPct)}</td>
            <td>${formatCurrency(m.avgTicketPrice)}</td>
            <td style="color: #a1a1aa;">${escapeHtml(m.bestTheatre || 'N/A')}</td>
        `;
        tbody.appendChild(row);
    });
}

// ==================== THEATRE PERFORMANCE ====================

async function loadTheatrePerformance() {
    const data = await analyticsApiCall('/theatres');
    if (!data || !data.theatres) return;

    const tbody = document.getElementById('theatrePerformanceBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    data.theatres.forEach(t => {
        const isUnderperforming = parseFloat(t.occupancyPct) < 40;
        const row = document.createElement('tr');
        if (isUnderperforming) row.classList.add('underperforming');
        row.innerHTML = `
            <td style="font-weight: 600;">${escapeHtml(t.name)}</td>
            <td style="color: #a1a1aa;">${escapeHtml(t.city)}</td>
            <td>${formatCurrency(t.revenue)}</td>
            <td>${formatNumber(t.ticketsSold)}</td>
            <td>${formatNumber(t.showCount)}</td>
            <td>${formatNumber(t.screenCount)}</td>
            <td>${renderOccupancyBar(t.occupancyPct)}</td>
            <td>${formatCurrency(t.revenuePerShow)}</td>
        `;
        tbody.appendChild(row);
    });
}

// ==================== SCREEN PERFORMANCE ====================

async function loadScreenPerformance() {
    const data = await analyticsApiCall('/screens');
    if (!data || !data.screens) return;

    const tbody = document.getElementById('screenPerformanceBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    data.screens.forEach(s => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td style="font-weight: 600;">${escapeHtml(s.name)}</td>
            <td style="color: #a1a1aa;">${escapeHtml(s.theatreName)}</td>
            <td>${formatCurrency(s.revenue)}</td>
            <td>${formatNumber(s.ticketsSold)}</td>
            <td>${formatNumber(s.showCount)}</td>
            <td>${renderOccupancyBar(s.occupancyPct)}</td>
            <td>${formatCurrency(s.revenuePerShow)}</td>
            <td>${formatCurrency(s.avgTicketPrice)}</td>
        `;
        tbody.appendChild(row);
    });
}

// ==================== SHOW PERFORMANCE ====================

async function loadShowPerformance() {
    const data = await analyticsApiCall('/shows');
    if (!data || !data.shows) return;

    const tbody = document.getElementById('showPerformanceBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    data.shows.slice(0, 50).forEach(s => {
        const startTime = new Date(s.startTime);
        const timeStr = startTime.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
        const row = document.createElement('tr');
        row.innerHTML = `
            <td style="font-weight: 600;">${escapeHtml(s.movieTitle)}</td>
            <td style="color: #a1a1aa;">${escapeHtml(s.theatreName)}</td>
            <td style="color: #a1a1aa;">${escapeHtml(s.screenName)}</td>
            <td>${timeStr}</td>
            <td><span class="badge badge-pill" style="font-size:0.7rem;">${escapeHtml(s.format)}</span></td>
            <td>${formatNumber(s.ticketsSold)} / ${formatNumber(s.totalSeats)}</td>
            <td>${renderOccupancyBar(s.occupancyPct)}</td>
            <td>${formatCurrency(s.revenue)}</td>
        `;
        tbody.appendChild(row);
    });
}

// ==================== TIME SLOT PERFORMANCE ====================

async function loadTimeSlotPerformance() {
    const data = await analyticsApiCall('/timeslots');
    if (!data || !data.slots) return;

    const labels = data.slots.map(s => s.slot);
    const occupancy = data.slots.map(s => s.avgOccupancyPct);
    const revenue = data.slots.map(s => s.totalRevenue);

    renderBarChart('timeSlotChart', labels, occupancy, 'Avg Occupancy (%)');
    renderBarChart('timeSlotRevenueChart', labels, revenue, 'Revenue (₹)');
}

// ==================== DAY OF WEEK ====================

async function loadDayOfWeekPerformance() {
    const data = await analyticsApiCall('/dayofweek');
    if (!data || !data.days) return;

    const labels = data.days.map(d => d.dayName);
    const occupancy = data.days.map(d => d.avgOccupancyPct);
    const revenue = data.days.map(d => d.totalRevenue);

    renderBarChart('dayOfWeekChart', labels, occupancy, 'Avg Occupancy (%)');
    renderBarChart('dayOfWeekRevenueChart', labels, revenue, 'Revenue (₹)');
}

// ==================== FORMAT PERFORMANCE ====================

async function loadFormatPerformance() {
    const data = await analyticsApiCall('/formats');
    if (!data || !data.formats) return;

    const labels = data.formats.map(f => f.format.replace('_', ' '));
    const values = data.formats.map(f => f.revenue);

    renderBarChart('formatChart', labels, values, 'Revenue (₹)');

    // Also populate table
    const tbody = document.getElementById('formatPerformanceBody');
    if (tbody) {
        tbody.innerHTML = '';
        data.formats.forEach(f => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td style="font-weight: 600;">${escapeHtml(f.format.replace('_', ' '))}</td>
                <td>${formatCurrency(f.revenue)}</td>
                <td>${formatNumber(f.ticketsSold)}</td>
                <td>${formatNumber(f.showCount)}</td>
                <td>${renderOccupancyBar(f.occupancyPct)}</td>
                <td>${formatCurrency(f.avgTicketPrice)}</td>
            `;
            tbody.appendChild(row);
        });
    }
}

// ==================== LANGUAGE PERFORMANCE ====================

async function loadLanguagePerformance() {
    const data = await analyticsApiCall('/languages');
    if (!data || !data.languages) return;

    const labels = data.languages.map(l => l.language);
    const values = data.languages.map(l => l.revenue);

    renderBarChart('languageChart', labels, values, 'Revenue (₹)');

    // Also populate table
    const tbody = document.getElementById('languagePerformanceBody');
    if (tbody) {
        tbody.innerHTML = '';
        data.languages.forEach(l => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td style="font-weight: 600;">${escapeHtml(l.language)}</td>
                <td>${formatCurrency(l.revenue)}</td>
                <td>${formatNumber(l.ticketsSold)}</td>
                <td>${formatNumber(l.showCount)}</td>
                <td>${renderOccupancyBar(l.occupancyPct)}</td>
                <td>${formatCurrency(l.avgTicketPrice)}</td>
            `;
            tbody.appendChild(row);
        });
    }
}

// ==================== CHART HELPERS ====================

function renderLineChart(canvasId, labels, values, label, color) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    // Destroy existing chart
    if (charts[canvasId]) charts[canvasId].destroy();

    charts[canvasId] = new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: label,
                data: values,
                borderColor: color || '#6c5ce7',
                backgroundColor: (color || '#6c5ce7') + '20',
                fill: true,
                tension: 0.3,
                pointRadius: 3,
                pointHoverRadius: 6
            }]
        },
        options: chartOptions()
    });
}

function renderBarChart(canvasId, labels, values, label) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    // Destroy existing chart
    if (charts[canvasId]) charts[canvasId].destroy();

    charts[canvasId] = new Chart(canvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: label,
                data: values,
                backgroundColor: generateColors(values.length),
                borderRadius: 6,
                borderSkipped: false
            }]
        },
        options: chartOptions()
    });
}

function chartOptions() {
    return {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { display: false },
            tooltip: {
                backgroundColor: '#1a1a2e',
                titleColor: '#e4e4e7',
                bodyColor: '#a1a1aa',
                borderColor: '#2d2d44',
                borderWidth: 1,
                padding: 12,
                callbacks: {
                    label: function(ctx) {
                        const val = ctx.parsed.y;
                        if (val >= 100000) return '₹' + (val / 100000).toFixed(1) + 'L';
                        if (val >= 1000) return '₹' + (val / 1000).toFixed(1) + 'K';
                        return '₹' + val.toFixed(0);
                    }
                }
            }
        },
        scales: {
            x: {
                ticks: { color: '#71717a', font: { size: 11 } },
                grid: { color: '#1f1f2e' }
            },
            y: {
                ticks: { color: '#71717a', font: { size: 11 } },
                grid: { color: '#1f1f2e' }
            }
        }
    };
}

function generateColors(count) {
    const palette = [
        '#6c5ce7', '#00cec9', '#fdcb6e', '#e17055', '#74b9ff',
        '#a29bfe', '#55efc4', '#fab1a0', '#81ecec', '#ffeaa7'
    ];
    const colors = [];
    for (let i = 0; i < count; i++) {
        colors.push(palette[i % palette.length]);
    }
    return colors;
}

// ==================== FORMATTING HELPERS ====================

function formatCurrency(val) {
    const num = parseFloat(val) || 0;
    if (num >= 100000) return '₹' + (num / 100000).toFixed(2) + 'L';
    if (num >= 1000) return '₹' + (num / 1000).toFixed(1) + 'K';
    return '₹' + num.toFixed(0);
}

function formatNumber(val) {
    const num = parseInt(val) || 0;
    return num.toLocaleString('en-IN');
}

function renderOccupancyBar(pct) {
    const val = parseFloat(pct) || 0;
    const colorClass = val >= 70 ? 'high' : val >= 40 ? 'medium' : 'low';
    return `
        <div class="occupancy-bar">
            <div class="bar"><div class="bar-fill ${colorClass}" style="width:${Math.min(val, 100)}%"></div></div>
            <span class="bar-text">${val}%</span>
        </div>
    `;
}

function setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
