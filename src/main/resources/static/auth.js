// Base API Context
const API_BASE_URL = "/auth";
const BOOKING_API_BASE = "/api/booking";

// Shared Active State
const state = {
    email: "",
    flow: "" // 'LOGIN' | 'REGISTER' | 'FORGOT_PASSWORD' | 'DELETE_ACCOUNT'
};

// --- View Controller ---
function switchView(viewId) {
    clearAllErrors();
    const views = [
        "loginView",
        "registerView",
        "otpView",
        "forgotPasswordView",
        "accountSettingsView",
        "myBookingsView"
    ];
    views.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.classList.add("hidden");
    });
    const target = document.getElementById(viewId);
    if (target) {
        target.classList.remove("hidden");
        // Load bookings when switching to myBookingsView
        if (viewId === 'myBookingsView') {
            loadMyBookings();
        }
    }
}

// --- Validation Error Helpers ---

// Show error specifically under an input field
function setFieldError(inputId, message) {
    const inputEl = document.getElementById(inputId);
    if (!inputEl) return;

    inputEl.classList.add("input-error");

    const parent = inputEl.parentElement;
    let errorEl = parent.querySelector(".field-error-msg");
    
    if (!errorEl) {
        errorEl = document.createElement("span");
        errorEl.className = "field-error-msg";
        parent.appendChild(errorEl);
    }
    
    errorEl.textContent = message;
}

// Clear errors for a single input
function clearFieldError(inputId) {
    const inputEl = document.getElementById(inputId);
    if (!inputEl) return;

    inputEl.classList.remove("input-error");
    const parent = inputEl.parentElement;
    const errorEl = parent.querySelector(".field-error-msg");
    if (errorEl) {
        errorEl.remove();
    }
}

// Clear all field and global errors
function clearAllErrors() {
    hideAlert();
    document.querySelectorAll(".input-error").forEach(el => el.classList.remove("input-error"));
    document.querySelectorAll(".field-error-msg").forEach(el => el.remove());
}

// Global Banner Alert
function showAlert(message, type = "error") {
    const alertBox = document.getElementById("alertBox");
    if (!alertBox) return;
    alertBox.className = `alert alert-${type}`;
    alertBox.textContent = message;
    alertBox.classList.remove("hidden");
}

function hideAlert() {
    const alertBox = document.getElementById("alertBox");
    if (alertBox) alertBox.classList.add("hidden");
}

// --- Enhanced Fetch Wrapper ---
async function apiCall(endpoint, payload, method = "POST", requiresAuth = false) {
    clearAllErrors();
    const headers = { "Content-Type": "application/json" };
    
    if (requiresAuth) {
        const accessToken = localStorage.getItem("accessToken");
        if (accessToken) {
            headers["Authorization"] = `Bearer ${accessToken}`;
        }
    }

    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            method: method,
            headers: headers,
            body: payload ? JSON.stringify(payload) : null
        });

        const result = await response.json();

        if (!response.ok || !result.success) {
            // Check if backend returned field-level binding errors (Spring MethodArgumentNotValidException)
            if (result.errors && typeof result.errors === "object") {
                // Map backend DTO field names to HTML Input IDs
                const fieldMap = {
                    email: getActiveEmailInputId(),
                    password: "loginPassword",
                    name: "regName",
                    otp: "otpInput",
                    newPassword: "newPasswordInput"
                };

                Object.keys(result.errors).forEach(field => {
                    const inputId = fieldMap[field] || field;
                    setFieldError(inputId, result.errors[field]);
                });
            } else {
                // General exception / business error message
                showAlert(result.message || "Operation failed. Please try again.", "error");
            }
            throw new Error(result.message || "Validation failed");
        }
        return result;
    } catch (err) {
        if (!err.message.includes("Validation failed")) {
            showAlert(err.message, "error");
        }
        throw err;
    }
}

// Utility to find which email input is currently active
function getActiveEmailInputId() {
    if (!document.getElementById("loginView")?.classList.contains("hidden")) return "loginEmail";
    if (!document.getElementById("registerView")?.classList.contains("hidden")) return "regEmail";
    if (!document.getElementById("forgotPasswordView")?.classList.contains("hidden")) return "forgotEmail";
    if (!document.getElementById("accountSettingsView")?.classList.contains("hidden")) return "deleteEmail";
    return "loginEmail";
}

// --- Client Validation Regexes ---
const RegexRules = {
    email: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
    otp: /^\d{6}$/,
    password: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,64}$/,
    name: /^[A-Za-z\s'-]{2,100}$/
};

