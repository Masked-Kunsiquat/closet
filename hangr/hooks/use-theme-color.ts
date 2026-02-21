/**
 * Legacy hook kept for scaffold components.
 * New code should read directly from tokens + AccentContext.
 */

import { Palette } from '@/constants/tokens';

export function useThemeColor(
  props: { light?: string; dark?: string },
  _colorName: keyof typeof Palette
) {
  // App is dark-only; always return the dark override if provided.
  return props.dark ?? props.light ?? Palette.textPrimary;
}
