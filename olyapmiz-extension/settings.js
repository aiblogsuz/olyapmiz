/**
 * Unified settings module — single source of truth for the whole extension.
 *
 * Schema mirrors the Android app's WallpaperSettings (LifeDotsPreferences.kt)
 * so the new-tab page, popup, and content overlay all render the exact same
 * thing the wallpaper does. Persisted via chrome.storage.local (falls back to
 * window.localStorage when the extension storage API isn't available, e.g.
 * when a page is opened directly from disk during development).
 *
 * Colors are stored as 32-bit ARGB integers (0xFFRRGGBB), the same convention
 * the app and the existing renderers use. Use OlyapmizSettings.color.* to
 * convert to/from CSS and hex.
 *
 * Usage:
 *   await OlyapmizSettings.init();          // load + cache, run legacy migration
 *   const s = OlyapmizSettings.get();       // synchronous cached snapshot
 *   await OlyapmizSettings.update({ theme: 'DARK' });   // deep-merge patch + persist
 *   OlyapmizSettings.onChange(s => render(s));          // react to any writer
 */
(function (global) {
    'use strict';

    const STORAGE_KEY = 'olyapmiz_settings';        // v2 (nested, app-mirroring)
    const LEGACY_KEY = 'olyapmiz_nt_settings';      // v1 (flat newtab schema)
    const SCHEMA_VERSION = 2;

    // ---- Default settings (mirror the Kotlin data-class defaults) ----------
    function getDefaults() {
        return {
            schemaVersion: SCHEMA_VERSION,

            // Top-level look
            theme: 'AMOLED',                         // LIGHT | DARK | AMOLED | CUSTOM
            customColors: {
                backgroundColor: 0xFF1A1A1A,
                filledDotColor: 0xFFE0E0E0,
                emptyDotColor: 0xFF3A3A3A,
                todayDotColor: 0xFF5BA0E9,
            },
            dotSize: 'MEDIUM',                       // TINY|SMALL|MEDIUM|LARGE|HUGE
            dotShape: 'CIRCLE',                      // CIRCLE|SQUARE|ROUNDED_SQUARE|DIAMOND
            gridDensity: 'COMPACT',                  // COMPACT|NORMAL|RELAXED|SPACIOUS
            highlightToday: true,
            filledDotAlpha: 1.0,
            emptyDotAlpha: 1.0,
            yilStatsBandOffset: 0,                   // % of canvas height

            viewModeSettings: {
                showMonthLabels: true,
                monthLabelColor: 0xFFFFFFFF,
            },

            calendarViewSettings: {
                columnsPerRow: 3,                    // 3 (3x4) or 6 (6x2)
                showYearStats: true,
                mondayFirst: true,
                highlightCurrentWeek: true,
                currentWeekColor: 0xFFFFD54F,        // warm yellow (today glow)
            },

            // Feature 3: dot effects
            dotEffectSettings: {
                style: 'FLAT',                       // FLAT|GRADIENT|OUTLINED|SOFT_GLOW|NEON|EMBOSSED
                glowRadius: 8,
                outlineWidth: 2,
            },

            // Feature 6: Yil goal countdowns (dots + "Xd to <title>")
            goalSettings: {
                enabled: true,
                goals: [],                           // { id, title, targetDate, color }
            },

            // Umr events ("X weeks to <title>"), kept separate from goals
            eventSettings: {
                enabled: true,
                events: [],                          // { id, title, targetDate, color }
            },

            // Yil grid position/scale
            positionSettings: {
                horizontalOffset: 0,                 // -50..50 %
                verticalOffset: 0,                   // -50..50 %
                scale: 1.0,                          // 0.5..1.5
            },

            // View selection + auto rotation
            topViewMode: 'YIL',                      // YIL | UMR
            autoSwitchSettings: {
                enabled: false,
                intervalMs: 5000,
                referenceMs: 0,
                startMode: 'YIL',
            },

            // Umr (life-in-weeks) settings
            umrSettings: {
                birthdayEpochMs: 0,                  // 0 = unset
                momBirthdayEpochMs: 0,
                dadBirthdayEpochMs: 0,
                visualMode: 'DOTS',                  // DOTS | X_MARKS
                livedAlpha: 1.0,
                emptyAlpha: 0.6,
                totalWeeks: 4000,
                position: { horizontalOffset: 0, verticalOffset: 7, scale: 1.0 },
                statsBandOffset: 0,                  // % of canvas height
            },

            // ---- Advanced effects (canvas-feasible subset; default OFF) ----
            animationSettings: {
                enabled: false,
                type: 'NONE',                        // NONE|FADE_IN|PULSE|WAVE|BREATHE|RIPPLE|CASCADE
                speed: 1.0,
                intensity: 0.5,
            },
            glassEffectSettings: {
                enabled: false,
                style: 'NONE',                       // NONE|LIGHT_FROST|HEAVY_FROST|ACRYLIC|CRYSTAL|ICE
                blur: 10,
                opacity: 0.3,
                tint: 0x80FFFFFF,
            },
            visualTheme: 'CLASSIC',                  // CLASSIC|MINIMALIST|CYBERPUNK|NATURE|FLUID|GLASS|COSMIC
        };
    }

    // ---- Validation / deep-merge ------------------------------------------
    function isPlainObject(v) {
        return v !== null && typeof v === 'object' && !Array.isArray(v);
    }

    /**
     * Deep-merge `raw` onto `defaults`, keeping only keys defined in defaults
     * and only when the incoming type matches. Unknown keys are dropped; this
     * keeps stored data forward/backward safe.
     */
    function mergeOnto(defaults, raw) {
        if (!isPlainObject(raw)) return clone(defaults);
        const out = Array.isArray(defaults) ? defaults.slice() : {};
        for (const key of Object.keys(defaults)) {
            const dv = defaults[key];
            const rv = raw[key];
            if (rv === undefined || rv === null) {
                out[key] = clone(dv);
            } else if (isPlainObject(dv)) {
                out[key] = mergeOnto(dv, rv);
            } else if (Array.isArray(dv)) {
                out[key] = Array.isArray(rv) ? rv.slice() : clone(dv);
            } else if (typeof dv === typeof rv) {
                out[key] = rv;
            } else if (typeof dv === 'number' && typeof rv === 'string' && rv.trim() !== '' && !isNaN(Number(rv))) {
                out[key] = Number(rv);          // tolerate numeric strings
            } else {
                out[key] = clone(dv);
            }
        }
        return out;
    }

    function clone(v) {
        if (Array.isArray(v)) return v.slice();
        if (isPlainObject(v)) {
            const o = {};
            for (const k of Object.keys(v)) o[k] = clone(v[k]);
            return o;
        }
        return v;
    }

    function validate(raw) {
        return mergeOnto(getDefaults(), raw || {});
    }

    /**
     * Map the legacy flat newtab schema (v1) onto the nested v2 schema so
     * existing users keep their birthday/theme/position when they upgrade.
     */
    function migrateLegacy(flat) {
        if (!isPlainObject(flat)) return null;
        const d = getDefaults();
        const cc = flat.customColors || {};
        return validate({
            theme: flat.theme,
            customColors: {
                backgroundColor: cc.backgroundColor,
                filledDotColor: cc.filledDotColor,
                emptyDotColor: cc.emptyDotColor,
                todayDotColor: cc.todayDotColor,
            },
            topViewMode: flat.currentView,
            umrSettings: {
                birthdayEpochMs: flat.birthdayEpochMs,
                momBirthdayEpochMs: flat.momBirthdayEpochMs,
                dadBirthdayEpochMs: flat.dadBirthdayEpochMs,
                visualMode: flat.visualMode,
                livedAlpha: flat.livedAlpha,
                emptyAlpha: flat.emptyAlpha,
                totalWeeks: flat.totalWeeks,
                position: flat.position,
                statsBandOffset: flat.statsBandOffset,
            },
        });
    }

    // ---- Storage backend (chrome.storage.local with localStorage fallback) -
    const hasChromeStorage =
        typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local;

    function storageGet(keys) {
        if (hasChromeStorage) {
            return new Promise((resolve) => {
                chrome.storage.local.get(keys, (res) => resolve(res || {}));
            });
        }
        const out = {};
        const list = Array.isArray(keys) ? keys : [keys];
        for (const k of list) {
            try {
                const v = global.localStorage.getItem(k);
                if (v != null) out[k] = JSON.parse(v);
            } catch (e) { /* ignore */ }
        }
        return Promise.resolve(out);
    }

    function storageSet(obj) {
        if (hasChromeStorage) {
            return new Promise((resolve) => {
                chrome.storage.local.set(obj, () => resolve());
            });
        }
        try {
            for (const k of Object.keys(obj)) {
                global.localStorage.setItem(k, JSON.stringify(obj[k]));
            }
        } catch (e) { /* ignore */ }
        return Promise.resolve();
    }

    // ---- Module state ------------------------------------------------------
    let cached = getDefaults();
    let initialized = false;
    const listeners = new Set();

    function notify() {
        const snap = get();
        listeners.forEach((cb) => {
            try { cb(snap); } catch (e) { console.error('settings listener error', e); }
        });
    }

    async function init() {
        const res = await storageGet([STORAGE_KEY, LEGACY_KEY]);
        if (res[STORAGE_KEY]) {
            cached = validate(res[STORAGE_KEY]);
        } else if (res[LEGACY_KEY]) {
            const migrated = migrateLegacy(res[LEGACY_KEY]);
            cached = migrated || getDefaults();
            await storageSet({ [STORAGE_KEY]: cached });   // persist the upgrade
        } else {
            cached = getDefaults();
        }
        initialized = true;

        // React to writes from other surfaces (popup edits visible in open tabs).
        if (hasChromeStorage && chrome.storage.onChanged) {
            chrome.storage.onChanged.addListener((changes, area) => {
                if (area === 'local' && changes[STORAGE_KEY]) {
                    cached = validate(changes[STORAGE_KEY].newValue);
                    notify();
                }
            });
        } else if (!hasChromeStorage && global.addEventListener) {
            global.addEventListener('storage', (e) => {
                if (e.key === STORAGE_KEY && e.newValue) {
                    cached = validate(JSON.parse(e.newValue));
                    notify();
                }
            });
        }
        return get();
    }

    function get() {
        return clone(cached);
    }

    function isReady() {
        return initialized;
    }

    async function save(next) {
        cached = validate(next);
        await storageSet({ [STORAGE_KEY]: cached });
        notify();
        return get();
    }

    /** Deep-merge a partial patch onto current settings and persist. */
    async function update(patch) {
        const merged = mergeOnto(cached, patch);   // mergeOnto keeps the full shape
        return save(merged);
    }

    /** Set a single dotted-path value, e.g. set('umrSettings.visualMode', 'X_MARKS'). */
    async function set(path, value) {
        const parts = path.split('.');
        const next = clone(cached);
        let node = next;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!isPlainObject(node[parts[i]])) node[parts[i]] = {};
            node = node[parts[i]];
        }
        node[parts[parts.length - 1]] = value;
        return save(next);
    }

    function onChange(cb) {
        listeners.add(cb);
        return () => listeners.delete(cb);
    }

    // ---- Goals / Events CRUD (parallel stores; never bleed together) -------
    function uuid() {
        if (global.crypto && global.crypto.randomUUID) return global.crypto.randomUUID();
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = (Math.random() * 16) | 0;
            const v = c === 'x' ? r : (r & 0x3) | 0x8;
            return v.toString(16);
        });
    }

    const GOAL_DEFAULT_COLOR = 0xFFE53935;   // Material Red 600 (app default)

    async function addGoal({ title, targetDate, color }) {
        const goals = cached.goalSettings.goals.slice();
        goals.push({ id: uuid(), title: title || '', targetDate: targetDate || 0, color: color || GOAL_DEFAULT_COLOR });
        return set('goalSettings.goals', goals);
    }
    async function updateGoal(goal) {
        const goals = cached.goalSettings.goals.slice();
        const i = goals.findIndex((g) => g.id === goal.id);
        if (i >= 0) { goals[i] = { ...goals[i], ...goal }; return set('goalSettings.goals', goals); }
        return get();
    }
    async function deleteGoal(id) {
        return set('goalSettings.goals', cached.goalSettings.goals.filter((g) => g.id !== id));
    }

    async function addEvent({ title, targetDate, color }) {
        const events = cached.eventSettings.events.slice();
        events.push({ id: uuid(), title: title || '', targetDate: targetDate || 0, color: color || GOAL_DEFAULT_COLOR });
        return set('eventSettings.events', events);
    }
    async function updateEvent(event) {
        const events = cached.eventSettings.events.slice();
        const i = events.findIndex((e) => e.id === event.id);
        if (i >= 0) { events[i] = { ...events[i], ...event }; return set('eventSettings.events', events); }
        return get();
    }
    async function deleteEvent(id) {
        return set('eventSettings.events', cached.eventSettings.events.filter((e) => e.id !== id));
    }

    // ---- Auto-switch helpers (mirror LifeDotsPreferences) ------------------
    /** Snapshot reference time + start side when enabling, like setAutoSwitchEnabled. */
    async function setAutoSwitchEnabled(enabled) {
        const a = cached.autoSwitchSettings;
        const next = enabled
            ? { ...a, enabled: true, referenceMs: Date.now(), startMode: cached.topViewMode }
            : { ...a, enabled: false };
        return set('autoSwitchSettings', next);
    }

    /**
     * Resolve which view is effective right now. Pure port of
     * currentEffectiveMode(now, settings) from the Kotlin source.
     */
    function currentEffectiveMode(now, s) {
        s = s || cached;
        const auto = s.autoSwitchSettings;
        if (!auto.enabled) return s.topViewMode;
        if (!s.umrSettings.birthdayEpochMs) return 'YIL';     // Umr needs a birthday
        const elapsed = now - auto.referenceMs;
        const ticks = auto.intervalMs > 0 ? Math.floor(elapsed / auto.intervalMs) : 0;
        const normalized = ticks < 0 ? 0 : ticks;             // clock-skew safety
        const startIsYil = auto.startMode === 'YIL';
        const onStartSide = (normalized % 2) === 0;
        if (onStartSide) return startIsYil ? 'YIL' : 'UMR';
        return startIsYil ? 'UMR' : 'YIL';
    }

    // ---- Color helpers (shared by all renderers) ---------------------------
    const color = {
        css(colorInt, alpha) {
            const a = alpha === undefined ? 1.0 : alpha;
            const r = (colorInt >> 16) & 0xFF;
            const g = (colorInt >> 8) & 0xFF;
            const b = colorInt & 0xFF;
            return a >= 1.0 ? `rgb(${r},${g},${b})` : `rgba(${r},${g},${b},${a})`;
        },
        /** Use the color's own alpha byte (ARGB) when composing, e.g. glass tint. */
        cssArgb(colorInt) {
            const a = ((colorInt >>> 24) & 0xFF) / 255;
            const r = (colorInt >> 16) & 0xFF;
            const g = (colorInt >> 8) & 0xFF;
            const b = colorInt & 0xFF;
            return `rgba(${r},${g},${b},${a})`;
        },
        toHex(colorInt) {
            const r = (colorInt >> 16) & 0xFF;
            const g = (colorInt >> 8) & 0xFF;
            const b = colorInt & 0xFF;
            return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
        },
        fromHex(hex) {
            // Returns an unsigned 0xFFRRGGBB int.
            return (parseInt(hex.replace('#', ''), 16) | 0xFF000000) >>> 0;
        },
    };

    // ---- Export ------------------------------------------------------------
    const api = {
        STORAGE_KEY,
        SCHEMA_VERSION,
        GOAL_DEFAULT_COLOR,
        getDefaults,
        validate,
        init,
        get,
        isReady,
        save,
        update,
        set,
        onChange,
        addGoal, updateGoal, deleteGoal,
        addEvent, updateEvent, deleteEvent,
        setAutoSwitchEnabled,
        currentEffectiveMode,
        color,
        uuid,
    };

    global.OlyapmizSettings = api;
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : this);
