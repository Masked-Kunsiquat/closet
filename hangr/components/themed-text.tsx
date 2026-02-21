import { Text, type TextProps } from 'react-native';

import { FontSize, FontWeight, Palette } from '@/constants/tokens';

export type ThemedTextProps = TextProps & {
  type?: 'default' | 'title' | 'semibold' | 'subtitle' | 'caption';
};

/**
 * Renders a React Native Text element with a themed style variant merged with any provided style.
 *
 * @param type - The predefined text variant to apply: 'default', 'title', 'semibold', 'subtitle', or 'caption'
 * @returns A Text element styled according to the selected variant with any incoming props and styles applied
 */
export function ThemedText({ style, type = 'default', ...rest }: ThemedTextProps) {
  return <Text style={[TYPE_STYLES[type], style]} {...rest} />;
}

const TYPE_STYLES = {
  default:  { color: Palette.textPrimary,   fontSize: FontSize.md },
  semibold: { color: Palette.textPrimary,   fontSize: FontSize.md,    fontWeight: FontWeight.semibold },
  title:    { color: Palette.textPrimary,   fontSize: FontSize['2xl'], fontWeight: FontWeight.bold },
  subtitle: { color: Palette.textPrimary,   fontSize: FontSize.lg,    fontWeight: FontWeight.semibold },
  caption:  { color: Palette.textSecondary, fontSize: FontSize.sm },
};