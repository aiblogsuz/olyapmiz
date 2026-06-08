/**
 * Umr core math — faithful port of UmrCellMath.kt + UmrLayoutCompute.kt.
 *
 * The layout is PURE (no position offset/scale baked in) exactly like the app:
 * the renderer applies position as a canvas transform. cellCenter accounts for
 * the wider month-gap inserted every WEEKS_PER_GROUP columns.
 */

const UMR_ROWS = 80;
const UMR_COLS = 52;
const UMR_TOTAL_CELLS = UMR_ROWS * UMR_COLS;     // 4160
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;          // 604800000

/** Map a birthday + now to a cell index (0..4159), or -1 if unset/future. */
function weekIndexFor(birthdayMs, nowMs) {
    if (birthdayMs === 0) return -1;
    if (nowMs < birthdayMs) return -1;
    const weeks = Math.floor((nowMs - birthdayMs) / WEEK_MS);
    return Math.min(Math.max(weeks, 0), UMR_TOTAL_CELLS - 1);
}

function cellRowCol(cellIndex) {
    return { row: Math.floor(cellIndex / UMR_COLS), col: cellIndex % UMR_COLS };
}

const UmrLayoutCompute = {
    ROWS: UMR_ROWS,
    COLS: UMR_COLS,
    TOTAL_CELLS: UMR_TOTAL_CELLS,
    WEEK_MS: WEEK_MS,

    WEEKS_PER_GROUP: 4,
    MONTH_GAPS_PER_ROW: Math.floor((UMR_COLS - 1) / 4),   // 12
    MONTH_GAP_MULTIPLIER: 1.6,
    DOT_SIZE_CAP_PX: 12,

    _dotGapRatio(widthPx) { return widthPx <= 720 ? 0.55 : widthPx <= 900 ? 0.62 : 0.70; },
    _safeTopRatio(aspect) { return aspect > 2.1 ? 0.28 : aspect > 2.0 ? 0.25 : 0.22; },
    _paddingXRatio(aspect) { return aspect > 2.1 ? 0.06 : aspect > 2.0 ? 0.08 : 0.10; },
    _counterBandRatio(aspect) { return aspect > 2.1 ? 0.05 : aspect > 2.0 ? 0.055 : 0.06; },

    /** Pure layout — mirrors UmrLayoutCompute.compute (no offsets/scale). */
    compute(widthPx, heightPx) {
        const width = widthPx, height = heightPx;
        const aspect = width > 0 ? height / width : 2.0;

        const paddingXPx = width * this._paddingXRatio(aspect);
        const safeTopPx = height * this._safeTopRatio(aspect);
        const safeBottomPx = height * 0.06;

        const counterBandHeightPx = height * this._counterBandRatio(aspect);
        const gridTopPx = safeTopPx + counterBandHeightPx;

        const availWidth = Math.max(width - 2 * paddingXPx, 1);
        const availHeight = Math.max(height - gridTopPx - safeBottomPx, 1);

        const MG = this.MONTH_GAPS_PER_ROW;
        const MULT = this.MONTH_GAP_MULTIPLIER;
        const gapRatio = this._dotGapRatio(widthPx);
        const regularGapsPerRow = (UMR_COLS - 1) - MG;
        const totalGapUnitsPerRow = regularGapsPerRow + MG * MULT;

        const maxDotByWidth = availWidth / (UMR_COLS + totalGapUnitsPerRow * gapRatio);
        const maxDotByHeight = availHeight / (UMR_ROWS + (UMR_ROWS - 1) * gapRatio);
        const dotSizePx = Math.max(1, Math.min(maxDotByWidth, maxDotByHeight, this.DOT_SIZE_CAP_PX));
        const dotGapPx = dotSizePx * gapRatio;
        const monthGapPx = dotGapPx * MULT;

        const gridWidthPx = UMR_COLS * dotSizePx + regularGapsPerRow * dotGapPx + MG * monthGapPx;
        const gridHeightPx = UMR_ROWS * dotSizePx + (UMR_ROWS - 1) * dotGapPx;
        const gridLeftPx = paddingXPx + (availWidth - gridWidthPx) / 2;
        const gridBottomPx = gridTopPx + gridHeightPx;

        return {
            paddingXPx, safeTopPx,
            dotSizePx, dotGapPx, monthGapPx,
            gridWidthPx, gridHeightPx, gridLeftPx, gridTopPx, gridBottomPx,
            counterBandHeightPx, counterBandTopPx: safeTopPx,
            dotGapRatio: gapRatio,
        };
    },

    /** Pixel centre for a cell, accounting for the per-group month gap. */
    cellCenter(layout, cellIndex) {
        const row = Math.floor(cellIndex / UMR_COLS);
        const col = cellIndex % UMR_COLS;
        const groupIndex = Math.floor(col / this.WEEKS_PER_GROUP);
        const step = layout.dotSizePx + layout.dotGapPx;
        const monthOffset = groupIndex * (layout.monthGapPx - layout.dotGapPx);
        const r = layout.dotSizePx / 2;
        const cx = layout.gridLeftPx + col * step + monthOffset + r;
        const cy = layout.gridTopPx + row * step + r;
        return { cx, cy };
    },

    getYearRow(weeksLived) { return Math.floor(weeksLived / UMR_COLS); },
    getCellForDate(birthdayMs, targetDateMs) { return weekIndexFor(birthdayMs, targetDateMs); },
};

function cellCenter(layout, cellIndex) { return UmrLayoutCompute.cellCenter(layout, cellIndex); }

if (typeof window !== 'undefined') {
    window.UMR_ROWS = UMR_ROWS; window.UMR_COLS = UMR_COLS;
    window.UMR_TOTAL_CELLS = UMR_TOTAL_CELLS; window.WEEK_MS = WEEK_MS;
    window.weekIndexFor = weekIndexFor; window.cellRowCol = cellRowCol;
    window.cellCenter = cellCenter; window.UmrLayoutCompute = UmrLayoutCompute;
}
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { UMR_ROWS, UMR_COLS, UMR_TOTAL_CELLS, WEEK_MS,
        weekIndexFor, cellRowCol, cellCenter, UmrLayoutCompute };
}
