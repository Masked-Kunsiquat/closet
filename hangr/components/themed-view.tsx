import { View, type ViewProps } from 'react-native';

import { Palette } from '@/constants/tokens';

export type ThemedViewProps = ViewProps & {
  surface?: 'surface0' | 'surface1' | 'surface2' | 'surface3';
};

/**
 * Renders a View whose background color is selected from the Palette based on the provided surface key.
 *
 * @param surface - One of 'surface0' | 'surface1' | 'surface2' | 'surface3'; determines which Palette color to use. Defaults to 'surface1'.
 * @returns The rendered View element with the themed background color and any passed props/styles applied.
 */
export function ThemedView({ style, surface = 'surface1', ...rest }: ThemedViewProps) {
  return <View style={[{ backgroundColor: Palette[surface] }, style]} {...rest} />;
}