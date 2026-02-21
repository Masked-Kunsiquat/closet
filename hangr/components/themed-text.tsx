import { Text, type TextProps } from 'react-native';

import { FontSize, FontWeight, Palette } from '@/constants/tokens';

export type ThemedTextProps = TextProps & {
  type?: 'default' | 'title' | 'semibold' | 'subtitle' | 'caption';
};

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
