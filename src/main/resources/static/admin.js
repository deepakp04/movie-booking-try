const API_BASE = '/api/admin';

// Initialize and verify authentication on boot
document.addEventListener('DOMContentLoaded', () => {
    if (checkAdminAuth()) {
        loadCities();
        loadMovies();
        loadTheatres();
        loadShows();
    }
});

function checkAdminAuth() {
    const token = localStorage.getItem('accessToken');
    if (!token) {
        window.location.href = '/auth.html';
        return false;
    }

    try {
        // Decode JWT payload to check role and display email
        const payload = JSON.parse(atob(token.split('.')[1]));
        const role = payload.role || payload.roles || '';
        
        // Ensure user holds ADMIN role
        if (!role.includes('ADMIN')) {
            alert('Access Denied. Admin privileges required.');
            handleLogout();
            return false;
        }

        // Display logged-in admin email in navbar if present
        const emailDisplay = document.getElementById('adminEmailDisplay');
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

// Global API Helper with JWT Authorization header
async function adminApiCall(endpoint, method = 'GET', body = null) {
    hideAlert();
    const token = localStorage.getItem('accessToken');
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
	
	//add it localhost in apibase and see if it works

    try {
        console.log(`[API REQUEST] ${method} ${API_BASE}${endpoint}`, body);

        const response = await fetch(`${API_BASE}${endpoint}`, {
            method,
            headers,
            cache: 'no-store', // never serve a stale cached GET; always hit the server
            body: body ? JSON.stringify(body) : null
        });

        console.log(`[API RESPONSE STATUS] ${response.status} ${response.statusText}`);

        if (response.status === 401 || response.status === 403) {
            showAlert('Session expired or unauthorized. Please log in again.', 'error');
            setTimeout(handleLogout, 1500);
            return null;
        }

        const result = await response.json();
        console.log(`[API RESPONSE BODY]`, result);

        if (!response.ok || (result.success !== undefined && !result.success)) {
            const errorMsg = result.message || result.error || `HTTP ${response.status} Error`;
            showAlert(errorMsg, 'error');
            throw new Error(errorMsg);
        }

        return result;
    } catch (err) {
        console.error(`[API ERROR] Endpoint: ${endpoint}`, err);
        throw err;
    }
}

// Prevents duplicate submissions from rapid/double clicks (or Enter spam).
// Disables the form's submit button for the duration of the async task, so a
// second click cannot fire a second POST before the first one resolves.
async function withSubmitGuard(formEl, task) {
    const btn = formEl ? formEl.querySelector('button[type="submit"], button:not([type])') : null;
    if (btn) {
        if (btn.dataset.busy === '1') return; // already running — ignore this click
        btn.dataset.busy = '1';
        btn.disabled = true;
    }
    try {
        await task();
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.dataset.busy = '0';
        }
    }
}

// Navigation Tab Switcher
let analyticsInitialized = false;

function switchTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.add('hidden'));
    document.querySelectorAll('.nav-tab').forEach(el => el.classList.remove('active'));

    const target = document.getElementById(tabId);
    if (target) {
        target.classList.remove('hidden');
    }
    if (event && event.currentTarget) {
        event.currentTarget.classList.add('active');
    }

    // Initialize analytics on first visit
    if (tabId === 'analyticsTab' && typeof initAnalytics === 'function' && !analyticsInitialized) {
        // Set default dates
        const dateFrom = document.getElementById('filterDateFrom');
        const dateTo = document.getElementById('filterDateTo');
        if (dateFrom) dateFrom.value = defaultDateFrom();
        if (dateTo) dateTo.value = defaultDateTo();
        // Latch only once the filter bar actually loaded, so a failed attempt can
        // recover on the next visit instead of leaving empty dropdowns forever.
        initAnalytics().then(ok => { if (ok !== false) analyticsInitialized = true; });
    }

    // Initialize operations tab on first visit
    if (tabId === 'operationsTab' && typeof opsInit === 'function') {
        opsInit();
    }
}

// Alert Banners
function showAlert(message, type = 'error') {
    const alertBox = document.getElementById('adminAlert');
    if (!alertBox) return;
    alertBox.className = `alert alert-${type}`;
    alertBox.textContent = message;
    alertBox.classList.remove('hidden');
}

function hideAlert() {
    const alertBox = document.getElementById('adminAlert');
    if (alertBox) alertBox.classList.add('hidden');
}

// Logout
function handleLogout() {
    localStorage.clear();
    window.location.href = '/auth.html';
}

