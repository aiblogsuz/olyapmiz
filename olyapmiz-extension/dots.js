/**
 * Shared dot rendering — ports drawStyledDot / drawTintedDot and the app's
 * exact theme colors (LifeDotsWallpaperService.kt). Used by both the Yil and
 * Umr renderers so a dot looks identical to the wallpaper.
 *
 * Colors are 0xFFRRGGBB ints. Depends on OlyapmizSettings.color for CSS.
 */
(function (global) {
    'use strict';

    const color = global.OlyapmizSettings ? global.OlyapmizSettings.color : null;

    function css(c, a) { return color ? color.css(c, a) : `rgb(${(c>>16)&255},${(c>>8)&255},${c&255})`; }

    // ---- Theme colors (exact match to getThemeColors in the app) -----------
    function getThemeColors(settings) {
        switch (settings.theme) {
            case 'LIGHT':
                return { background: 0xFFF5F5F5, filledDot: 0xFF2C2C2C, emptyDot: 0xFFD0D0D0, todayDot: 0xFF4A90D9,
                         text: 0xFF1A1A1A, textMuted: 0xFF757575 };
            case 'DARK':
                return { background: 0xFF1A1A1A, filledDot: 0xFFE0E0E0, emptyDot: 0xFF3A3A3A, todayDot: 0xFF5BA0E9,
                         text: 0xFFFFFFFF, textMuted: 0xFFAAAAAA };
            case 'CUSTOM':
                return { background: settings.customColors.backgroundColor,
                         filledDot: settings.customColors.filledDotColor,
                         emptyDot: settings.customColors.emptyDotColor,
                         todayDot: settings.customColors.todayDotColor,
                         text: 0xFFFFFFFF, textMuted: 0xFFAAAAAA };
            case 'AMOLED':
            default:
                return { background: 0xFF000000, filledDot: 0xFFFFFFFF, emptyDot: 0xFF2A2A2A, todayDot: 0xFF6AB0F9,
                         text: 0xFFFFFFFF, textMuted: 0xFF888888 };
        }
    }

    // ---- Color math (port lightenColor/darkenColor) ------------------------
    function lighten(c, f) {
        const r = Math.min(255, Math.round(((c >> 16) & 255) * (1 - f) + 255 * f));
        const g = Math.min(255, Math.round(((c >> 8) & 255) * (1 - f) + 255 * f));
        const b = Math.min(255, Math.round((c & 255) * (1 - f) + 255 * f));
        return (0xFF000000 | (r << 16) | (g << 8) | b) >>> 0;
    }
    function darken(c, f) {
        const r = Math.round(((c >> 16) & 255) * (1 - f));
        const g = Math.round(((c >> 8) & 255) * (1 - f));
        const b = Math.round((c & 255) * (1 - f));
        return (0xFF000000 | (r << 16) | (g << 8) | b) >>> 0;
    }

    // ---- Shape path (CIRCLE | SQUARE | ROUNDED_SQUARE | DIAMOND) ------------
    function pathDot(ctx, cx, cy, r, shape) {
        ctx.beginPath();
        switch (shape) {
            case 'SQUARE':
                ctx.rect(cx - r, cy - r, r * 2, r * 2);
                break;
            case 'ROUNDED_SQUARE': {
                const rad = r * 0.45;
                if (ctx.roundRect) ctx.roundRect(cx - r, cy - r, r * 2, r * 2, rad);
                else ctx.rect(cx - r, cy - r, r * 2, r * 2);
                break;
            }
            case 'DIAMOND':
                ctx.moveTo(cx, cy - r);
                ctx.lineTo(cx + r, cy);
                ctx.lineTo(cx, cy + r);
                ctx.lineTo(cx - r, cy);
                ctx.closePath();
                break;
            case 'CIRCLE':
            default:
                ctx.arc(cx, cy, r, 0, Math.PI * 2);
                break;
        }
    }

    function fillDot(ctx, cx, cy, r, shape, fillStyle, alpha) {
        ctx.save();
        if (alpha !== undefined) ctx.globalAlpha = Math.max(0, Math.min(1, alpha));
        ctx.fillStyle = fillStyle;
        pathDot(ctx, cx, cy, r, shape);
        ctx.fill();
        ctx.restore();
    }

    // ---- Animation alpha/scale (NONE returns 1/1; others driven by timeMs) --
    function animAlphaScale(index, total, anim, timeMs) {
        if (!anim || !anim.enabled || anim.type === 'NONE') return { alpha: 1, scale: 1 };
        const t = (timeMs || 0) / 1000 * (anim.speed || 1);
        const k = total > 0 ? index / total : 0;
        const intensity = anim.intensity == null ? 0.5 : anim.intensity;
        let alpha = 1, scale = 1;
        switch (anim.type) {
            case 'FADE_IN':  alpha = Math.min(1, Math.max(0, (t - k * 2))); break;
            case 'PULSE':    scale = 1 + Math.sin(t * 3) * 0.15 * intensity; break;
            case 'BREATHE':  { const b = (Math.sin(t * 1.5) + 1) / 2; alpha = 0.4 + 0.6 * b; scale = 1 + b * 0.1 * intensity; break; }
            case 'WAVE':     { const w = Math.sin(t * 2 + k * Math.PI * 4); alpha = 0.6 + 0.4 * (w + 1) / 2; break; }
            case 'RIPPLE':   { const phase = (t % 3) / 3; const d = Math.abs(k - phase); scale = 1 + Math.max(0, 0.3 - d) * intensity; break; }
            case 'CASCADE':  alpha = Math.min(1, Math.max(0, t - k * 3)); break;
        }
        return { alpha, scale };
    }

    // ---- Styled dot (port of drawStyledDot, all 6 styles) ------------------
    // type: 'TODAY' | 'FILLED' | 'EMPTY'
    function drawStyledDot(ctx, cx, cy, radius, type, settings, colors, ctxExtra) {
        const baseColor =
            type === 'TODAY' ? colors.todayDot :
            type === 'FILLED' ? colors.filledDot : colors.emptyDot;

        const ce = ctxExtra || {};
        const anim = animAlphaScale(ce.dotIndex || 0, ce.total || 0, settings.animationSettings, ce.timeMs || 0);

        const baseAlpha =
            type === 'TODAY' ? 1.0 :
            type === 'FILLED' ? settings.filledDotAlpha : settings.emptyDotAlpha;
        const alpha = Math.max(0, Math.min(1, baseAlpha * anim.alpha));
        const r = radius * anim.scale;
        const shape = settings.dotShape;
        const fx = settings.dotEffectSettings || { style: 'FLAT', glowRadius: 8, outlineWidth: 2 };

        switch (fx.style) {
            case 'GRADIENT': {
                const grad = ctx.createRadialGradient(
                    cx - r * 0.3, cy - r * 0.3, r * 0.1,
                    cx - r * 0.3, cy - r * 0.3, r * 1.5);
                grad.addColorStop(0, css(lighten(baseColor, 0.3)));
                grad.addColorStop(1, css(darken(baseColor, 0.3)));
                fillDot(ctx, cx, cy, r, shape, grad, alpha);
                break;
            }
            case 'OUTLINED': {
                ctx.save();
                ctx.globalAlpha = alpha;
                ctx.strokeStyle = css(baseColor);
                ctx.lineWidth = fx.outlineWidth;
                pathDot(ctx, cx, cy, Math.max(0.5, r - fx.outlineWidth / 2), shape);
                ctx.stroke();
                ctx.restore();
                break;
            }
            case 'SOFT_GLOW': {
                ctx.save();
                ctx.globalAlpha = alpha * 0.6;
                ctx.shadowColor = css(baseColor);
                ctx.shadowBlur = fx.glowRadius;
                fillDot(ctx, cx, cy, r, shape, css(baseColor), 1);
                ctx.restore();
                fillDot(ctx, cx, cy, r, shape, css(baseColor), alpha);
                break;
            }
            case 'NEON': {
                for (let i = 3; i >= 1; i--) {
                    ctx.save();
                    ctx.globalAlpha = alpha * 0.18 * i;
                    ctx.shadowColor = css(baseColor);
                    ctx.shadowBlur = fx.glowRadius * i;
                    fillDot(ctx, cx, cy, r, shape, css(baseColor), 1);
                    ctx.restore();
                }
                fillDot(ctx, cx, cy, r * 0.7, shape, css(lighten(baseColor, 0.5)), alpha);
                break;
            }
            case 'EMBOSSED': {
                fillDot(ctx, cx + 1.5, cy + 1.5, r, shape, css(darken(baseColor, 0.5)), alpha * 0.5);
                fillDot(ctx, cx, cy, r, shape, css(lighten(baseColor, 0.3)), alpha);
                fillDot(ctx, cx, cy, r * 0.9, shape, css(baseColor), alpha);
                break;
            }
            case 'FLAT':
            default:
                fillDot(ctx, cx, cy, r, shape, css(baseColor), alpha);
                break;
        }
    }

    // ---- Tinted dot with glow (today / goal markers) -----------------------
    function drawTintedDot(ctx, cx, cy, radius, colorInt, glow, shape) {
        if (glow) {
            ctx.save();
            ctx.shadowColor = css(colorInt);
            ctx.shadowBlur = radius * 1.8;
            fillDot(ctx, cx, cy, radius * 1.15, shape || 'CIRCLE', css(colorInt), 0.55);
            ctx.restore();
        }
        fillDot(ctx, cx, cy, radius, shape || 'CIRCLE', css(colorInt), 1);
    }

    // ---- X mark (Umr X_MARKS visual mode) ----------------------------------
    function drawXMark(ctx, cx, cy, radius, colorInt, alpha) {
        ctx.save();
        ctx.globalAlpha = Math.max(0, Math.min(1, alpha == null ? 1 : alpha));
        ctx.strokeStyle = css(colorInt);
        ctx.lineWidth = Math.max(1, radius * 0.45);
        ctx.lineCap = 'round';
        const d = radius * 0.85;
        ctx.beginPath();
        ctx.moveTo(cx - d, cy - d); ctx.lineTo(cx + d, cy + d);
        ctx.moveTo(cx + d, cy - d); ctx.lineTo(cx - d, cy + d);
        ctx.stroke();
        ctx.restore();
    }

    // ---- Glass / frost overlay (port of drawGlassBackground) ---------------
    // Drawn in screen space between the background fill and the grid. Canvas
    // can't post-process blur the layers beneath, so frost is approximated as
    // translucent tints + gradients + the same decorative strokes the app uses.
    function rgba(r, g, b, a) { return `rgba(${r},${g},${b},${Math.max(0, Math.min(1, a))})`; }

    function drawIceCrystal(ctx, x, y, size) {
        ctx.beginPath();
        for (let k = 0; k < 6; k++) {
            const ang = (Math.PI / 3) * k;
            ctx.moveTo(x, y);
            ctx.lineTo(x + Math.cos(ang) * size, y + Math.sin(ang) * size);
        }
        ctx.stroke();
    }

    function drawGlassOverlay(ctx, width, height, glass) {
        if (!glass || !glass.enabled || glass.style === 'NONE') return;
        const op = glass.opacity;
        const cx = width / 2, cy = height / 2;
        const tintR = (glass.tint >> 16) & 0xFF, tintG = (glass.tint >> 8) & 0xFF, tintB = glass.tint & 0xFF;

        switch (glass.style) {
            case 'LIGHT_FROST': {
                ctx.fillStyle = rgba(255, 255, 255, op);
                ctx.fillRect(0, 0, width, height);
                const g = ctx.createLinearGradient(0, 0, 0, height);
                g.addColorStop(0, rgba(255, 255, 255, 40 / 255));
                g.addColorStop(1, rgba(255, 255, 255, 10 / 255));
                ctx.fillStyle = g;
                ctx.fillRect(0, 0, width, height);
                break;
            }
            case 'HEAVY_FROST': {
                for (let i = 3; i >= 1; i--) {
                    ctx.fillStyle = rgba(255, 255, 255, (op * 80 / i) / 255);
                    ctx.fillRect(0, 0, width, height);
                }
                break;
            }
            case 'ACRYLIC': {
                ctx.fillStyle = rgba(tintR, tintG, tintB, op * 200 / 255);
                ctx.fillRect(0, 0, width, height);
                ctx.fillStyle = rgba(255, 255, 255, 15 / 255);
                for (let i = 0; i < 200; i++) {
                    const x = Math.random() * width, y = Math.random() * height;
                    ctx.beginPath(); ctx.arc(x, y, 1, 0, Math.PI * 2); ctx.fill();
                }
                break;
            }
            case 'CRYSTAL': {
                const g = ctx.createRadialGradient(cx, cy, 1, cx, cy, Math.max(width, height) / 2);
                g.addColorStop(0, rgba(255, 255, 255, op * 100 / 255));
                g.addColorStop(0.5, rgba(200, 220, 255, op * 50 / 255));
                g.addColorStop(1, rgba(180, 200, 255, op * 30 / 255));
                ctx.fillStyle = g;
                ctx.fillRect(0, 0, width, height);
                ctx.strokeStyle = rgba(255, 255, 255, 30 / 255);
                ctx.lineWidth = 2;
                for (let i = 0; i < 5; i++) {
                    const sx = width * (0.2 + i * 0.15);
                    ctx.beginPath(); ctx.moveTo(sx, 0); ctx.lineTo(sx - 50, height); ctx.stroke();
                }
                break;
            }
            case 'ICE': {
                ctx.fillStyle = rgba(200, 230, 255, op * 150 / 255);
                ctx.fillRect(0, 0, width, height);
                ctx.strokeStyle = rgba(255, 255, 255, 40 / 255);
                ctx.lineWidth = 1.5;
                // deterministic-ish positions so it doesn't shimmer every frame
                let seed = 42;
                const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
                for (let i = 0; i < 20; i++) {
                    drawIceCrystal(ctx, rnd() * width, rnd() * height, 30 + rnd() * 40);
                }
                break;
            }
        }
    }

    global.OlyapmizDots = {
        getThemeColors, lighten, darken, pathDot, fillDot,
        drawStyledDot, drawTintedDot, drawXMark, animAlphaScale, drawGlassOverlay,
    };
    if (typeof module !== 'undefined' && module.exports) module.exports = global.OlyapmizDots;
})(typeof window !== 'undefined' ? window : this);
