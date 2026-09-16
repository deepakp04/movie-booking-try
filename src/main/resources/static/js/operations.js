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
}

// ================= SECTION NAVIGATION =================

function opsShowSection(sectionId) {
    document.querySelectorAll('.ops-section').forEach(el => el.classList.add('hidden'));
    const section = document.getElementById(sectionId);
    if (section) section.classList.remove('hidden');
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
        const scope = OPS_API_BASE === '/api/admin' ? 'all' : 'all';
        const result = await opsApiCall(`/shows?scope=${scope}`);
        if (!result) return;

        const shows = result.data || [];
        const selects = ['opsShowSelect', 'opsTHShowSelect'];
        selects.forEach(id => {
            const select = document.getElementById(id);
            if (!select) return;
            // Keep first option
            while (select.options.length > 1) select.remove(1);
            shows.forEach(show => {
                const opt = document.createElement('option');
                opt.value = show.id;
                const date = show.startTime ? new Date(show.startTime).toLocaleDateString() : '';
                opt.textContent = `${show.movieTitle || 'Movie'} — ${show.screenName || ''} — ${date}`;
                select.appendChild(opt);
            });
        });
    } catch (e) {
        console.error('Failed to load shows:', e);
    }
}

async function opsLoadTheatresDropdown() {
    try {
        const result = await opsApiCall('/theatres');
        if (!result) return;

        const theatres = result.data || [];
        const selects = ['opsTheatreSelect', 'incTheatre'];
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
    } catch (e) {
        console.error('Failed to load theatres:', e);
    }
}

// ================= FILTER OPTIONS (Admin only) =================

let opsFilterData = { theatres: [], movies: [], screens: [] };

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
    // Populate theatre filter
    const theatreSelect = document.getElementById('opsFilterTheatre');
    if (theatreSelect) {
        while (theatreSelect.options.length > 1) theatreSelect.remove(1);
        (opsFilterData.theatres || []).forEach(t => {
            const opt = document.createElement('option');
            opt.value = t.id;
            opt.textContent = t.name;
            theatreSelect.appendChild(opt);
        });
    }
    // Populate movie filter
    const movieSelect = document.getElementById('opsFilterMovie');
    if (movieSelect) {
        while (movieSelect.options.length > 1) movieSelect.remove(1);
        (opsFilterData.movies || []).forEach(m => {
            const opt = document.createElement('option');
            opt.value = m.title;
            opt.textContent = m.title;
            movieSelect.appendChild(opt);
        });
    }
    // Populate screen filter
    const screenSelect = document.getElementById('opsFilterScreen');
    if (screenSelect) {
        while (screenSelect.options.length > 1) screenSelect.remove(1);
        (opsFilterData.screens || []).forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.name + '|' + s.theatreName;
            opt.textContent = s.name + ' — ' + s.theatreName;
            screenSelect.appendChild(opt);
        });
    }
}

function opsApplyShowFilters() {
    const dateFrom = document.getElementById('opsFilterDateFrom')?.value || '';
    const dateTo = document.getElementById('opsFilterDateTo')?.value || '';
    const theatreId = document.getElementById('opsFilterTheatre')?.value || '';
    const movieTitle = document.getElementById('opsFilterMovie')?.value || '';
    const screenVal = document.getElementById('opsFilterScreen')?.value || '';

    let url = '/shows?scope=all';
    if (dateFrom) url += `&dateFrom=${dateFrom}`;
    if (dateTo) url += `&dateTo=${dateTo}`;
    if (theatreId) url += `&theatreId=${theatreId}`;

    opsApiCall(url).then(result => {
        if (!result) return;
        let shows = result.data || [];
        // Client-side filter for movie and screen (since we don't have server endpoints for these)
        if (movieTitle) shows = shows.filter(s => s.movieTitle === movieTitle);
        if (screenVal) {
            const [sName, tName] = screenVal.split('|');
            shows = shows.filter(s => s.screenName === sName && s.theatreName === tName);
        }
        populateShowsSelect(shows);
    }).catch(e => console.error('Failed to filter shows:', e));
}

function opsResetShowFilters() {
    const ids = ['opsFilterDateFrom', 'opsFilterDateTo', 'opsFilterTheatre', 'opsFilterMovie', 'opsFilterScreen'];
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    opsLoadShowsDropdown();
}

function opsApplyTHFilters() {
    const dateFrom = document.getElementById('opsTHFilterDateFrom')?.value || '';
    const dateTo = document.getElementById('opsTHFilterDateTo')?.value || '';
    const movieTitle = document.getElementById('opsTHFilterMovie')?.value || '';

    let url = '/shows?scope=all';
    if (dateFrom) url += `&dateFrom=${dateFrom}`;
    if (dateTo) url += `&dateTo=${dateTo}`;

    opsApiCall(url).then(result => {
        if (!result) return;
        let shows = result.data || [];
        if (movieTitle) shows = shows.filter(s => s.movieTitle === movieTitle);
        // Populate TH show select
        const select = document.getElementById('opsTHShowSelect');
        if (!select) return;
        while (select.options.length > 1) select.remove(1);
        shows.forEach(show => {
            const opt = document.createElement('option');
            opt.value = show.id;
            const date = show.startTime ? new Date(show.startTime).toLocaleDateString() : '';
            opt.textContent = `${show.movieTitle || 'Movie'} — ${show.screenName || ''} — ${date}`;
            select.appendChild(opt);
        });
    }).catch(e => console.error('Failed to filter TH shows:', e));
}

function opsResetTHFilters() {
    ['opsTHFilterDateFrom', 'opsTHFilterDateTo', 'opsTHFilterMovie'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    opsLoadShowsDropdown();
}

function populateShowsSelect(shows) {
    const selects = ['opsShowSelect', 'opsTHShowSelect'];
    selects.forEach(id => {
        const select = document.getElementById(id);
        if (!select) return;
        while (select.options.length > 1) select.remove(1);
        shows.forEach(show => {
            const opt = document.createElement('option');
            opt.value = show.id;
            const date = show.startTime ? new Date(show.startTime).toLocaleDateString() : '';
            opt.textContent = `${show.movieTitle || 'Movie'} — ${show.screenName || ''} — ${date}`;
            select.appendChild(opt);
        });
    });
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
                            ${r.ticketHolders.map(h => `
                                <tr>
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

async function opsLoadTicketHolders() {
    const showId = document.getElementById('opsTHShowSelect')?.value;
    if (!showId) return;

    const container = document.getElementById('opsTicketHoldersResult');
    container.classList.remove('hidden');
    container.innerHTML = '<p style="color: var(--text-muted);">Loading...</p>';

    try {
        const result = await opsApiCall(`/reports/ticket-holders?showId=${showId}`);
        if (!result) return;

        const holders = result.data || [];
        container.innerHTML = `
            <div style="display: flex; gap: 8px; margin-bottom: 16px;">
                <button class="btn btn-primary btn-sm" onclick="opsGenerateReport('TICKET_HOLDER_REPORT', ${showId})">Generate Report</button>
            </div>
            ${holders.length > 0 ? `
                <p style="color: var(--text-muted); font-size: 13px; margin-bottom: 8px;">${holders.length} confirmed ticket holder(s)</p>
                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
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
                            ${holders.map(h => `
                                <tr>
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
            ` : '<p style="color: var(--text-muted);">No confirmed ticket holders for this show.</p>'}
        `;
    } catch (e) {
        container.innerHTML = `<p style="color: #ff5252;">Error: ${e.message}</p>`;
    }
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
