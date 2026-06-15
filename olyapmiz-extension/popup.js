/**
 * Popup host — shows a small live preview and hosts the shared SettingsUI
 * panel. All settings flow through OlyapmizSettings, so edits here update the
 * new tab (and vice-versa) automatically.
 */

const S = window.OlyapmizSettings;
const byId = (id) => document.getElementById(id);

let committed = S.getDefaults();

function renderPreview(s) {
    const canvas = byId('previewCanvas');
    if (!canvas) return;
    // Render at device-pixel resolution; CSS pins the display size.
    const dpr = Math.max(1, window.devicePixelRatio || 1);
    canvas.width = Math.round((canvas.clientWidth || 380) * dpr);
    canvas.height = Math.round((canvas.clientHeight || 150) * dpr);
    const view = S.currentEffectiveMode(Date.now(), s);
    if (view === 'UMR') OlyapmizUmr.render(canvas, s);
    else OlyapmizYil.render(canvas, s);
}

async function init() {
    committed = await S.init();
    S.onChange((s) => { committed = s; renderPreview(s); });
    SettingsUI.wire({ onPreview: renderPreview });
    renderPreview(committed);
}

document.addEventListener('DOMContentLoaded', init);
