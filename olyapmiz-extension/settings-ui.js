/**
 * Shared settings-panel UI — wires the tabbed settings panel that exists in the
 * DOM of both the new-tab page and the toolbar popup. Single source of truth for
 * the panel's behavior so both surfaces look and act identically.
 *
 * The panel MARKUP must already be present in the page (same IDs / classes /
 * data-attrs in newtab.html and popup.html). The HOST owns its own canvas and
 * provides an onPreview(settings) callback used during live slider drags so it
 * can re-render without persisting. Committed changes flow through
 * OlyapmizSettings and reach every surface via storage events.
 *
 * Host wires its own chrome (open/close button, canvas render, timers).
 *
 * API:
 *   SettingsUI.wire({ onPreview })   // attach listeners + initial sync
 *   SettingsUI.refresh()             // re-sync from current settings (e.g. on open)
 *   SettingsUI.getActiveTab()
 */
window.SettingsUI = (function () {
    'use strict';

    const S = window.OlyapmizSettings;
    const byId = (id) => document.getElementById(id);

    let settings = S.getDefaults();
    let activeTab = 'YIL';          // which view's settings the panel is editing
    let onPreview = null;           // host live-render callback

    function preview() { if (onPreview) onPreview(settings); }

    // ---- Path helpers --------------------------------------------------
    function getPath(obj, path) {
        return path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj);
    }
    function setPathLocal(obj, path, value) {
        const parts = path.split('.');
        let node = obj;
        for (let i = 0; i < parts.length - 1; i++) {
            if (node[parts[i]] == null) node[parts[i]] = {};
            node = node[parts[i]];
        }
        node[parts[parts.length - 1]] = value;
    }

    function editView() { return activeTab; }

    function alphaPaths() {
        return editView() === 'UMR'
            ? ['umrSettings.livedAlpha', 'umrSettings.emptyAlpha']
            : ['filledDotAlpha', 'emptyDotAlpha'];
    }
    function posPaths() {
        return editView() === 'UMR'
            ? ['umrSettings.position.horizontalOffset', 'umrSettings.position.verticalOffset', 'umrSettings.position.scale']
            : ['positionSettings.horizontalOffset', 'positionSettings.verticalOffset', 'positionSettings.scale'];
    }
    function statsPath() {
        return editView() === 'UMR' ? 'umrSettings.statsBandOffset' : 'yilStatsBandOffset';
    }

    // ---- dd/mm/yyyy helpers --------------------------------------------
    function epochToDateInput(ms) {
        if (!ms) return '';
        const d = new Date(ms);
        const p = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
    }
    function parseDMY(str) {
        const m = String(str).trim().match(/^(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{2}|\d{4})$/);
        if (!m) return null;
        let d = +m[1], mo = +m[2], y = +m[3];
        if (m[3].length === 2) y += (y <= (new Date().getFullYear() % 100)) ? 2000 : 1900;
        if (mo < 1 || mo > 12 || d < 1 || d > 31) return null;
        const date = new Date(y, mo - 1, d);
        if (date.getFullYear() !== y || date.getMonth() !== mo - 1 || date.getDate() !== d) return null;
        return date.getTime();
    }
    function formatDMY(ms) {
        if (!ms) return '';
        const d = new Date(ms);
        const p = (n) => String(n).padStart(2, '0');
        return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
    }

    // ---- Tab visibility ------------------------------------------------
    function applyTabVisibility() {
        const tab = activeTab.toLowerCase();
        document.querySelectorAll('.settings-section[data-scope]').forEach((sec) => {
            const scope = sec.dataset.scope;
            const show = scope === 'both' || scope === tab;
            if (sec.id === 'customColorsSection') {
                sec.classList.toggle('hidden', !(show && settings.theme === 'CUSTOM'));
            } else {
                sec.classList.toggle('hidden', !show);
            }
        });
        document.querySelectorAll('.tab-btn').forEach((b) =>
            b.classList.toggle('active', b.dataset.tab === activeTab));
    }

    // ---- Event wiring --------------------------------------------------
    function setupEventListeners() {
        // Tabs — only switch which view's settings are shown (display unchanged).
        document.querySelectorAll('.tab-btn').forEach((btn) => {
            btn.addEventListener('click', () => {
                activeTab = btn.dataset.tab;
                syncPanelFromSettings();
                updateSaveLabel();
            });
        });

        // Save — persist + show the view whose settings you're editing.
        byId('saveViewBtn').addEventListener('click', () => {
            S.set('topViewMode', activeTab);
            flashSaved();
        });

        // Birthdays (Umr) — dd/mm/yyyy text entry
        const bindBirthday = (id, path) => {
            const input = byId(id);
            input.addEventListener('change', (e) => {
                const raw = e.target.value.trim();
                if (raw === '') { input.classList.remove('invalid'); S.set(path, 0); return; }
                const ms = parseDMY(raw);
                if (ms == null) { input.classList.add('invalid'); return; }
                input.classList.remove('invalid');
                input.value = formatDMY(ms);
                S.set(path, ms);
            });
            input.addEventListener('input', () => input.classList.remove('invalid'));
        };
        bindBirthday('meBirthday', 'umrSettings.birthdayEpochMs');
        bindBirthday('momBirthday', 'umrSettings.momBirthdayEpochMs');
        bindBirthday('dadBirthday', 'umrSettings.dadBirthdayEpochMs');

        // Auto-switch
        byId('autoSwitchToggle').addEventListener('change', (e) => S.setAutoSwitchEnabled(e.target.checked));
        byId('autoSwitchInterval').addEventListener('change', (e) =>
            S.set('autoSwitchSettings.intervalMs', parseInt(e.target.value, 10)));

        // Theme
        document.querySelectorAll('.theme-btn').forEach((btn) =>
            btn.addEventListener('click', () => S.set('theme', btn.dataset.theme)));

        // Custom colors
        const colorMap = {
            customBg: 'customColors.backgroundColor',
            customFilled: 'customColors.filledDotColor',
            customEmpty: 'customColors.emptyDotColor',
            customToday: 'customColors.todayDotColor',
        };
        Object.entries(colorMap).forEach(([id, path]) => {
            byId(id).addEventListener('input', (e) => S.set(path, S.color.fromHex(e.target.value)));
        });

        // Dot shape / style chips (Yil)
        document.querySelectorAll('#shapeGroup .chip').forEach((c) =>
            c.addEventListener('click', () => S.set('dotShape', c.dataset.shape)));
        document.querySelectorAll('#styleGroup .chip').forEach((c) =>
            c.addEventListener('click', () => S.set('dotEffectSettings.style', c.dataset.dotstyle)));

        // Umr marks
        document.querySelectorAll('.style-btn').forEach((btn) =>
            btn.addEventListener('click', () => S.set('umrSettings.visualMode', btn.dataset.style)));

        // Calendar (Yil)
        document.querySelectorAll('.col-btn').forEach((b) =>
            b.addEventListener('click', () => S.set('calendarViewSettings.columnsPerRow', parseInt(b.dataset.cols, 10))));
        byId('highlightToday').addEventListener('change', (e) => S.set('highlightToday', e.target.checked));
        byId('showStats').addEventListener('change', (e) => S.set('calendarViewSettings.showYearStats', e.target.checked));
        byId('showMonthLabels').addEventListener('change', (e) => S.set('viewModeSettings.showMonthLabels', e.target.checked));

        // Transparency (active tab)
        bindSlider('filledAlpha', 'filledAlphaVal', (v) => setPathLocal(settings, alphaPaths()[0], v / 100), () => alphaPaths()[0]);
        bindSlider('emptyAlpha', 'emptyAlphaVal', (v) => setPathLocal(settings, alphaPaths()[1], v / 100), () => alphaPaths()[1]);

        // Position (active tab)
        bindSlider('hOffset', 'hOffsetVal', (v) => setPathLocal(settings, posPaths()[0], v), () => posPaths()[0]);
        bindSlider('vOffset', 'vOffsetVal', (v) => setPathLocal(settings, posPaths()[1], v), () => posPaths()[1]);
        bindSlider('scale', 'scaleVal', (v) => setPathLocal(settings, posPaths()[2], v / 100), () => posPaths()[2]);
        bindSlider('statsOffset', 'statsOffsetVal', (v) => setPathLocal(settings, statsPath(), v), () => statsPath());

        // Goals / Events
        byId('goalsEnabled').addEventListener('change', (e) => S.set('goalSettings.enabled', e.target.checked));
        byId('eventsEnabled').addEventListener('change', (e) => S.set('eventSettings.enabled', e.target.checked));
        byId('addGoalBtn').addEventListener('click', () => openEditor('goal'));
        byId('addEventBtn').addEventListener('click', () => openEditor('event'));

        // Total weeks
        byId('totalWeeks').addEventListener('change', (e) => {
            const w = parseInt(e.target.value, 10) || 4000;
            byId('yearsLabel').textContent = Math.floor(w / 52);
            S.set('umrSettings.totalWeeks', w);
        });

        byId('exportBtn').addEventListener('click', handleExport);
        byId('importBtn').addEventListener('click', handleImport);
    }

    /** Slider with live preview on input (via onPreview), persist on release. */
    function bindSlider(sliderId, valueId, applyLocal, pathFn) {
        const slider = byId(sliderId);
        const valueEl = byId(valueId);
        slider.addEventListener('input', (e) => {
            const raw = parseInt(e.target.value, 10);
            if (valueEl) valueEl.textContent = `${raw}%`;
            applyLocal(raw);
            preview();
        });
        slider.addEventListener('change', () => S.set(pathFn(), getPath(settings, pathFn())));
    }

    // ---- Save button label / flash -------------------------------------
    let savedFlashTimer = null;
    function updateSaveLabel() {
        const btn = byId('saveViewBtn');
        if (!btn || savedFlashTimer) return;
        btn.textContent = `Save & show ${activeTab === 'UMR' ? 'Umr' : 'Yil'}`;
    }
    function flashSaved() {
        const btn = byId('saveViewBtn');
        if (!btn) return;
        btn.textContent = 'Saved ✓';
        btn.classList.add('saved');
        if (savedFlashTimer) clearTimeout(savedFlashTimer);
        savedFlashTimer = setTimeout(() => {
            savedFlashTimer = null;
            btn.classList.remove('saved');
            updateSaveLabel();
        }, 1400);
    }

    // ---- Goal / Event list + editor ------------------------------------
    function shortDate(ms) {
        return new Date(ms).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
    }
    function relativeLabel(ms, unit) {
        const now = Date.now();
        if (ms < now) return 'passed';
        const days = Math.ceil((ms - now) / 86400000);
        if (unit === 'weeks') return `${Math.ceil(days / 7)} weeks left`;
        return `${days}d left`;
    }
    function renderList(containerId, items, type) {
        const container = byId(containerId);
        container.innerHTML = '';
        const unit = type === 'event' ? 'weeks' : 'days';
        items.forEach((item) => {
            const row = document.createElement('div');
            row.className = 'list-item';
            const swatch = document.createElement('span');
            swatch.className = 'swatch';
            swatch.style.background = S.color.css(item.color);
            const text = document.createElement('div');
            text.className = 'li-text';
            const title = document.createElement('div');
            title.className = 'li-title';
            title.textContent = item.title || '(untitled)';
            const sub = document.createElement('div');
            sub.className = 'li-sub';
            sub.textContent = `${shortDate(item.targetDate)} · ${relativeLabel(item.targetDate, unit)}`;
            text.append(title, sub);
            const edit = document.createElement('button');
            edit.className = 'icon-btn';
            edit.textContent = '✎';
            edit.title = 'Edit';
            edit.addEventListener('click', () => openEditor(type, item));
            const del = document.createElement('button');
            del.className = 'icon-btn danger';
            del.textContent = '🗑';
            del.title = 'Delete';
            del.addEventListener('click', () => {
                if (type === 'goal') S.deleteGoal(item.id); else S.deleteEvent(item.id);
            });
            row.append(swatch, text, edit, del);
            container.appendChild(row);
        });
    }

    let openEditorEl = null;
    function closeEditor() {
        if (openEditorEl && openEditorEl.parentNode) openEditorEl.parentNode.removeChild(openEditorEl);
        openEditorEl = null;
    }
    function openEditor(type, existing) {
        closeEditor();
        const isGoal = type === 'goal';
        const addBtn = byId(isGoal ? 'addGoalBtn' : 'addEventBtn');

        const editor = document.createElement('div');
        editor.className = 'editor';
        editor.innerHTML = `
            <div class="editor-row">
                <label>Title</label>
                <input type="text" class="ed-title" placeholder="${isGoal ? 'e.g. Wedding' : 'e.g. Graduation'}">
            </div>
            <div class="editor-row">
                <label>Date</label>
                <input type="date" class="ed-date">
            </div>
            <div class="editor-row">
                <label>Color</label>
                <input type="color" class="ed-color" value="#E53935">
            </div>
            <div class="editor-actions">
                <button class="btn ed-cancel">Cancel</button>
                <button class="btn primary ed-save">Save</button>
            </div>`;

        const titleEl = editor.querySelector('.ed-title');
        const dateEl = editor.querySelector('.ed-date');
        const colorEl = editor.querySelector('.ed-color');
        if (existing) {
            titleEl.value = existing.title || '';
            dateEl.value = epochToDateInput(existing.targetDate);
            colorEl.value = S.color.toHex(existing.color);
        }

        editor.querySelector('.ed-cancel').addEventListener('click', closeEditor);
        editor.querySelector('.ed-save').addEventListener('click', () => {
            const title = titleEl.value.trim();
            const targetDate = dateEl.value ? new Date(dateEl.value + 'T00:00:00').getTime() : 0;
            const color = S.color.fromHex(colorEl.value);
            if (!title || !targetDate) { titleEl.focus(); return; }
            if (existing) {
                const upd = { ...existing, title, targetDate, color };
                isGoal ? S.updateGoal(upd) : S.updateEvent(upd);
            } else {
                isGoal ? S.addGoal({ title, targetDate, color }) : S.addEvent({ title, targetDate, color });
            }
            closeEditor();
        });

        addBtn.parentNode.insertBefore(editor, addBtn);
        openEditorEl = editor;
        titleEl.focus();
    }

    // ---- Sync panel from settings --------------------------------------
    function syncPanelFromSettings() {
        const u = settings.umrSettings;

        applyTabVisibility();

        byId('meBirthday').value = formatDMY(u.birthdayEpochMs);
        byId('momBirthday').value = formatDMY(u.momBirthdayEpochMs);
        byId('dadBirthday').value = formatDMY(u.dadBirthdayEpochMs);

        document.querySelectorAll('.theme-btn').forEach((b) =>
            b.classList.toggle('active', b.dataset.theme === settings.theme));
        document.querySelectorAll('.style-btn').forEach((b) =>
            b.classList.toggle('active', b.dataset.style === u.visualMode));
        document.querySelectorAll('#shapeGroup .chip').forEach((c) =>
            c.classList.toggle('active', c.dataset.shape === settings.dotShape));
        document.querySelectorAll('#styleGroup .chip').forEach((c) =>
            c.classList.toggle('active', c.dataset.dotstyle === settings.dotEffectSettings.style));
        document.querySelectorAll('.col-btn').forEach((b) =>
            b.classList.toggle('active', parseInt(b.dataset.cols, 10) === settings.calendarViewSettings.columnsPerRow));

        byId('autoSwitchToggle').checked = settings.autoSwitchSettings.enabled;
        byId('autoSwitchRow').classList.toggle('hidden', !settings.autoSwitchSettings.enabled);
        byId('autoSwitchInterval').value = String(settings.autoSwitchSettings.intervalMs);

        byId('highlightToday').checked = settings.highlightToday;
        byId('showStats').checked = settings.calendarViewSettings.showYearStats;
        byId('showMonthLabels').checked = settings.viewModeSettings.showMonthLabels;

        if (settings.theme === 'CUSTOM') {
            byId('customBg').value = S.color.toHex(settings.customColors.backgroundColor);
            byId('customFilled').value = S.color.toHex(settings.customColors.filledDotColor);
            byId('customEmpty').value = S.color.toHex(settings.customColors.emptyDotColor);
            byId('customToday').value = S.color.toHex(settings.customColors.todayDotColor);
        }

        const view = editView();
        byId('alphaModeTag').textContent = view;
        byId('posModeTag').textContent = view;
        const ap = alphaPaths(), pp = posPaths(), sp = statsPath();
        const setSlider = (id, valId, val) => {
            byId(id).value = val;
            if (byId(valId)) byId(valId).textContent = `${val}%`;
        };
        setSlider('filledAlpha', 'filledAlphaVal', Math.round(getPath(settings, ap[0]) * 100));
        setSlider('emptyAlpha', 'emptyAlphaVal', Math.round(getPath(settings, ap[1]) * 100));
        setSlider('hOffset', 'hOffsetVal', Math.round(getPath(settings, pp[0])));
        setSlider('vOffset', 'vOffsetVal', Math.round(getPath(settings, pp[1])));
        setSlider('scale', 'scaleVal', Math.round(getPath(settings, pp[2]) * 100));
        setSlider('statsOffset', 'statsOffsetVal', Math.round(getPath(settings, sp)));

        byId('goalsEnabled').checked = settings.goalSettings.enabled;
        byId('eventsEnabled').checked = settings.eventSettings.enabled;
        renderList('goalsList', settings.goalSettings.goals, 'goal');
        renderList('eventsList', settings.eventSettings.events, 'event');

        byId('totalWeeks').value = u.totalWeeks;
        byId('yearsLabel').textContent = Math.floor(u.totalWeeks / 52);
    }

    // ---- Import / export -----------------------------------------------
    async function handleExport() {
        const statusEl = byId('dataStatus');
        try {
            await navigator.clipboard.writeText(JSON.stringify(S.get(), null, 2));
            statusEl.textContent = 'Settings copied to clipboard!';
            statusEl.className = 'data-status success';
        } catch (e) {
            statusEl.textContent = 'Export failed';
            statusEl.className = 'data-status error';
        }
        setTimeout(() => { statusEl.textContent = ''; statusEl.className = 'data-status'; }, 3000);
    }
    async function handleImport() {
        const statusEl = byId('dataStatus');
        try {
            const text = await navigator.clipboard.readText();
            await S.save(JSON.parse(text));
            statusEl.textContent = 'Settings imported!';
            statusEl.className = 'data-status success';
        } catch (e) {
            statusEl.textContent = 'Import failed — check clipboard';
            statusEl.className = 'data-status error';
        }
        setTimeout(() => { statusEl.textContent = ''; statusEl.className = 'data-status'; }, 3000);
    }

    // ---- Public API ----------------------------------------------------
    function wire(opts) {
        opts = opts || {};
        onPreview = opts.onPreview || null;
        settings = S.get();
        activeTab = settings.topViewMode === 'UMR' ? 'UMR' : 'YIL';
        setupEventListeners();
        syncPanelFromSettings();
        updateSaveLabel();
        S.onChange((s) => { settings = s; syncPanelFromSettings(); });
    }
    function refresh() {
        settings = S.get();
        syncPanelFromSettings();
        updateSaveLabel();
    }

    return { wire, refresh, getActiveTab: () => activeTab };
})();