// Attach listeners to clear error dynamically on input
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll("input").forEach(input => {
        input.addEventListener("input", (e) => {
            clearFieldError(e.target.id);
        });
    });
});

// --- Form Listeners ---

// 1. Register Form
document.getElementById("registerForm")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    clearAllErrors();

    const name = document.getElementById("regName").value.trim();
    const email = document.getElementById("regEmail").value.trim();
    const password = document.getElementById("regPassword").value;

    let hasError = false;

    if (!RegexRules.name.test(name)) {
        setFieldError("regName", "Name must contain only letters, spaces, apostrophes, or hyphens (2-100 characters).");
        hasError = true;
    }
    if (!RegexRules.email.test(email)) {
        setFieldError("regEmail", "Invalid email format.");
        hasError = true;
    }
    if (!RegexRules.password.test(password)) {
        setFieldError("regPassword", "Password must contain 1 uppercase, 1 lowercase, 1 number, 1 special character (@$!%*?&), and be 8-64 characters.");
        hasError = true;
    }

    if (hasError) return;

    try {
        await apiCall("/register", { name, email, password });
        state.email = email;
        state.flow = "REGISTER";
        setupOtpView("Verify Registration", "Account OTP sent successfully.");
    } catch (_) {}
});

// 2. Login Form
document.getElementById("loginForm")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    clearAllErrors();

    const email = document.getElementById("loginEmail").value.trim();
    const password = document.getElementById("loginPassword").value;

    let hasError = false;

    if (!RegexRules.email.test(email)) {
        setFieldError("loginEmail", "Invalid email format.");
        hasError = true;
    }
    if (!password) {
        setFieldError("loginPassword", "Password is required.");
        hasError = true;
    }

    if (hasError) return;

    try {
        await apiCall("/login", { email, password });
        state.email = email;
        state.flow = "LOGIN";
        setupOtpView("Verify Login", "Login OTP sent to your email.");
    } catch (_) {}
});

// 3. Forgot Password Request Form
document.getElementById("forgotPasswordForm")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    clearAllErrors();

    const email = document.getElementById("forgotEmail").value.trim();

    if (!RegexRules.email.test(email)) {
        return setFieldError("forgotEmail", "Invalid email address.");
    }

    try {
        await apiCall("/forgot-password", { email });
        state.email = email;
        state.flow = "FORGOT_PASSWORD";
        setupOtpView("Reset Password", "Password reset OTP sent to your email.");
    } catch (_) {}
});

// 4. Request Account Deletion Form
document.getElementById("requestDeletionForm")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    clearAllErrors();

    const email = document.getElementById("deleteEmail").value.trim();

    if (!RegexRules.email.test(email)) {
        return setFieldError("deleteEmail", "Invalid email address.");
    }

    try {
        await apiCall("/request-account-deletion", { email });
        state.email = email;
        state.flow = "DELETE_ACCOUNT";
        setupOtpView("Confirm Deletion", "Account deletion OTP sent to your email.");
    } catch (_) {}
});

// 5. Universal OTP Form Dispatcher & Redirect Logic
document.getElementById('otpForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAllErrors();

    const otp = document.getElementById('otpInput').value.trim();
    const email = state.email || document.getElementById('otpTargetEmail').innerText;

    if (!RegexRules.otp.test(otp)) {
        return setFieldError("otpInput", "Enter a valid 6-digit OTP code.");
    }

    try {
        let endpoint = "";
        let payload = { email, otp };

        // Route request to correct backend endpoint based on active state flow
        switch (state.flow) {
            case "REGISTER":
                endpoint = "/verify-registration-otp";
                break;
            case "FORGOT_PASSWORD":
                const newPassword = document.getElementById("newPasswordInput").value;
                if (!RegexRules.password.test(newPassword)) {
                    return setFieldError("newPasswordInput", "Password must meet complexity requirements.");
                }
                endpoint = "/reset-password";
                payload.newPassword = newPassword;
                break;
            case "DELETE_ACCOUNT":
                endpoint = "/confirm-account-deletion";
                break;
            case "LOGIN":
            default:
                endpoint = "/verify-login-otp";
                break;
        }

        const result = await apiCall(endpoint, payload);

        // Handle flows that return Access and Refresh Tokens (Login & Register Verification)
        if (result.data && result.data.accessToken) {
            const { accessToken, refreshToken } = result.data;

            localStorage.setItem('accessToken', accessToken);
            localStorage.setItem('refreshToken', refreshToken);
            localStorage.setItem('userEmail', email);

            // Decode JWT to extract role
            let role = '';
            try {
                const tokenPayload = JSON.parse(atob(accessToken.split('.')[1]));
                role = tokenPayload.role || tokenPayload.roles || '';
            } catch (_) {}

            showAlert('Verification successful! Redirecting...', 'success');

            // Redirect based on role or admin email
            setTimeout(() => {
                if (role.includes('ADMIN') || email.includes('admin')) {
                    window.location.href = '/admin.html';
                } else if (role.includes('THEATRE_OWNER')) {
                    window.location.href = '/owner.html';
                } else {
                    window.location.href = '/index.html';
                }
            }, 1000);
        } else {
            // For Reset Password or Account Deletion flows
            showAlert(result.message || 'Action completed successfully.', 'success');
            setTimeout(() => switchView("loginView"), 1500);
        }

    } catch (err) {
        // Errors are automatically caught and set by apiCall()
    }
});

