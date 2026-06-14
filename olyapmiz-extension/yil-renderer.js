/**
 * Yil renderer — faithful port of LifeDotsWallpaperService.drawCalendarView
 * + CalendarLayout.compute. Renders the 12-month calendar: months arranged in
 * a 2x6 or 3x4 grid, each month a 7-column weekday-aligned mini calendar
 * (Monday-first by default). Past days filled, future days empty, today glows,
 * goals render as tinted glowing dots. Bottom stats line "Xd left · X%" plus
 * one "Xd to <title>" countdown per upcoming goal, screen-anchored.
 *
 * Globals: OlyapmizSettings (colors), OlyapmizDots (dot drawing).
 */
(function (global) {
    'use strict';

    const SHORT_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                          'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const MONO = '-apple-system, "SF Mono", "Roboto Mono", Menlo, Consolas, monospace';

    function isLeap(y) { return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0; }
    function totalDaysInYear(y) { return isLeap(y) ? 366 : 365; }
    function dayOfYear(d) {
        const start = new Date(d.getFullYear(), 0, 0);
        return Math.floor((d - start) / 86400000); // 1-based (Jan 1 => 1)
    }

    /** Port of CalendarLayout.compute (device-class buckets keyed on aspect/width).
     *  Adds a landscape branch so wide laptop/desktop screens (aspect < 1) spread
     *  the grid across the available width instead of inheriting phone-portrait
     *  margins. Note: width/height are backing-store pixels (canvas is rendered
     *  at devicePixelRatio), so the cap scales with resolution for crisp dots. */
    function computeLayout(widthPx, heightPx) {
        const width = widthPx, height = heightPx;
        const aspect = height / width;

        let paddingXRatio, safeTopRatio, statsInsetRatio;
        if (aspect < 1.0) {
            // Landscape: tighten horizontal padding and top inset to use the width.
            paddingXRatio = aspect < 0.6 ? 0.06 : 0.09;
            safeTopRatio = 0.12;
            statsInsetRatio = 0.06;
        } else {
            paddingXRatio = aspect > 2.1 ? 0.12 : aspect > 2.0 ? 0.15 : 0.18;
            safeTopRatio = aspect > 2.1 ? 0.28 : aspect > 2.0 ? 0.25 : 0.22;
            statsInsetRatio = aspect > 2.1 ? 0.045 : aspect > 2.0 ? 0.048 : 0.055;
        }

        const paddingXPx = width * paddingXRatio;
        const safeTopPx = height * safeTopRatio;
        const statsBottomBaselinePx = height - height * statsInsetRatio;

        const dotGapRatio = width <= 720 ? 0.55 : width <= 900 ? 0.62 : 0.70;
        // Scale the cap with the smaller backing dimension so high-DPI / large
        // screens can grow dots (floor of 20 keeps small windows unchanged).
        const dotSizeCapPx = Math.max(20, Math.round(Math.min(width, height) * 0.025));
        const monthMarginRatio = Math.max(1.0, Math.min(2.0, widthPx / 600));

        return { paddingXPx, safeTopPx, statsBottomBaselinePx, dotGapRatio, monthMarginRatio, dotSizeCapPx };
    }

    function render(canvas, settings, nowDate) {
        const ctx = canvas.getContext('2d');
        const Dots = global.OlyapmizDots;
        const C = global.OlyapmizSettings.color;
        const now = nowDate || new Date();

        const width = canvas.width, height = canvas.height;
        const colors = Dots.getThemeColors(settings);

        // Background
        ctx.fillStyle = C.css(colors.background);
        ctx.fillRect(0, 0, width, height);
        Dots.drawGlassOverlay(ctx, width, height, settings.glassEffectSettings);

        const year = now.getFullYear();
        const doy = dayOfYear(now);
        const totalDays = totalDaysInYear(year);

        // Goals for the current year, mapped by day-of-year.
        const goalByDoy = {};
        if (settings.goalSettings.enabled) {
            for (const g of settings.goalSettings.goals) {
                const gd = new Date(g.targetDate);
                if (gd.getFullYear() === year) goalByDoy[dayOfYear(gd)] = g;
            }
        }
        const upcomingGoalCount = Object.keys(goalByDoy).filter((d) => Number(d) > doy).length;

        const columns = Math.max(2, Math.min(6, settings.calendarViewSettings.columnsPerRow));
        const rows = Math.ceil(12 / columns);

        const layout = computeLayout(width, height);
        const paddingX = layout.paddingXPx;
        const availableWidth = width - 2 * paddingX;
        const safeTop = layout.safeTopPx;
        const yilStatsShift = height * (settings.yilStatsBandOffset / 100);
        const statsBottomBaseline = layout.statsBottomBaselinePx + yilStatsShift;
        const dotGapRatio = layout.dotGapRatio;
        const monthMarginRatio = layout.monthMarginRatio;
        const showStats = settings.calendarViewSettings.showYearStats;

        // Solve for the largest dot size that fits width and height budgets.
        const dotsPerCol = 7 + 6 * dotGapRatio;
        const maxDotSizeH = availableWidth / (columns * dotsPerCol + (columns - 1) * monthMarginRatio);
        const statsExtraDotUnits = showStats ? (4.8 + 1.6 + upcomingGoalCount * (0.8 + 1.6)) : 0;
        const monthBlockDotUnits = 2.6 + 6 + 5 * dotGapRatio;
        const gridUnits = monthBlockDotUnits * rows + 1.6 * (rows - 1);
        const totalDotUnitsForFit = gridUnits + statsExtraDotUnits;
        const maxDotSizeV = (statsBottomBaseline - safeTop) / totalDotUnitsForFit;
        let dotSize = Math.min(maxDotSizeH, maxDotSizeV);
        dotSize = Math.max(2, Math.min(layout.dotSizeCapPx, dotSize));

        const dotGap = dotSize * dotGapRatio;
        const labelSize = dotSize * 1.6;
        const labelMarginBottom = dotSize * 1.0;
        const blockHeight = labelSize + labelMarginBottom + 6 * dotSize + 5 * dotGap;
        const rowGap = labelSize * 1.0;
        const totalGridHeight = rows * blockHeight + (rows - 1) * rowGap;

        const statsMargin = rowGap * 3;
        const statsFontSize = labelSize;
        const statsLineGap = labelSize * 0.5;

        const statsLine1BaselineY = upcomingGoalCount > 0
            ? statsBottomBaseline - upcomingGoalCount * (statsLineGap + statsFontSize)
            : statsBottomBaseline;

        const statsBlockTopY = (showStats ? statsLine1BaselineY : statsBottomBaseline) - statsFontSize;
        const gridBottomY = statsBlockTopY - statsMargin;
        const gridAreaHeight = gridBottomY - safeTop;
        const gridStartY = gridAreaHeight > totalGridHeight
            ? safeTop + labelSize
            : safeTop + Math.max(0, (gridAreaHeight - totalGridHeight) / 2);

        const dotGridWidth = 7 * dotSize + 6 * dotGap;
        const monthMargin = dotSize * monthMarginRatio;
        const gridBlockWidth = columns * dotGridWidth + (columns - 1) * monthMargin;
        const gridLeftStart = paddingX + (availableWidth - gridBlockWidth) / 2;

        const mondayFirst = settings.calendarViewSettings.mondayFirst;
        // Month-label / stats text follows the theme so it stays legible on light
        // backgrounds. (viewModeSettings.monthLabelColor defaults to white, which
        // is invisible on the LIGHT theme and isn't exposed in the UI.)
        const monthLabelColor = colors.text;
        const showMonthLabels = settings.viewModeSettings.showMonthLabels;
        const currentWeekColor = settings.calendarViewSettings.currentWeekColor;

        // --- Grid (responds to position/scale; stats drawn after restore) ---
        ctx.save();
        const offsetX = width * (settings.positionSettings.horizontalOffset / 100);
        const offsetY = height * (settings.positionSettings.verticalOffset / 100);
        ctx.translate(offsetX, offsetY);
        ctx.translate(width / 2, height / 2);
        ctx.scale(settings.positionSettings.scale, settings.positionSettings.scale);
        ctx.translate(-width / 2, -height / 2);

        let globalDayCounter = 0;
        let dotIndex = 0;
        const totalDots = totalDays;

        for (let monthIndex = 0; monthIndex < 12; monthIndex++) {
            const firstOfMonth = new Date(year, monthIndex, 1);
            const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
            let firstDayOffset = firstOfMonth.getDay(); // 0=Sun..6=Sat
            if (mondayFirst) firstDayOffset = firstDayOffset === 0 ? 6 : firstDayOffset - 1;

            const gridCol = monthIndex % columns;
            const gridRow = Math.floor(monthIndex / columns);
            const cellLeft = gridLeftStart + gridCol * (dotGridWidth + monthMargin);
            const cellTop = gridStartY + gridRow * (blockHeight + rowGap);

            if (showMonthLabels) {
                ctx.save();
                ctx.globalAlpha = 180 / 255;
                ctx.fillStyle = C.css(monthLabelColor);
                ctx.font = `${labelSize}px ${MONO}`;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'alphabetic';
                ctx.fillText(SHORT_MONTHS[monthIndex], cellLeft, cellTop + labelSize);
                ctx.restore();
            }

            const dotsTop = cellTop + labelSize + labelMarginBottom;

            for (let i = 0; i < 42; i++) {
                const dayNum = i - firstDayOffset + 1;
                if (dayNum < 1 || dayNum > daysInMonth) continue;
                globalDayCounter++;

                const row = Math.floor(i / 7);
                const col = i % 7;
                const cx = cellLeft + col * (dotSize + dotGap) + dotSize / 2;
                const cy = dotsTop + row * (dotSize + dotGap) + dotSize / 2;
                const r = dotSize / 2;

                const goal = goalByDoy[globalDayCounter];
                const isToday = globalDayCounter === doy && settings.highlightToday;
                const extra = { dotIndex: dotIndex++, total: totalDots, timeMs: render._timeMs || 0 };

                if (goal) {
                    Dots.drawTintedDot(ctx, cx, cy, r, goal.color, true, settings.dotShape);
                } else if (isToday) {
                    Dots.drawTintedDot(ctx, cx, cy, r, currentWeekColor, true, settings.dotShape);
                } else if (globalDayCounter < doy) {
                    Dots.drawStyledDot(ctx, cx, cy, r, 'FILLED', settings, colors, extra);
                } else {
                    Dots.drawStyledDot(ctx, cx, cy, r, 'EMPTY', settings, colors, extra);
                }
            }
        }
        ctx.restore();

        // --- Stats line + goal countdowns (screen-anchored) ---
        if (!showStats) return;

        const daysLeft = totalDays - doy;
        const percent = Math.floor((doy / totalDays) * 100);
        const leftText = `${daysLeft}d left`;
        const sepText = '  ·  ';
        const pctText = `${percent}%`;

        ctx.font = `${statsFontSize}px ${MONO}`;
        ctx.textAlign = 'left';
        ctx.textBaseline = 'alphabetic';
        const leftW = ctx.measureText(leftText).width;
        const sepW = ctx.measureText(sepText).width;
        const pctW = ctx.measureText(pctText).width;
        const totalW = leftW + sepW + pctW;

        const baselineY = statsLine1BaselineY;
        let x = (width - totalW) / 2;

        ctx.fillStyle = C.css(colors.todayDot);
        ctx.fillText(leftText, x, baselineY);
        x += leftW;
        ctx.fillStyle = C.css(monthLabelColor, 130 / 255);
        ctx.fillText(sepText, x, baselineY);
        x += sepW;
        ctx.fillText(pctText, x, baselineY);

        const upcoming = Object.keys(goalByDoy)
            .map(Number)
            .filter((d) => d > doy)
            .sort((a, b) => a - b);
        if (upcoming.length) {
            const lineHeight = statsFontSize + statsLineGap;
            upcoming.forEach((day, index) => {
                const goal = goalByDoy[day];
                const diff = day - doy;
                const countText = `${diff}d to `;
                const labelText = goal.title;
                const countW = ctx.measureText(countText).width;
                const lineW = countW + ctx.measureText(labelText).width;
                const by = statsLine1BaselineY + (index + 1) * lineHeight;
                let x2 = (width - lineW) / 2;
                ctx.fillStyle = C.css(goal.color);
                ctx.fillText(countText, x2, by);
                x2 += countW;
                ctx.fillStyle = C.css(monthLabelColor, 130 / 255);
                ctx.fillText(labelText, x2, by);
            });
        }
    }

    render._timeMs = 0;

    global.OlyapmizYil = { render, computeLayout, dayOfYear, totalDaysInYear };
    if (typeof module !== 'undefined' && module.exports) module.exports = global.OlyapmizYil;
})(typeof window !== 'undefined' ? window : this);
