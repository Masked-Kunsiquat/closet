/**
 * Selects the text color (black or white) that provides higher contrast against the given background hex color.
 *
 * Accepts 3-char (#RGB), 6-char (#RRGGBB), and 8-char (#RRGGBBAA) forms; a leading `#` is permitted and any alpha channel is ignored.
 *
 * @param hex - Background color in hex notation
 * @returns `#000000` if black yields higher contrast, `#FFFFFF` otherwise. Invalid or unparsable input yields `#000000`.
 */
export function contrastingTextColor(hex: string): '#000000' | '#FFFFFF' {
  // Strip leading '#'
  let c = hex.startsWith('#') ? hex.slice(1) : hex;

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