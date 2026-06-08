/**
 * Umr renderer — faithful port of LifeDotsWallpaperService.drawUmrView.
 * 52x80 life-in-weeks grid with month-gap rhythm, a 3-column counter band
 * (Me/Mom/Dad weeks), a "you are here" year-row gradient, parent rings and
 * event markers (DOTS or X_MARKS), plus future-event "weeks remaining" lines
 * below the grid. Position/scale apply to the grid; the event lines are
 * screen-anchored.
 *
 * Globals: OlyapmizSettings (colors), UmrLayoutCompute / weekIndexFor.
 */
(function (global) {
    'use strict';

    const MOM_COLOR = 0xFFE53935;   // warm red
    const DAD_COLOR = 0xFF2D75A8;   // steel blue
    const SANS = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';

    function css(c, a) { return global.OlyapmizSettings.color.css(c, a); }

    function circle(ctx, cx, cy, r, fillStyle, alpha) {
        ctx.save();
        if (alpha !== undefined) ctx.globalAlpha = Math.max(0, Math.min(1, alpha));
        ctx.fillStyle = fillStyle;
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }

    function glowCircle(ctx, cx, cy, r, colorInt, alpha, blur) {
        ctx.save();
        ctx.globalAlpha = Math.max(0, Math.min(1, alpha));
        ctx.shadowColor = css(colorInt);
        ctx.shadowBlur = blur;
        ctx.fillStyle = css(colorInt);
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }

    function cross(ctx, cx, cy, s, colorInt, strokeWidth, alpha) {
        ctx.save();
        ctx.globalAlpha = Math.max(0, Math.min(1, alpha == null ? 1 : alpha));
        ctx.strokeStyle = css(colorInt);
        ctx.lineWidth = strokeWidth;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(cx - s, cy - s); ctx.lineTo(cx + s, cy + s);
        ctx.moveTo(cx - s, cy + s); ctx.lineTo(cx + s, cy - s);
        ctx.stroke();
        ctx.restore();
    }

    function render(canvas, settings, nowDate) {
        const ctx = canvas.getContext('2d');
        const Dots = global.OlyapmizDots;
        const L = global.UmrLayoutCompute;
        const now = (nowDate || new Date()).getTime();
        const u = settings.umrSettings;

        const width = canvas.width, height = canvas.height;
        const colors = Dots.getThemeColors(settings);
        const totalCells = L.TOTAL_CELLS;

        const birthdayMs = u.birthdayEpochMs;
        const weeksLived = (birthdayMs !== 0 && now >= birthdayMs)
            ? Math.min(Math.floor((now - birthdayMs) / L.WEEK_MS), totalCells - 1)
            : -1;

        const layout = L.compute(width, height);
        const r = layout.dotSizePx / 2;
        const isX = u.visualMode === 'X_MARKS';
        const livedAlpha = u.livedAlpha;
        const emptyAlpha = u.emptyAlpha;
        const emptyColor = isX ? 0xFFFFFFFF : colors.emptyDot;

        // Background
        ctx.fillStyle = css(colors.background);
        ctx.fillRect(0, 0, width, height);
        Dots.drawGlassOverlay(ctx, width, height, settings.glassEffectSettings);

        // Position transform (Umr has its own position; default vertical 7%).
        ctx.save();
        const offsetX = width * (u.position.horizontalOffset / 100);
        const offsetY = height * (u.position.verticalOffset / 100);
        ctx.translate(offsetX, offsetY);
        ctx.translate(width / 2, height / 2);
        ctx.scale(u.position.scale, u.position.scale);
        ctx.translate(-width / 2, -height / 2);

        // --- Counter band (Me / Mom / Dad), anchored to the grid ---
        {
            const statsShift = height * (u.statsBandOffset / 100);
            const bandTop = layout.counterBandTopPx + statsShift;
            const bandBottom = layout.gridTopPx + statsShift;
            const bandHeight = Math.max(bandBottom - bandTop, 1);
            const textSize = Math.min(bandHeight * 0.42, 20);
            const totalWeeks = u.totalWeeks;

            const youWeeks = weekIndexFor(u.birthdayEpochMs, now);
            const momWeeks = weekIndexFor(u.momBirthdayEpochMs, now);
            const dadWeeks = weekIndexFor(u.dadBirthdayEpochMs, now);

            // Center the three counters over the GRID (not the whole canvas), so
            // on wide screens they stay grouped above the grid instead of being
            // flung to the screen edges. Clamp the band so it never exceeds the
            // canvas (e.g. the tiny popup preview).
            const gridCenterX = layout.gridLeftPx + layout.gridWidthPx / 2;
            const bandWidth = Math.max(layout.gridWidthPx, Math.min(540, width * 0.92));
            const bandLeft = gridCenterX - bandWidth / 2;
            const colWidth = bandWidth / 3;
            const numberY = bandTop + bandHeight * 0.58;
            const labelY = bandTop + bandHeight * 0.95;
            const swatchRadius = textSize * 0.32;

            const cols = [
                { label: 'Me', weeks: youWeeks < 0 ? null : youWeeks, color: colors.filledDot },
                { label: 'Mom', weeks: momWeeks < 0 ? null : momWeeks, color: MOM_COLOR },
                { label: 'Dad', weeks: dadWeeks < 0 ? null : dadWeeks, color: DAD_COLOR },
            ];
            ctx.textBaseline = 'alphabetic';
            cols.forEach((c, idx) => {
                const colCx = bandLeft + colWidth * (idx + 0.5);
                const numberText = c.weeks == null ? `— / ${totalWeeks}` : `${c.weeks} / ${totalWeeks}`;
                ctx.font = `${textSize}px ${SANS}`;
                const numW = ctx.measureText(numberText).width;
                // Swatch sits immediately to the left of the number.
                const swatchX = colCx - numW / 2 - swatchRadius - textSize * 0.4;
                const swatchCy = numberY - textSize * 0.32;
                circle(ctx, swatchX, swatchCy, swatchRadius, css(c.color), (c.weeks == null ? 80 : 220) / 255);
                ctx.textAlign = 'center';
                ctx.fillStyle = css(colors.filledDot, (c.weeks == null ? 100 : 230) / 255);
                ctx.fillText(numberText, colCx, numberY);
                ctx.font = `${textSize * 0.5}px ${SANS}`;
                ctx.fillText(c.label, colCx, labelY);
            });
        }

        // --- "You are here" year-row gradient ---
        if (weeksLived >= 0 && weeksLived <= totalCells - 1) {
            const yourRow = Math.floor(weeksLived / L.COLS);
            const rowTop = layout.gridTopPx + yourRow * (layout.dotSizePx + layout.dotGapPx);
            const rowBottom = rowTop + layout.dotSizePx;
            const pad = layout.dotSizePx * 0.75;
            const grad = ctx.createLinearGradient(layout.gridLeftPx, 0, layout.gridLeftPx + layout.gridWidthPx, 0);
            grad.addColorStop(0, css(colors.filledDot, 0));
            grad.addColorStop(0.5, css(colors.filledDot, 64 / 255));
            grad.addColorStop(1, css(colors.filledDot, 0));
            ctx.fillStyle = grad;
            ctx.fillRect(layout.gridLeftPx, rowTop - pad, layout.gridWidthPx, (rowBottom + pad) - (rowTop - pad));
        }

        // --- Cells ---
        for (let i = 0; i < totalCells; i++) {
            const { cx, cy } = L.cellCenter(layout, i);
            if (weeksLived < 0 || i > weeksLived) {
                circle(ctx, cx, cy, r, css(emptyColor), emptyAlpha);
            } else {
                // past or current week
                if (isX) {
                    cross(ctx, cx, cy, r * 0.85, colors.filledDot, layout.dotSizePx * 0.18, livedAlpha);
                } else {
                    circle(ctx, cx, cy, r, css(colors.filledDot), livedAlpha);
                }
            }
        }

        // --- Parent markers (glow halo + fill / heavy X) ---
        const parentR = layout.dotSizePx / 2;
        const glowRadius = parentR * 2.3;
        const glowBlur = layout.dotSizePx * 1.6;
        const crossStroke = layout.dotSizePx * 0.32;

        function drawParent(cell, colorInt) {
            if (cell < 0) return;
            const { cx, cy } = L.cellCenter(layout, cell);
            glowCircle(ctx, cx, cy, glowRadius, colorInt, 150 / 255, glowBlur);
            if (isX) cross(ctx, cx, cy, parentR * 0.95, colorInt, crossStroke, 1);
            else circle(ctx, cx, cy, parentR, css(colorInt), 1);
        }
        drawParent(weekIndexFor(u.momBirthdayEpochMs, now), MOM_COLOR);
        drawParent(weekIndexFor(u.dadBirthdayEpochMs, now), DAD_COLOR);

        // --- Event markers on the grid ---
        if (settings.eventSettings.enabled && birthdayMs !== 0) {
            for (const event of settings.eventSettings.events) {
                const cell = weekIndexFor(birthdayMs, event.targetDate);
                if (cell < 0) continue;
                const { cx, cy } = L.cellCenter(layout, cell);
                glowCircle(ctx, cx, cy, glowRadius, event.color, 150 / 255, glowBlur);
                circle(ctx, cx, cy, parentR, css(event.color), 1);
                if (isX) cross(ctx, cx, cy, parentR * 0.55, 0xFFFFFFFF, layout.dotSizePx * 0.20, 235 / 255);
            }
        }

        ctx.restore();

        // --- Future-event "weeks remaining" lines (screen-anchored) ---
        if (settings.eventSettings.enabled && settings.eventSettings.events.length) {
            const futureEvents = settings.eventSettings.events.filter((e) => e.targetDate > now);
            if (futureEvents.length) {
                const lineHeight = layout.dotSizePx * 2.2;
                const gridBottom = layout.gridTopPx + layout.gridHeightPx;
                const startY = gridBottom + lineHeight * 0.6;
                const textSize = lineHeight * 0.62;
                const swatchRadius = textSize * 0.30;
                const leftPad = layout.gridLeftPx + textSize * 0.4;
                const swatchToText = textSize * 0.9;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'alphabetic';
                ctx.font = `${textSize}px ${SANS}`;
                futureEvents.forEach((event, idx) => {
                    const y = startY + idx * lineHeight;
                    if (y > height - 32) return;
                    const weeksLeft = Math.max(0, Math.floor((event.targetDate - now) / L.WEEK_MS));
                    circle(ctx, leftPad, y - textSize * 0.32, swatchRadius, css(event.color), 1);
                    ctx.fillStyle = css(0xFFEDE8DE, 230 / 255);
                    ctx.fillText(`${event.title} — ${weeksLeft} weeks`, leftPad + swatchToText, y);
                });
            }
        }
    }

    global.OlyapmizUmr = { render };
    if (typeof module !== 'undefined' && module.exports) module.exports = global.OlyapmizUmr;
})(typeof window !== 'undefined' ? window : this);
