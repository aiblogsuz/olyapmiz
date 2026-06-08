/**
 * New-tab host — owns the full-screen canvas, auto-switch rotation, refresh
 * triggers, and the gear button. The settings panel itself is wired by the
 * shared SettingsUI module (same panel the popup uses).
 */

const S = window.OlyapmizSettings;
const byId = (id) => document.getElementById(id);

let committed = S.getDefaults();
let lastView = null;
let autoTimer = null;
let midnightTimer = null;

// ---- Rendering ---------------------------------------------------------
function renderView(s) {
    const canvas = byId('mainCanvas');
    if (!canvas) return;
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    const view = S.currentEffectiveMode(Date.now(), s);
    lastView = view;
    if (view === 'UMR') OlyapmizUmr.render(canvas, s);
    else OlyapmizYil.render(canvas, s);

    const indicator = byId('viewIndicator');
    if (indicator) indicator.textContent = view === 'UMR' ? 'Umr · life in weeks' : 'Yil · calendar year';
}
function render() { renderView(committed); }

function updateDateIndicator() {
    const el = byId('dateIndicator');
    if (!el) return;
    el.textContent = new Date().toLocaleDateString('en-US',
        { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}

// ---- Auto-switch + midnight refresh ------------------------------------
function restartAutoTimer() {
    if (autoTimer) { clearInterval(autoTimer); autoTimer = null; }
    if (!committed.autoSwitchSettings.enabled) return;
    const poll = Math.max(250, Math.min(committed.autoSwitchSettings.intervalMs, 1000));
    autoTimer = setInterval(() => {
        if (S.currentEffectiveMode(Date.now(), committed) !== lastView) render();
    }, poll);
}

function scheduleMidnight() {
    if (midnightTimer) clearTimeout(midnightTimer);
    const now = new Date();
    const next = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 3);
    midnightTimer = setTimeout(() => {
        render();
        updateDateIndicator();
        scheduleMidnight();
    }, next.getTime() - now.getTime());
}

// ---- Init --------------------------------------------------------------
async function init() {
    committed = await S.init();

    S.onChange((s) => {
        committed = s;
        restartAutoTimer();
        render();
    });

    // Host chrome: open / close the settings panel.
    byId('settingsBtn').addEventListener('click', () => {
        const panel = byId('settingsPanel');
        const opening = panel.classList.contains('hidden');
        panel.classList.toggle('hidden');
        if (opening) SettingsUI.refresh();
    });
    byId('closeSettings').addEventListener('click', () => byId('settingsPanel').classList.add('hidden'));

    // Shared panel — live slider drags re-render the canvas without persisting.
    SettingsUI.wire({ onPreview: (s) => renderView(s) });

    render();
    updateDateIndicator();
    scheduleMidnight();
    restartAutoTimer();

    window.addEventListener('resize', render);
    window.addEventListener('focus', () => { render(); updateDateIndicator(); });
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) { render(); updateDateIndicator(); }
    });
}

document.addEventListener('DOMContentLoaded', init);
