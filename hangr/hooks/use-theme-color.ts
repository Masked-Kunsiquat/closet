/**
 * Legacy hook kept for scaffold components.
 * New code should read directly from tokens + AccentContext.
 */

import { Palette } from '@/constants/tokens';

/**
 * Selects a theme color override from provided props, falling back to the default text color.
 *
 * @param props - Object with optional `light` and `dark` color overrides
 * @param _colorName - Palette key (unused placeholder retained for compatibility)
 * @returns The `dark` override if provided, otherwise the `light` override, otherwise `Palette.textPrimary`
 */
export function useThemeColor(
  props: { light?: string; dark?: string },
  _colorName: keyof typeof Palette
) {
  // App is dark-only; always return the dark override if provided.
  return props.dark ?? props.light ?? Palette.textPrimary;
}