// --- Resend OTP Handler ---
async function handleResendOtp() {
    if (!state.email) return showAlert("Email context lost. Please restart the process.");

    const endpoint = state.flow === "LOGIN" ? "/login" : "/resend-registration-otp";
    try {
        await apiCall(endpoint, { email: state.email });
        showAlert("A fresh OTP has been sent to your inbox.", "success");
    } catch (_) {}
}

// --- Logout Handlers ---
async function handleLogout() {
    const refreshToken = localStorage.getItem("refreshToken");
    if (refreshToken) {
        try {
            await apiCall("/logout", { refreshToken });
        } catch (_) {}
    }
    localStorage.clear();
    showAlert("Logged out successfully.", "success");
    switchView("loginView");
}

async function handleLogoutAll() {
    try {
        await apiCall("/logout-all", null, "POST", true);
    } catch (_) {}
    localStorage.clear();
    showAlert("Logged out from all devices.", "success");
    switchView("loginView");
}

// Helper to configure OTP view state
function setupOtpView(title, message) {
    document.getElementById("otpTitle").textContent = title;
    document.getElementById("otpTargetEmail").textContent = state.email;
    document.getElementById("otpInput").value = "";
    document.getElementById("newPasswordInput").value = "";

    const newPassGroup = document.getElementById("newPasswordGroup");
    const resendBtn = document.getElementById("resendOtpBtn");

    if (newPassGroup) {
        if (state.flow === "FORGOT_PASSWORD") {
            newPassGroup.classList.remove("hidden");
        } else {
            newPassGroup.classList.add("hidden");
        }
    }

    if (resendBtn) {
        if (state.flow === "REGISTER" || state.flow === "LOGIN") {
            resendBtn.classList.remove("hidden");
        } else {
            resendBtn.classList.add("hidden");
        }
    }

    switchView("otpView");
    showAlert(message, "success");
}

// On page load check session state
window.addEventListener("DOMContentLoaded", () => {
    if (localStorage.getItem("accessToken")) {
        // If logged-in user is an admin, take them straight to admin panel
        try {
            const token = localStorage.getItem("accessToken");
            const payload = JSON.parse(atob(token.split('.')[1]));
            const role = payload.role || payload.roles || '';
            if (role.includes('ADMIN')) {
                window.location.href = '/admin.html';
                return;
            }
            if (role.includes('THEATRE_OWNER')) {
                window.location.href = '/owner.html';
                return;
            }
        } catch (_) {}

        switchView("accountSettingsView");
    } else {
        switchView("loginView");
    }
});

