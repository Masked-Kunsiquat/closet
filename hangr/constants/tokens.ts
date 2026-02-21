import { Platform } from 'react-native';

// ---------------------------------------------------------------------------
// Accent colors
// Each accent is a named palette the user can pick from in Settings.
// The active accent is injected via AccentContext — never hardcode these.
// ---------------------------------------------------------------------------

export type AccentKey =
  | 'amber'
  | 'coral'
  | 'sage'
  | 'sky'
  | 'lavender'
  | 'rose';

export type AccentPalette = {
  /** Primary accent — used for active icons, CTAs, selection rings */
  primary: string;
  /** Slightly dimmed variant — used for pressed states, secondary labels */
  muted: string;
  /** Very low-opacity tint — used for selected row backgrounds */
  subtle: string;
};

export const AccentPalettes: Record<AccentKey, AccentPalette> = {
  amber: {
    primary: '#F59E0B',
    muted: '#B45309',
    subtle: '#F59E0B1A',
  },
  coral: {
    primary: '#F97316',
    muted: '#C2410C',
    subtle: '#F973161A',
  },
  sage: {
    primary: '#84CC16',
    muted: '#4D7C0F',
    subtle: '#84CC161A',
  },
  sky: {
    primary: '#38BDF8',
    muted: '#0369A1',
    subtle: '#38BDF81A',
  },
  lavender: {
    primary: '#A78BFA',
    muted: '#6D28D9',
    subtle: '#A78BFA1A',
  },
  rose: {
    primary: '#FB7185',
    muted: '#BE123C',
    subtle: '#FB71851A',
  },
};

export const DEFAULT_ACCENT: AccentKey = 'amber';

// ---------------------------------------------------------------------------
// Base palette — dark-first
// ---------------------------------------------------------------------------

export const Palette = {
  // Neutrals
  black: '#000000',
  white: '#FFFFFF',

  // Surface layers (dark mode)
  surface0: '#0A0A0A', // deepest background
  surface1: '#111111', // card / sheet base
  surface2: '#1A1A1A', // elevated card
  surface3: '#242424', // input / chip background

  // Borders / dividers
  border: '#2A2A2A',
  borderMuted: '#1E1E1E',

  // Text
  textPrimary: '#F5F5F5',
  textSecondary: '#A0A0A0',
  textDisabled: '#555555',

  // Semantic
  error: '#EF4444',
  warning: '#F59E0B',
  success: '#22C55E',
} as const;

// ---------------------------------------------------------------------------
// Spacing scale — 4pt base grid
// ---------------------------------------------------------------------------

export const Spacing = {
  0: 0,
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  8: 32,
  10: 40,
  12: 48,
  16: 64,
} as const;

// ---------------------------------------------------------------------------
// Border radius
// ---------------------------------------------------------------------------

export const Radius = {
  sm: 6,
  md: 10,
  lg: 16,
  xl: 24,
  full: 9999,
} as const;

// ---------------------------------------------------------------------------
// Typography
// Font families are platform-aware (system fonts only — no custom font load).
// ---------------------------------------------------------------------------

export const FontFamily = Platform.select({
  ios: {
    sans: 'system-ui',
    rounded: 'ui-rounded',
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
    rounded:
      "'SF Pro Rounded', 'Hiragino Maru Gothic ProN', Meiryo, 'MS PGothic', sans-serif",
    mono: "SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace",
  },
})!;

export const FontSize = {
  xs: 11,
  sm: 13,
  md: 15,
  lg: 17,
  xl: 20,
  '2xl': 24,
  '3xl': 30,
  '4xl': 36,
} as const;

export const FontWeight = {
  regular: '400' as const,
  medium: '500' as const,
  semibold: '600' as const,
  bold: '700' as const,
};

export const LineHeight = {
  tight: 1.2,
  normal: 1.45,
  relaxed: 1.65,
} as const;

// ---------------------------------------------------------------------------
// Shadows (dark-mode shadows are subtle — mostly elevation cues via borders)
// ---------------------------------------------------------------------------

export const Shadow = {
  sm: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.4,
    shadowRadius: 2,
    elevation: 2,
  },
  md: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.5,
    shadowRadius: 6,
    elevation: 5,
  },
  lg: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.6,
    shadowRadius: 16,
    elevation: 10,
  },
} as const;

// ---------------------------------------------------------------------------
// Animation durations (ms)
// ---------------------------------------------------------------------------

export const Duration = {
  fast: 150,
  normal: 250,
  slow: 400,
} as const;
