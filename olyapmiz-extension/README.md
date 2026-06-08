# O'lyapmiz — Chrome Extension

**Memento mori on every new tab.** A faithful browser port of the
[O'lyapmiz](../README.md) Android live wallpaper — the same **Yil** year
calendar and **Umr** life-in-weeks views, rendered onto Chrome's new-tab page.

> *O'lyapmiz* (Uzbek): "we are dying." A reminder that life is finite — and
> that this is what makes today worth showing up for.

The Android app is a live wallpaper; a Chrome extension can't set the OS
wallpaper, so the new-tab page is the analog "backdrop." Every render decision
(layout math, theme colors, dot styles, stats lines, parent rings, event
markers) is ported from the app's `LifeDotsWallpaperService` so a dot looks the
same here as on the phone.

---

## Features (parity with the app)

- **Yil view** — all 12 months of the current year, months arranged **3×4 or
  2×6**, each month a 7-column weekday-aligned mini-calendar (Monday-first).
  Past days filled, future days dimmed, **today glows**, and a bottom stats
  line shows `Xd left · X%` with one `Xd to <title>` line per upcoming goal.
- **Umr view** — the 52×80 life-in-weeks grid (4160 weeks) with the month-gap
  rhythm every 4 columns, a **Me / Mom / Dad** counter band, a "you are here"
  year-row glow, **parent rings**, event markers, and future-event
  "weeks remaining" lines. **Dots or X-marks**.
- **Auto-switch** — wall-clock rotation between Yil and Umr (1 second → 1 hour),
  using the app's exact `currentEffectiveMode` formula.
- **Themes** — Light / Dark / AMOLED / Custom (with custom bg/filled/empty/today
  colors), matching the app's palettes exactly.
- **Dots** — 4 shapes (circle / square / rounded / diamond) × 6 styles
  (flat / gradient / outlined / soft-glow / neon / embossed).
- **Goals (Yil) & Events (Umr)** — add / edit / delete, each with title, date,
  and color. Goals show as glowing dots + countdowns; events as ring markers +
  weeks-remaining lines. Kept as separate stores, like the app.
- **Per-view transparency, position, scale, and stats offset** — the active
  view's values are edited live.
- **Effects** — glass / frost overlay (light / heavy / acrylic / crystal / ice),
  live dot animations (fade / pulse / wave / breathe / ripple / cascade), and
  visual-theme presets (Classic / Minimalist / Cyberpunk / Glass / Cosmic).
- **Daily refresh** — re-renders on focus, tab visibility, window resize,
  storage changes (so popup edits reflect in open tabs), and at local midnight.
- **Import / export** — copy/paste full settings as JSON via the clipboard.
- **Privacy** — no telemetry, no network calls, no accounts. Settings live in
  `chrome.storage.local`.

### Not ported
The app's tree-growth and fluid/liquid simulation effects are full particle
systems and are intentionally **not** ported. The footer-text feature is
omitted (the new tab has no equivalent surface).

---

## Install (load unpacked)

1. Open `chrome://extensions`.
2. Toggle **Developer mode** (top-right).
3. Click **Load unpacked** and select the `olyapmiz-extension/` folder.
4. Open a new tab — you'll see the **Yil** calendar. Click the **⚙️** button
   (top-right) to open settings, or click the toolbar icon for the compact
   popup.

---

## Surfaces

| Surface | File | Role |
|---------|------|------|
| New tab | `newtab.html` / `newtab.js` | Full experience + all settings + goal/event editors |
| Popup   | `popup.html` / `popup.js`   | Compact live preview + quick controls (shares the same settings) |

Both read and write the **one** settings store, so changes in one are reflected
in the other immediately.

## Code map

| File | Responsibility |
|------|----------------|
| `settings.js` | `OlyapmizSettings` — schema mirroring the app's `WallpaperSettings`, `chrome.storage.local` persistence, validation/migration, goals/events CRUD, `currentEffectiveMode`, color helpers |
| `umr-math.js` | `UmrLayoutCompute` + `weekIndexFor` — pure port of the Umr layout/cell math |
| `dots.js` | `OlyapmizDots` — theme colors, `drawStyledDot` (6 styles × 4 shapes), tinted/glow dots, X-marks, glass overlay, animation alpha/scale |
| `yil-renderer.js` | `OlyapmizYil` — port of `drawCalendarView` + `CalendarLayout` |
| `umr-renderer.js` | `OlyapmizUmr` — port of `drawUmrView` |

## Settings map (extension → app)

The stored schema mirrors `WallpaperSettings`: `theme`, `customColors`,
`dotShape`, `dotEffectSettings`, `highlightToday`, `filledDotAlpha` /
`emptyDotAlpha`, `yilStatsBandOffset`, `calendarViewSettings` (columns / stats /
mondayFirst / currentWeekColor), `viewModeSettings`, `goalSettings`,
`eventSettings`, `positionSettings`, `topViewMode`, `autoSwitchSettings`,
`umrSettings` (birthdays / visual mode / alphas / totalWeeks / position /
statsBandOffset), `animationSettings`, `glassEffectSettings`, `visualTheme`.

---

## Smoke-test checklist

- [ ] New tab renders the Yil calendar by default (months in a 3×4 grid).
- [ ] Settings → set your birthday → switch to **Umr**; the grid fills to today.
- [ ] Add a **Goal** with a future date → a glowing dot appears in Yil plus an
      `Xd to <title>` line under the stats.
- [ ] Set Mom/Dad birthdays → ring markers + counter band update in Umr.
- [ ] Change theme to **Light/Custom** → background + dot colors update.
- [ ] Enable **Auto-switch** at 2s → the view rotates Yil ↔ Umr.
- [ ] Enable a **glass** style and an **animation** type in Effects.
- [ ] Reload the tab → all settings persist. Open the popup → it shows the same
      state and its preview matches.
