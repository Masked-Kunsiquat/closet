import { View, type ViewProps } from 'react-native';

import { Palette } from '@/constants/tokens';

export type ThemedViewProps = ViewProps & {
  surface?: 'surface0' | 'surface1' | 'surface2' | 'surface3';
};

export function ThemedView({ style, surface = 'surface1', ...rest }: ThemedViewProps) {
  return <View style={[{ backgroundColor: Palette[surface] }, style]} {...rest} />;
}