// --- My Bookings Feature ---
async function loadMyBookings() {
    const container = document.getElementById('bookingsListContainer');
    if (!container) return;

    container.innerHTML = '<p style="text-align: center; color: var(--text-muted); padding: 40px 0;">Loading your bookings...</p>';

    try {
        const accessToken = localStorage.getItem('accessToken');
        if (!accessToken) {
            container.innerHTML = '<p style="text-align: center; color: #ef4444;">Please log in to view your bookings.</p>';
            return;
        }

        const response = await fetch(`${BOOKING_API_BASE}/my-bookings`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`
            }
        });

        const result = await response.json();

        if (!result.success || !result.data || result.data.length === 0) {
            container.innerHTML = '<p style="text-align: center; color: var(--text-muted); padding: 40px 0;">You have no bookings yet. Book your first movie ticket!</p>';
            return;
        }

        const bookings = result.data;
        let html = '';

        bookings.forEach(booking => {
            const statusClass = getStatusClass(booking.status);
            const statusLabel = formatBookingStatus(booking.status);
            const showDate = new Date(booking.showStartTime).toLocaleString('en-IN', { 
                dateStyle: 'medium', 
                timeStyle: 'short' 
            });
            
            const seatCodes = booking.seatCodes.join(', ');
            const totalAmount = booking.totalAmount;

            html += `
                <div class="booking-card" style="border: 1px solid #3f3f46; border-radius: 8px; padding: 16px; margin-bottom: 16px; background: #18181b;">
                    <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 12px;">
                        <div>
                            <h3 style="margin: 0 0 4px 0; color: #e4e4e7; font-size: 1.1rem;">${escapeHtml(booking.movieTitle)}</h3>
                            <p style="margin: 0; color: #a1a1aa; font-size: 0.875rem;">${escapeHtml(booking.theatreName)} - ${escapeHtml(booking.screenName)}</p>
                        </div>
                        <span class="badge ${statusClass}" style="padding: 4px 12px; border-radius: 999px; font-size: 0.75rem; font-weight: 600;">${statusLabel}</span>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 12px;">
                        <div>
                            <p style="margin: 0; color: #71717a; font-size: 0.75rem;">SHOW TIME</p>
                            <p style="margin: 4px 0 0 0; color: #e4e4e7; font-size: 0.875rem;">${showDate}</p>
                        </div>
                        <div>
                            <p style="margin: 0; color: #71717a; font-size: 0.75rem;">SEATS</p>
                            <p style="margin: 4px 0 0 0; color: #e4e4e7; font-size: 0.875rem;">${seatCodes}</p>
                        </div>
                        <div>
                            <p style="margin: 0; color: #71717a; font-size: 0.75rem;">TOTAL</p>
                            <p style="margin: 4px 0 0 0; color: #e4e4e7; font-size: 0.875rem;">₹ ${totalAmount}</p>
                        </div>
                        <div>
                            <p style="margin: 0; color: #71717a; font-size: 0.75rem;">TRANSACTION ID</p>
                            <p style="margin: 4px 0 0 0; color: #e4e4e7; font-size: 0.75rem; word-break: break-all;">${escapeHtml(booking.transactionId)}</p>
                        </div>
                    </div>

                    ${booking.holdExpiresAt && booking.status === 'PENDING_PAYMENT' ? `
                        <div style="margin-top: 12px; padding: 8px 12px; background: rgba(239, 68, 68, 0.1); border-radius: 6px; border: 1px solid #ef4444;">
                            <p style="margin: 0; color: #fca5a5; font-size: 0.75rem;">
                                ⏰ Hold expires at: ${new Date(booking.holdExpiresAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
                            </p>
                        </div>
                    ` : ''}

                    ${booking.status === 'PENDING_PAYMENT' ? `
                        <div style="margin-top: 12px; display: flex; gap: 8px;">
                            <button class="btn btn-primary btn-sm" onclick="continuePayment(${booking.bookingId})">Continue Payment</button>
                            <button class="btn btn-secondary btn-sm" onclick="cancelBooking(${booking.bookingId})">Cancel Booking</button>
                        </div>
                    ` : ''}
                </div>
            `;
        });

        container.innerHTML = html;
    } catch (err) {
        console.error('[LOAD MY BOOKINGS]', err);
        container.innerHTML = '<p style="text-align: center; color: #ef4444; padding: 40px 0;">Failed to load bookings. Please try again.</p>';
    }
}

function getStatusClass(status) {
    switch (status) {
        case 'CONFIRMED': return 'badge-gold';
        case 'PENDING_PAYMENT': return 'badge-orange';
        case 'CANCELLED': return 'badge-red';
        case 'EXPIRED': return 'badge-gray';
        default: return 'badge-gray';
    }
}

function formatBookingStatus(status) {
    return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

async function continuePayment(bookingId) {
    showAlert('Payment integration coming soon. Your seats are held for 10 minutes.', 'info');
}

async function cancelBooking(bookingId) {
    if (!confirm('Are you sure you want to cancel this booking? The seats will be released.')) {
        return;
    }

    try {
        const accessToken = localStorage.getItem('accessToken');
        const response = await fetch(`${BOOKING_API_BASE}/${bookingId}/cancel`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${accessToken}`
            }
        });

        const result = await response.json();
        if (result.success) {
            showAlert('Booking cancelled successfully. Seats have been released.', 'success');
            loadMyBookings();
        } else {
            showAlert(result.message || 'Failed to cancel booking.', 'error');
        }
    } catch (err) {
        console.error('[CANCEL BOOKING]', err);
        showAlert('Failed to cancel booking. Please try again.', 'error');
    }
}