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
    canvas.width = canvas.clientWidth || 380;
    canvas.height = canvas.clientHeight || 150;
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