// --- 1. CITY MANAGEMENT ---
async function loadCities() {
    try {
        const res = await adminApiCall('/cities');
        if (!res) return;

        // Populate dropdowns across other forms as well
        const cityDropdowns = [
            document.getElementById('theatreCitySelect'),
            document.getElementById('showCitySelect')
        ];
        cityDropdowns.forEach(d => {
            if (d) d.innerHTML = '<option value="" disabled selected>Select City</option>';
        });

        window.__cityCache = res.data;
        res.data.forEach(city => {
            cityDropdowns.forEach(d => {
                if (d) d.innerHTML += `<option value="${city.id}">${city.name}, ${city.state}</option>`;
            });
        });

        // Drawn from the cache so the search box can filter without another request.
        applyCitySearch();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

/** Draws the Manage Cities table for the rows it is given. */
function renderCitiesTable(cities, emptyMessage) {
    const tbody = document.getElementById('citiesTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';
    cities.forEach(city => {
        tbody.innerHTML += `
            <tr>
                <td>${city.id}</td>
                <td><strong>${city.name}</strong></td>
                <td>${city.state}</td>
                <td>
                    <button class="btn-secondary-sm" onclick="editCity(${city.id})">✏️</button>
                    <button class="btn-danger-sm" onclick="deleteCity(${city.id})">🗑️</button>
                </td>
            </tr>
        `;
    });

    if (cities.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="color: var(--text-muted); font-style: italic;">${emptyMessage}</td></tr>`;
    }
}

/** Called by the search box on the Manage Cities card. */
function applyCitySearch() {
    const all = window.__cityCache || [];
    const term = (document.getElementById('citySearch')?.value || '').trim().toLowerCase();

    const matches = !term ? all : all.filter(city =>
        [city.id, city.name, city.state].filter(Boolean).join(' ').toLowerCase().includes(term));

    renderCitiesTable(matches, term
        ? 'No city matches that search. Clear the box to see them all.'
        : 'No cities yet. Add one with the form.');

    const countEl = document.getElementById('cityCount');
    if (countEl) {
        countEl.textContent = all.length === 0
            ? ''
            : (term ? `${matches.length} of ${all.length} cities match "${term}"` : `${all.length} city(s)`);
    }
}

document.getElementById('addCityForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('cityName').value.trim();
    const state = document.getElementById('cityState').value.trim();

    try {
        await adminApiCall('/cities', 'POST', { name, state });
        showAlert('City added successfully!', 'success');
        document.getElementById('addCityForm').reset();
        loadCities();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
});

async function deleteCity(id) {
    if (!confirm('Are you sure you want to delete this city?')) return;
    try {
        await adminApiCall(`/cities/${id}`, 'DELETE');
        showAlert('City deleted.', 'success');
        loadCities();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

// --- 2. MOVIE LIBRARY MANAGEMENT ---
async function loadMovies() {
    try {
        const res = await adminApiCall('/movies');
        if (!res) return;
        const movieSelect = document.getElementById('showMovieSelect');

        if (movieSelect) movieSelect.innerHTML = '<option value="" disabled selected>Select Movie</option>';

        window.__movieCache = res.data;
        res.data.forEach(movie => {
            if (movieSelect) {
                movieSelect.innerHTML += `<option value="${movie.id}">${movie.title} (${movie.cbfcRating})</option>`;
            }
        });

        // Drawn from the cache so the search box filters without a new request.
        applyMovieSearch();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

/** Draws the Movie Library table for the rows it is given. */
function renderMoviesTable(movies, emptyMessage) {
    const tbody = document.getElementById('moviesTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';
    movies.forEach(movie => {
        const langs = Array.isArray(movie.availableLanguages) && movie.availableLanguages.length
            ? movie.availableLanguages.join(', ')
            : '—';
        tbody.innerHTML += `
            <tr>
                <td>#${movie.id}</td>
                <td><img src="${movie.posterUrl}" alt="poster" style="width: 40px; height: 60px; object-fit: cover; border-radius: 4px;"></td>
                <td><strong>${movie.title}</strong></td>
                <td>${movie.cbfcRating}</td>
                <td>${movie.durationMinutes}m</td>
                <td>${langs}</td>
                <td>
                    <button class="btn-secondary-sm" onclick="editMovie(${movie.id})">✏️</button>
                    <button class="btn-danger-sm" onclick="deleteMovie(${movie.id})">🗑️</button>
                </td>
            </tr>
        `;
    });

    if (movies.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="color: var(--text-muted); font-style: italic;">${emptyMessage}</td></tr>`;
    }
}

/** Called by the search box in the Movie Library card. */
function applyMovieSearch() {
    const all = window.__movieCache || [];
    const term = (document.getElementById('movieSearch')?.value || '').trim().toLowerCase();

    const matches = !term ? all : all.filter(movie => [
        '#' + movie.id,
        movie.title,
        movie.cbfcRating,
        (movie.availableLanguages || []).join(' '),
        (movie.availableFormats || []).join(' ')
    ].filter(Boolean).join(' ').toLowerCase().includes(term));

    renderMoviesTable(matches, term
        ? 'No movie matches that search. Clear the box to see them all.'
        : 'No movies in the library yet. Add one with the form.');

    const countEl = document.getElementById('movieCount');
    if (countEl) {
        countEl.textContent = all.length === 0
            ? ''
            : (term ? `${matches.length} of ${all.length} movies match "${term}"` : `${all.length} movie(s)`);
    }
}

document.getElementById('addMovieForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const languages = splitMovieMetadata(document.getElementById('movieLanguages').value);
    const formats = splitMovieMetadata(document.getElementById('movieFormats').value);

    // Validated before the request so an unsupported value is explained straight
    // away instead of failing deeper down.
    clearFieldErrors();
    const metadataError = validateMovieMetadata(languages, formats);
    if (metadataError) {
        showAlert(metadataError.message, 'error');
        markFieldError(metadataError.field, metadataError.message);
        return;
    }

    const payload = {
        title: document.getElementById('movieTitle').value,
        cbfcRating: document.getElementById('movieCbfc').value,
        durationMinutes: parseInt(document.getElementById('movieDuration').value),
        releaseDate: document.getElementById('movieRelease').value,
        // Canonical enum names: the API stores TWO_D/IMAX_3D style values, so the
        // labels the operator typed ("2D", "IMAX 3D") are converted here.
        availableLanguages: languages.map(canonicalLanguage),
        availableFormats: formats.map(canonicalFormat),
        posterUrl: document.getElementById('moviePoster').value,
        bannerUrl: document.getElementById('movieBanner').value,
        castMembers: document.getElementById('movieCast').value,
        description: document.getElementById('movieDesc').value
    };

    try {
        await adminApiCall('/movies', 'POST', payload);
        showAlert('Movie added to master library!', 'success');
        document.getElementById('addMovieForm').reset();
        loadMovies();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
});

async function deleteMovie(id) {
    if (!confirm('Delete this movie record?')) return;
    try {
        await adminApiCall(`/movies/${id}`, 'DELETE');
        showAlert('Movie deleted.', 'success');
        loadMovies();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

// --- 3. THEATRES & SCREENS MANAGEMENT ---
async function loadTheatres() {
    try {
        const res = await adminApiCall('/theatres');
        if (!res) return;
        const container = document.getElementById('theatresListContainer');
        const screenTheatreSelect = document.getElementById('screenTheatreSelect');

        container.innerHTML = '';
        if (screenTheatreSelect) screenTheatreSelect.innerHTML = '<option value="" disabled selected>Select Theatre</option>';

        res.data.forEach(t => {
            if (screenTheatreSelect) {
                screenTheatreSelect.innerHTML += `<option value="${t.id}">${t.name} (${t.cityName})</option>`;
            }

			const screenListHtml = t.screens && t.screens.length > 0 
			    ? t.screens.map(s => `
			        <li style="margin-top: 8px; color: var(--text-muted); display: flex; justify-content: space-between; align-items: center;">
			            <span>${s.name} — ${s.totalSeats > 0 ? s.totalSeats + ' seats' : '<em style="color:#a1a1aa;">layout not drawn</em>'}</span>
			            <span style="display:flex; gap:6px;">
			                <button class="btn-secondary-sm" onclick="editScreen(${s.id})">✏️</button>
			                <button class="btn-danger-sm" onclick="deleteScreen(${s.id})">🗑️</button>
			            </span>
			        </li>
			      `).join('')
			    : '<li style="color: var(--text-muted); font-style: italic;">No screens configured yet.</li>';

			const ownerHtml = t.ownerId
			    ? `<span style="color: var(--text-muted);">👤 Owner: <strong style="color:#ccc;">${t.ownerName}</strong> (${t.ownerEmail})</span>
			       <button class="btn-danger-sm" onclick="unassignOwner(${t.id})">Unassign</button>`
			    : `<span style="color: var(--text-muted); font-style: italic;">No owner assigned</span>
			       <button class="btn-secondary-sm" onclick="assignOwner(${t.id})">Assign Owner</button>`;

            container.innerHTML += `
                <div style="background-color: #0b0c0e; border: 1px solid var(--border-color); padding: 12px; border-radius: 4px; margin-bottom: 12px;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <strong style="color: var(--primary-gold);">${t.name}</strong>
                        <span style="display: flex; gap: 6px; align-items: center;">
                            <span style="font-size: 0.75rem; color: var(--text-muted);">${t.cityName}</span>
                            <button class="btn-secondary-sm" onclick="editTheatre(${t.id})">✏️ Edit</button>
                            <button class="btn-danger-sm" onclick="deleteTheatre(${t.id})">🗑️ Delete</button>
                        </span>
                    </div>
                    <div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 4px;">${t.address}</div>
                    <div style="font-size: 0.8rem; margin-top: 8px; display: flex; justify-content: space-between; align-items: center;">
                        ${ownerHtml}
                    </div>
                    <ul style="margin-top: 8px; padding-left: 16px; font-size: 0.85rem;">
                        ${screenListHtml}
                    </ul>
                </div>
            `;
        });

        window.__theatreCache = res.data;

        // Feed the Maintenance tab's theatre picker from the same data.
        if (typeof mtPopulateTheatres === 'function') mtPopulateTheatres();

        // Re-apply whatever search term is in the box, and refresh the count.
        applyTheatreSearch();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

async function assignOwner(theatreId) {
    const name = prompt('Theatre owner full name:');
    if (!name) return;
    const email = prompt('Theatre owner email (they will log in with this):');
    if (!email) return;
    const password = prompt('Set a temporary password for this owner (min 8 chars, upper+lower+number+special):');
    if (!password) return;

    try {
        await adminApiCall(`/theatres/${theatreId}/owner`, 'POST', { name, email, password });
        showAlert('Owner assigned. Share these login credentials with them.', 'success');
        loadTheatres();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

async function unassignOwner(theatreId) {
    if (!confirm('Unassign the current owner from this theatre?')) return;
    try {
        await adminApiCall(`/theatres/${theatreId}/owner`, 'DELETE');
        showAlert('Owner unassigned.', 'success');
        loadTheatres();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

document.getElementById('addTheatreForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const cityIdRaw = document.getElementById('theatreCitySelect').value;
    const name = document.getElementById('theatreName').value.trim();
    const address = document.getElementById('theatreAddress').value.trim();

    if (!cityIdRaw) {
        showAlert('Please select a city.', 'error');
        return;
    }

    const cityId = parseInt(cityIdRaw, 10); // Convert string "1" to integer 1

    try {
        await adminApiCall('/theatres', 'POST', { cityId, name, address });
        showAlert('Theatre registered successfully!', 'success');
        document.getElementById('addTheatreForm').reset();
        loadTheatres();
    } catch (err) {
        // The retry-with-nested-city fallback that used to live here re-POSTed on
        // ANY error, so a request that succeeded server-side but threw on the
        // client produced two theatres. Removed.
        console.error('[THEATRE CREATE FAILED]', err);
    }
});
document.getElementById('addScreenForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    await withSubmitGuard(e.currentTarget, async () => {
        const theatreId = document.getElementById('screenTheatreSelect').value;
        const name = document.getElementById('screenName').value;

        try {
            await adminApiCall(`/theatres/${theatreId}/screens`, 'POST', { name, totalSeats: 0 });
            showAlert('Screen added to theatre!', 'success');
            document.getElementById('addScreenForm').reset();
            loadTheatres();
        } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
    });
});

// Cascading Selectors for Shows Scheduler
async function loadTheatresForCity(cityId) {
    const theatreSelect = document.getElementById('showTheatreSelect');
    theatreSelect.innerHTML = '<option value="" disabled selected>Loading...</option>';
    try {
        const res = await adminApiCall(`/cities/${cityId}/theatres`);
        if (!res) return;
        theatreSelect.innerHTML = '<option value="" disabled selected>Select Theatre</option>';
        res.data.forEach(t => {
            theatreSelect.innerHTML += `<option value="${t.id}">${t.name}</option>`;
        });
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

async function loadScreensForTheatre(theatreId) {
    const screenSelect = document.getElementById('showScreenSelect');
    screenSelect.innerHTML = '<option value="" disabled selected>Loading...</option>';
    try {
        const res = await adminApiCall(`/theatres/${theatreId}/screens`);
        if (!res) return;
        screenSelect.innerHTML = '<option value="" disabled selected>Select Screen</option>';
        res.data.forEach(s => {
            screenSelect.innerHTML += `<option value="${s.id}">${s.name} (${s.totalSeats} seats)</option>`;
        });
        // Clear any tier prices left over from a previously chosen screen.
        renderShowTierPriceInputs([]);
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

// --- 4. SHOW SCHEDULER ---
// Last response, so the search box can filter without hitting the API on every
// keystroke.
let adminShowsCache = [];

async function loadShows() {
    try {
        const scopeEl = document.getElementById('showScopeSelect');
        const scope = scopeEl ? scopeEl.value : 'upcoming';
        const res = await adminApiCall(`/shows?scope=${scope}`);
        if (!res) return;
        adminShowsCache = res.data || [];

        // Drawn from the cache so the search box filters without a new request.
        applyAdminShowSearch();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

/** Called by the search box on the Scheduled Shows card. */
function applyAdminShowSearch() {
    renderAdminShows();
}

/** Draws the Scheduled Shows table for the current scope and search term. */
function renderAdminShows() {
    const tbody = document.getElementById('showsTableBody');
    if (!tbody) return;

    const scope = document.getElementById('showScopeSelect')?.value || 'upcoming';
    const term = (document.getElementById('adminShowSearch')?.value || '').trim().toLowerCase();
    const now = new Date();

    tbody.innerHTML = '';
    let shown = 0;

    adminShowsCache.forEach(s => {
        const showDateTime = new Date(s.startTime);
        if (isNaN(showDateTime)) return;

        // Skip past shows for upcoming scope - they should only appear in "past" scope
        if (scope === 'upcoming' && showDateTime <= now) return;

        // Skip future shows for past scope
        if (scope === 'past' && showDateTime > now) return;

        // Matches the columns on screen, including the Show ID.
        if (term) {
            const haystack = ['#' + s.id, s.movieTitle, s.theatreName, s.screenName, s.format]
                .filter(Boolean).join(' ').toLowerCase();
            if (!haystack.includes(term)) return;
        }

        shown++;
        const dateStr = showDateTime.toLocaleString();
        const isPast = showDateTime <= now;

        tbody.innerHTML += `
            <tr>
                <td>#${s.id}</td>
                <td><strong>${s.movieTitle}</strong></td>
                <td>${s.theatreName} - ${s.screenName}</td>
                <td>${dateStr}</td>
                <td>₹${s.price} (${s.format})</td>
                <td>
                    ${isPast ? '<span style="color: var(--text-muted);">Completed</span>'
                             : `<button class="btn-danger-sm" onclick="deleteShow(${s.id})">Cancel</button>`}
                </td>
            </tr>
        `;
    });

    if (shown === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="color: var(--text-muted); font-style: italic;">${
            term ? 'No show matches that search. Clear the box to see them all.'
                 : 'No shows to display for this scope.'
        }</td></tr>`;
    }

    const countEl = document.getElementById('adminShowCount');
    if (countEl) {
        countEl.textContent = term
            ? `${shown} show(s) match "${term}"`
            : (shown ? `${shown} show(s)` : '');
    }
}

document.getElementById('addShowForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    await withSubmitGuard(e.currentTarget, async () => {
        // Field names must match the backend ShowRequest record exactly:
        // basePrice (not price), language + hasCaptions are required.
        const payload = {
            screenId: parseInt(document.getElementById('showScreenSelect').value),
            movieId: parseInt(document.getElementById('showMovieSelect').value),
            startTime: document.getElementById('showStartTime').value,
            basePrice: parseFloat(document.getElementById('showPrice').value),
            format: document.getElementById('showFormat').value,
            language: document.getElementById('showLanguage').value,
            hasCaptions: document.getElementById('showCaptions').checked,
            // Pricing is per show. When the screen has tiers, the backend derives
            // basePrice from the cheapest one, so basePrice may be left blank.
            tierPrices: collectTierPrices()
        };
        if (isNaN(payload.basePrice)) {
            delete payload.basePrice;
        }
        if (showTiers.length > 0 && payload.tierPrices.length !== showTiers.length) {
            const message = 'Enter a price for every seat tier on this screen.';
            showAlert(message, 'error');
            markFieldError(Array.from(document.querySelectorAll('.tier-price-input')), message);
            return;
        }
        if (showTiers.length === 0 && payload.basePrice === undefined) {
            const message = 'Enter a ticket price.';
            showAlert(message, 'error');
            markFieldError('showPrice', message);
            return;
        }
        
        // Parse reserved seat codes (comma-separated)
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

        // Catch the obvious input mistakes locally; the server validates the
        // same rules and remains the source of truth.
        // Returns the field that is wrong along with the message, so the form can
        // point at the offending control instead of only printing a banner.
        const problem = validateShowSchedule(payload, showTiers.length);
        if (problem) {
            showAlert(problem.message, 'error');
            markFieldError(scheduleFieldTarget(problem.field), problem.message);
            return;
        }

        clearFieldErrors();

        try {
            await adminApiCall('/shows', 'POST', payload);
            showAlert('Show scheduled successfully!', 'success');
            document.getElementById('addShowForm').reset();
            clearFieldErrors();
            loadShows();
        } catch (err) {
            console.error('[SCHEDULE SHOW]', err);
            // adminApiCall() has already shown the server's message; also mark the
            // field it belongs to (double-booking, locked layout, bad seat codes…).
            markScheduleErrorFromServer(err.message);
        }
    });
});

async function deleteShow(id) {
    if (!confirm('Cancel this scheduled show?')) return;
    try {
        await adminApiCall(`/shows/${id}`, 'DELETE');
        showAlert('Show cancelled.', 'success');
        loadShows();
    } catch (err) {
        console.error('[RENDER ERROR]', err);
        showAlert(`Something failed while rendering: ${err.message}`, 'error');
    }
}

// Global State for current Screen being modified

// 1. Open Modal and Reset State




// 2. Generate Interactive Grid Canvas


// Re-number a single row so that only ACTIVE seats get sequential labels.
// Pathways are skipped, so "A5 [pathway] A6" instead of "A5 [pathway] A8".


// 3. Toggle Seat Type (Seat vs Pathway), then renumber the whole row


// 4. Save Layout Schema to Backend API



/* =====================================================================
   Theatre search + show scheduling input guards.
   The scheduling rules mirror ShowScheduleValidator on the server, which stays
   the source of truth; these only save a round trip and read better inline.
   ===================================================================== */

/**
 * Filters the already-rendered theatre cards by name / city / address.
 * window.__theatreCache is filled in the same order as the rendered cards, so
 * the card for index i belongs to cache entry i.
 */
function applyTheatreSearch() {
    const container = document.getElementById('theatresListContainer');
    if (!container) return;
    const cache = window.__theatreCache || [];
    const term = (document.getElementById('theatreSearchBox')?.value || '').trim().toLowerCase();

    let visible = 0;
    Array.from(container.children).forEach((card, idx) => {
        if (!card.hasAttribute || card.hasAttribute('data-theatre-empty')) return;
        const t = cache[idx] || {};
        const haystack = [t.name, t.cityName, t.address]
            .filter(Boolean).join(' ').toLowerCase();
        const matches = !term || haystack.includes(term);
        card.style.display = matches ? '' : 'none';
        if (matches) visible++;
    });

    const countEl = document.getElementById('theatreCount');
    if (countEl) {
        countEl.textContent = term
            ? `${visible} of ${cache.length} theatre(s) match "${term}"`
            : (cache.length ? `${cache.length} theatre(s)` : '');
    }

    let empty = container.querySelector('[data-theatre-empty]');
    if (cache.length > 0 && visible === 0) {
        if (!empty) {
            empty = document.createElement('p');
            empty.setAttribute('data-theatre-empty', '');
            empty.style.cssText = 'color: var(--text-muted); font-style: italic;';
            empty.textContent = 'No theatre matches that search. Clear the box to see all theatres.';
            container.appendChild(empty);
        }
    } else if (empty) {
        empty.remove();
    }
}

function toLocalInputValue(date) {
    const pad = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// Keep the native picker from offering past dates at all.
function primeShowStartTimeBounds(inputId) {
    const el = document.getElementById(inputId);
    if (!el) return;
    const refresh = () => { el.min = toLocalInputValue(new Date()); };
    refresh();
    el.addEventListener('focus', refresh);
}
primeShowStartTimeBounds('showStartTime');

/**
 * Returns the first scheduling problem as `{ field, message }`, or null when the
 * payload is good enough to send. The field id lets the caller mark the exact
 * control, so no error is ever shown without pointing at where it came from.
 */
function validateShowSchedule(payload, tierCount) {
    if (!payload.screenId || Number.isNaN(payload.screenId)) {
        return { field: 'showScreenSelect', message: 'Select an auditorium / screen.' };
    }
    if (!payload.movieId || Number.isNaN(payload.movieId)) {
        return { field: 'showMovieSelect', message: 'Select a movie.' };
    }
    if (!payload.startTime) {
        return { field: 'showStartTime', message: 'Choose a start date and time.' };
    }
    if (!payload.format) {
        return { field: 'showFormat', message: 'Select a screening format.' };
    }
    if (!payload.language) {
        return { field: 'showLanguage', message: 'Select an audio language.' };
    }

    const start = new Date(payload.startTime);
    if (Number.isNaN(start.getTime())) {
        return { field: 'showStartTime', message: 'That start date and time could not be understood. Pick it again.' };
    }
    if (start <= new Date()) {
        return { field: 'showStartTime', message: 'A show cannot be scheduled in the past. Pick a start time in the future.' };
    }
    if ((start - new Date()) > 365 * 24 * 60 * 60 * 1000) {
        return { field: 'showStartTime', message: 'That start time is more than a year away. Schedule shows within the next 12 months.' };
    }

    const price = payload.basePrice;
    if (price !== undefined && price !== null && !(price > 0)) {
        return { field: 'showPrice', message: 'Ticket price must be greater than zero.' };
    }
    if (tierCount > 0 && (payload.tierPrices || []).some(p => !(p.price > 0))) {
        return { field: 'tierPrices', message: 'Every seat tier needs a ticket price greater than zero.' };
    }

    const seen = new Set();
    for (const code of (payload.reservedSeatCodes || [])) {
        const key = String(code).toUpperCase();
        if (seen.has(key)) {
            return { field: 'showReservedSeats', message: `Seat ${code} is listed twice. Each reserved seat can only be listed once.` };
        }
        seen.add(key);
    }

    return null;
}

/* =====================================================================
   Phase 2 CRUD handlers.
   Appended at the end on purpose: function declarations hoist, so position
   never matters, and appending cannot clobber anything above it.
   Values come from the caches set during render rather than being
   interpolated into onclick attributes, which breaks on any name or
   address containing an apostrophe.
   ===================================================================== */

function __find(cacheName, id) {
    return (window[cacheName] || []).find(x => x.id === id) || {};
}

function __findScreen(id) {
    for (const t of (window.__theatreCache || [])) {
        const s = (t.screens || []).find(x => x.id === id);
        if (s) return s;
    }
    return {};
}

async function editTheatre(id) {
    const t = __find('__theatreCache', id);
    const name = prompt('Theatre name:', t.name || '');
    if (name === null) return;
    const address = prompt('Address:', t.address || '');
    if (address === null) return;
    try {
        await adminApiCall(`/theatres/${id}`, 'PUT', { name, address });
        showAlert('Theatre updated.', 'success');
        loadTheatres();
    } catch (err) { console.error('[EDIT THEATRE]', err); }
}

async function deleteTheatre(id) {
    const t = __find('__theatreCache', id);
    if (!confirm(`Delete theatre "${t.name || id}"? Its screens will be removed too.`)) return;
    try {
        await adminApiCall(`/theatres/${id}`, 'DELETE');
        showAlert('Theatre deleted.', 'success');
        loadTheatres();
    } catch (err) { console.error('[DELETE THEATRE]', err); }
}

async function editScreen(id) {
    const s = __findScreen(id);
    const name = prompt('Screen name:', s.name || '');
    if (name === null) return;
    try {
        // Capacity is intentionally not editable here: it is derived from the seat
        // layout drawn in the Maintenance tab.
        await adminApiCall(`/screens/${id}`, 'PUT', { name });
        showAlert('Screen updated.', 'success');
        loadTheatres();
    } catch (err) { console.error('[EDIT SCREEN]', err); }
}

async function deleteScreen(id) {
    const s = __findScreen(id);
    if (!confirm(`Delete screen "${s.name || id}"?`)) return;
    try {
        await adminApiCall(`/screens/${id}`, 'DELETE');
        showAlert('Screen deleted.', 'success');
        loadTheatres();
    } catch (err) { console.error('[DELETE SCREEN]', err); }
}

/* ===== Movie metadata validation =====
   Languages and formats are typed as free text, and the labels people naturally
   use ("2D", "IMAX 3D") are not the enum names the API stores (TWO_D, IMAX_3D).
   Both spellings are accepted and sent canonically; anything else is reported
   here with the accepted list, mirroring AdminService on the server. */

const MOVIE_LANGUAGE_NAMES = ['ENGLISH', 'TAMIL', 'HINDI', 'TELUGU', 'KANNADA', 'MALAYALAM'];

const MOVIE_FORMAT_ALIASES = {
    '2D': 'TWO_D',
    'TWO_D': 'TWO_D',
    '3D': 'THREE_D',
    'THREE_D': 'THREE_D',
    'IMAX_2D': 'IMAX_2D',
    'IMAX_3D': 'IMAX_3D',
    '4DX': 'FOUR_DX',
    'FOUR_DX': 'FOUR_DX'
};

const MOVIE_FORMAT_ACCEPTED =
    'TWO_D (2D), THREE_D (3D), IMAX_2D (IMAX 2D), IMAX_3D (IMAX 3D), FOUR_DX (4DX)';

/** Splits a comma separated text box into trimmed, non-empty tokens. */
function splitMovieMetadata(raw) {
    return String(raw || '').split(',').map(x => x.trim()).filter(Boolean);
}

function normalizeMovieToken(raw) {
    return String(raw || '').trim().toUpperCase().replace(/[\s-]+/g, '_');
}

function canonicalLanguage(raw) {
    const key = normalizeMovieToken(raw);
    return MOVIE_LANGUAGE_NAMES.includes(key) ? key : null;
}

function canonicalFormat(raw) {
    return MOVIE_FORMAT_ALIASES[normalizeMovieToken(raw)] || null;
}

/**
 * Returns { field, message } for the first unsupported language or format, or null
 * when everything can be stored. The message names the accepted values, so a typo is
 * explained instead of surfacing as a vague failure.
 */
function validateMovieMetadata(languages, formats) {
    const badLanguage = languages.find(l => !canonicalLanguage(l));
    if (badLanguage) {
        return {
            field: 'movieLanguages',
            message: `'${badLanguage}' is not a supported audio language. Accepted languages: ${MOVIE_LANGUAGE_NAMES.join(', ')}.`
        };
    }

    const badFormat = formats.find(f => !canonicalFormat(f));
    if (badFormat) {
        return {
            field: 'movieFormats',
            message: `'${badFormat}' is not a supported format. Accepted formats: ${MOVIE_FORMAT_ACCEPTED}.`
        };
    }

    return null;
}

async function editMovie(id) {
    const m = __find('__movieCache', id);

    const title = prompt('Movie title:', m.title || '');
    if (title === null) return;

    const durationMinutes = prompt('Duration (minutes):', m.durationMinutes || '');
    if (durationMinutes === null) return;

    // This flow edits through prompts rather than a form, so the same check runs
    // here before anything is sent.
    const rawLanguages = prompt(
        `Languages, comma separated (${MOVIE_LANGUAGE_NAMES.join(', ')}):`,
        (m.availableLanguages || []).join(', '));
    if (rawLanguages === null) return;

    const rawFormats = prompt(
        `Formats, comma separated (${MOVIE_FORMAT_ACCEPTED}):`,
        (m.availableFormats || []).join(', '));
    if (rawFormats === null) return;

    const languages = splitMovieMetadata(rawLanguages);
    const formats = splitMovieMetadata(rawFormats);

    const metadataError = validateMovieMetadata(languages, formats);
    if (metadataError) {
        showAlert(metadataError.message, 'error');
        return;
    }

    try {
        await adminApiCall(`/movies/${id}`, 'PUT', {
            title,
            durationMinutes: parseInt(durationMinutes, 10),
            availableLanguages: languages.map(canonicalLanguage),
            availableFormats: formats.map(canonicalFormat)
        });
        showAlert('Movie updated.', 'success');
        loadMovies();
    } catch (err) { console.error('[EDIT MOVIE]', err); }
}

async function editCity(id) {
    const c = __find('__cityCache', id);
    const name = prompt('City name:', c.name || '');
    if (name === null) return;
    const state = prompt('State:', c.state || '');
    if (state === null) return;
    try {
        await adminApiCall(`/cities/${id}`, 'PUT', { name, state });
        showAlert('City updated.', 'success');
        loadCities();
    } catch (err) { console.error('[EDIT CITY]', err); }
}


/* =====================================================================
   PHASE 3 - Theatre Maintenance: seat tiers + layout designer.

   Cells are built with createElement and textContent rather than
   innerHTML string concatenation, so a tier named with an apostrophe or
   an angle bracket cannot break the markup or inject script.
   ===================================================================== */

let mtScreenId = null;
let mtTiers = [];
let mtGrid = [];          // [row][col] = { type: 'SEAT'|'PATHWAY', tierId: Long|null }
let mtTool = null;        // { kind: 'TIER', tierId } | { kind: 'PATHWAY' }
let mtEditable = true;
let mtPainting = false;

/**
 * Maintenance Step 1 — City picker. Cities are derived from the theatres that
 * are already loaded, so no extra request is needed and the list can never
 * contain a city with no theatres behind it.
 */
function mtPopulateCities() {
    const sel = document.getElementById('mtCitySelect');
    if (!sel) return;
    const previous = sel.value;

    const seen = new Set();
    const cities = [];
    (window.__theatreCache || []).forEach(t => {
        if (t.cityId && !seen.has(String(t.cityId))) {
            seen.add(String(t.cityId));
            cities.push({ id: t.cityId, name: t.cityName || ('City #' + t.cityId) });
        }
    });
    cities.sort((a, b) => String(a.name).localeCompare(String(b.name)));

    sel.innerHTML = '<option value="">All Cities</option>';
    cities.forEach(c => {
        const o = document.createElement('option');
        o.value = c.id;
        o.textContent = c.name;
        sel.appendChild(o);
    });
    sel.value = cities.some(c => String(c.id) === String(previous)) ? previous : '';
}

/**
 * City -> Theatre for Maintenance. Choosing a city leaves only its theatres in
 * the picker, so the screen being edited is quick to find on a long list.
 * A theatre that is no longer reachable is dropped rather than kept selected.
 */
function mtNarrowTheatresByCity() {
    const sel = document.getElementById('mtTheatreSelect');
    if (!sel) return;
    const cityEl = document.getElementById('mtCitySelect');
    const cityId = cityEl ? cityEl.value : '';
    const previous = sel.value;

    const theatres = (window.__theatreCache || [])
        .filter(t => !cityId || String(t.cityId) === String(cityId));

    sel.innerHTML = `<option value="" disabled selected>${
        (cityId && theatres.length === 0) ? 'No theatres in this city' : 'Select Theatre'
    }</option>`;
    theatres.forEach(t => {
        const o = document.createElement('option');
        o.value = t.id;
        o.textContent = cityId ? t.name : `${t.name} (${t.cityName})`;
        sel.appendChild(o);
    });
    sel.value = theatres.some(t => String(t.id) === String(previous)) ? previous : '';
}

function mtOnCityChange() {
    clearFieldErrors();
    mtNarrowTheatresByCity();
    // Rebuild the screen list (and reset the panels) for whatever theatre the
    // narrowed list now has selected — or for none, if it was dropped.
    mtOnTheatreChange();
}

function mtPopulateTheatres() {
    mtPopulateCities();
    mtNarrowTheatresByCity();
}

function mtOnTheatreChange() {
    const theatreId = parseInt(document.getElementById('mtTheatreSelect').value, 10);
    const t = (window.__theatreCache || []).find(x => x.id === theatreId);
    const sel = document.getElementById('mtScreenSelect');
    clearFieldErrors();
    sel.innerHTML = '<option value="" disabled selected>Select Screen</option>';
    document.getElementById('mtPanels').classList.add('hidden');
    mtScreenId = null;

    ((t && t.screens) || []).forEach(s => {
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
        const res = await adminApiCall(`/screens/${mtScreenId}/seats`);
        if (!res) return;
        const d = res.data;

        mtTiers = d.tiers || [];
        mtEditable = d.editable !== false;

        const banner = document.getElementById('mtLockBanner');
        if (!mtEditable) {
            banner.textContent = '🔒 ' + (d.lockReason || 'This layout is locked.');
            banner.classList.remove('hidden');
            // Mark the screen that caused the refusal, so the reason and the field
            // it belongs to are shown together.
            markFieldError('mtScreenSelect', d.lockReason || 'This screen has shows scheduled, so its layout cannot be changed.');
        } else {
            banner.classList.add('hidden');
            clearFieldError('mtScreenSelect');
        }

        mtRenderTierTable();
        mtRenderPalette();

        // Rebuild the in-memory grid from the saved seats. This is the step the
        // old designer was missing: it always reset to a blank 6x10 and then
        // overwrote whatever was actually stored.
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
        await adminApiCall(`/screens/${mtScreenId}/tiers`, 'POST', {
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
        await adminApiCall(`/tiers/${tierId}`, 'PUT', {
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
        await adminApiCall(`/tiers/${tierId}`, 'DELETE');
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
        await adminApiCall(`/screens/${mtScreenId}/seats`, 'PUT', { grid });
        showAlert('Seat layout saved.', 'success');
        await mtLoadLayout();
        loadTheatres();   // refresh capacities shown on the Theatres tab
    } catch (err) { console.error('[MT SAVE LAYOUT]', err); }
}


/* =====================================================================
   Per-show tier pricing. Price is a property of the screening, not the
   furniture, so each tier on the chosen screen needs its own price here.
   ===================================================================== */

let showTiers = [];

async function loadTiersForShowScreen(screenId) {
    if (!screenId) { renderShowTierPriceInputs([]); return; }
    try {
        const res = await adminApiCall(`/screens/${screenId}/tiers`);
        showTiers = (res && res.data) ? res.data : [];
        renderShowTierPriceInputs(showTiers);
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
    const basePriceGroup = document.getElementById('showPrice');

    if (showTiers.length === 0) {
        wrap.classList.add('hidden');
        if (basePriceGroup) basePriceGroup.disabled = false;
        return;
    }

    wrap.classList.remove('hidden');
    // With tiers present the base price is derived from the cheapest tier, so
    // asking for it separately would just be a second source of truth.
    if (basePriceGroup) {
        basePriceGroup.value = '';
        basePriceGroup.disabled = true;
    }

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
    const inputs = document.querySelectorAll('.tier-price-input');
    const out = [];
    inputs.forEach(i => {
        const v = parseFloat(i.value);
        if (!isNaN(v)) {
            out.push({ tierId: parseInt(i.dataset.tierId, 10), price: v });
        }
    });
    return out;
}
