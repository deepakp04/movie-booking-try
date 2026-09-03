/**
 * Analytics Module — Vanilla JS + Chart.js
 * Backend returns clean { labels, values } data.
 * This file handles all Chart.js rendering and filter interactions.
 */

// Detect which portal we're in (admin vs owner)
const ANALYTICS_API_BASE = (function() {
    if (window.location.pathname.includes('owner')) return '/api/owner/analytics';
    return '/api/admin/analytics';
})();

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

async function initAnalytics() {
    await loadFilterOptions();
    applyFilters();
}

// ==================== FILTERS ====================

async function loadFilterOptions() {
    try {
        const token = localStorage.getItem('accessToken');
        const res = await fetch(`${ANALYTICS_API_BASE}/filters`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const result = await res.json();
        if (!result.success) return;

        const f = result.data;

        // Populate movie filter
        populateSelect('filterMovie', f.movies, 'All Movies');

        // Populate city filter
        populateSelect('filterCity', f.cities, 'All Cities');

        // Populate format filter
        populateSelect('filterFormat',
            f.formats.map(fmt => ({ id: fmt.name, name: fmt.name.replace('_', ' ') })),
            'All Formats');

        // Populate language filter
        populateSelect('filterLanguage',
            f.languages.map(lang => ({ id: lang.name, name: lang.name })),
            'All Languages');

        // Theatre filter (admin only — owner doesn't need it)
        const theatreSelect = document.getElementById('filterTheatre');
        if (theatreSelect) {
            populateSelect('filterTheatre', f.theatres, 'All Theatres');
        }

        // Screen filter
        const screenSelect = document.getElementById('filterScreen');
        if (screenSelect) {
            populateSelect('filterScreen', f.screens, 'All Screens');
        }
    } catch (err) {
        console.error('[ANALYTICS] Failed to load filter options:', err);
    }
}

function populateSelect(elementId, items, defaultLabel) {
    const sel = document.getElementById(elementId);
    if (!sel) return;
    sel.innerHTML = `<option value="">${defaultLabel}</option>`;
    items.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.id || '';
        opt.textContent = item.name;
        sel.appendChild(opt);
    });
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
    const token = localStorage.getItem('accessToken');
    const qs = buildQueryString();
    const separator = endpoint.includes('?') ? '&' : '?';
    const url = `${ANALYTICS_API_BASE}${endpoint}${qs ? separator + qs : ''}`;

    const res = await fetch(url, {
        headers: { 'Authorization': `Bearer ${token}` }
    });

    if (res.status === 401 || res.status === 403) {
        console.warn(`[ANALYTICS] ${endpoint} returned ${res.status} — unauthorized`);
        return null;
    }

    if (!res.ok) {
        console.error(`[ANALYTICS] ${endpoint} returned HTTP ${res.status}`);
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
