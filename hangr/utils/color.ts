// ---------------------------------------------------------------------------
// HSL conversion + color matching
// ---------------------------------------------------------------------------

type Hsl = { h: number; s: number; l: number };

/** Convert a 6-char hex string (with or without #) to HSL. Returns null on bad input. */
export function hexToHsl(hex: string): Hsl | null {
  const c = hex.startsWith('#') ? hex.slice(1) : hex;
  if (c.length !== 6 || !/^[0-9a-fA-F]{6}$/.test(c)) return null;

  const r = parseInt(c.substring(0, 2), 16) / 255;
  const g = parseInt(c.substring(2, 4), 16) / 255;
  const b = parseInt(c.substring(4, 6), 16) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const delta = max - min;
  const l = (max + min) / 2;

  if (delta === 0) return { h: 0, s: 0, l: l * 100 };

  const s = delta / (1 - Math.abs(2 * l - 1));
  let h = 0;
  if (max === r) h = ((g - b) / delta + (g < b ? 6 : 0)) / 6;
  else if (max === g) h = ((b - r) / delta + 2) / 6;
  else h = ((r - g) / delta + 4) / 6;

  return { h: h * 360, s: s * 100, l: l * 100 };
}

/** Circular distance between two hue angles (0–360). */
function hueDelta(a: number, b: number): number {
  const d = Math.abs(a - b) % 360;
  return d > 180 ? 360 - d : d;
}

type NamedColor = { id: number; hex: string | null };

/**
 * Given a list of extracted hex strings from an image and a list of named DB
 * colors (with id + hex), return up to `maxResults` color IDs that best match
 * the extracted palette.
 *
 * Matching strategy:
 *  - Neutrals (saturation < 12%): match on lightness only against
 *    Black/White/Grey/Silver/Charcoal/Cream equivalents in the DB.
 *  - Chromatic: weighted HSL distance (hue × 2 + lightness × 1). Skip if best
 *    distance exceeds the threshold (avoids forcing a bad match).
 * Colors already in `existingIds` are excluded from results (don't re-suggest
 * what the user already picked).
 */
export function matchColorsFromHexes(
  extractedHexes: string[],
  dbColors: NamedColor[],
  existingIds: number[] = [],
  maxResults = 3,
  distanceThreshold = 30,
): number[] {
  const dbWithHsl = dbColors
    .filter((c) => c.hex !== null)
    .map((c) => ({ id: c.id, hsl: hexToHsl(c.hex!)! }))
    .filter((c) => c.hsl !== null);

  const suggested = new Set<number>();

  for (const hex of extractedHexes) {
    if (suggested.size >= maxResults) break;
    const extracted = hexToHsl(hex);
    if (!extracted) continue;

    let bestId: number | null = null;
    let bestDist = Infinity;

    for (const db of dbWithHsl) {
      if (existingIds.includes(db.id)) continue;
      if (suggested.has(db.id)) continue;

      let dist: number;
      if (extracted.s < 12) {
        // Neutral: match purely on lightness
        if (db.hsl.s >= 12) continue;
        dist = Math.abs(extracted.l - db.hsl.l);
      } else {
        // Chromatic: skip neutral DB entries
        if (db.hsl.s < 12) continue;
        dist = hueDelta(extracted.h, db.hsl.h) * 2 + Math.abs(extracted.l - db.hsl.l);
      }

      if (dist < bestDist) {
        bestDist = dist;
        bestId = db.id;
      }
    }

    if (bestId !== null && bestDist <= distanceThreshold) {
      suggested.add(bestId);
    }
  }

  return Array.from(suggested);
}

// ---------------------------------------------------------------------------
// Contrast text color
// ---------------------------------------------------------------------------

/**
 * Selects the text color (black or white) that provides higher contrast against the given background hex color.
 *
 * Accepts 3-char (#RGB), 6-char (#RRGGBB), and 8-char (#RRGGBBAA) forms; a leading `#` is permitted and any alpha channel is ignored.
 *
 * @param hex - Background color in hex notation
 * @returns `#000000` if black yields higher contrast, `#FFFFFF` otherwise. Invalid or unparsable input yields `#000000`.
 */
export function contrastingTextColor(hex: string): '#000000' | '#FFFFFF' {
  // Coerce to string to guard against null/undefined callers
  const input = String(hex ?? '');
  // Strip leading '#'
  let c = input.startsWith('#') ? input.slice(1) : input;

  // Strip alpha channel from 8-char form
  if (c.length === 8) c = c.slice(0, 6);

  // Expand 3-char shorthand to 6-char
  if (c.length === 3) {
    c = c[0] + c[0] + c[1] + c[1] + c[2] + c[2];
  }

  if (c.length !== 6 || !/^[0-9a-fA-F]{6}$/.test(c)) {
    // Invalid input — default to dark text on unknown background
    return '#000000';
  }

  const r8 = parseInt(c.substring(0, 2), 16) / 255;
  const g8 = parseInt(c.substring(2, 4), 16) / 255;
  const b8 = parseInt(c.substring(4, 6), 16) / 255;

  // sRGB gamma linearisation (IEC 61966-2-1)
  const linearise = (v: number) =>
    v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);

  const R = linearise(r8);
  const G = linearise(g8);
  const B = linearise(b8);

  // WCAG 2.1 relative luminance (ITU-R BT.709 coefficients)
  const L = 0.2126 * R + 0.7152 * G + 0.0722 * B;

  // WCAG threshold: black text on backgrounds with L > 0.179, white otherwise
  return L > 0.179 ? '#000000' : '#FFFFFF';
}