import { SymbolView, SymbolViewProps, SymbolWeight } from 'expo-symbols';
import { StyleProp, ViewStyle } from 'react-native';

/**
 * Render a system symbol as an icon using expo-symbols' SymbolView.
 *
 * @param name - The symbol identifier to render.
 * @param size - Icon width and height in pixels; applied to both dimensions.
 * @param color - Tint color applied to the symbol.
 * @param style - Additional view styles that will be merged with and may override the size.
 * @param weight - Visual weight of the symbol (e.g., 'regular', 'bold').
 * @returns A React element displaying the configured symbol icon.
 */
export function IconSymbol({
  name,
  size = 24,
  color,
  style,
  weight = 'regular',
}: {
  name: SymbolViewProps['name'];
  size?: number;
  color: string;
  style?: StyleProp<ViewStyle>;
  weight?: SymbolWeight;
}) {
  return (
    <SymbolView
      weight={weight}
      tintColor={color}
      resizeMode="scaleAspectFit"
      name={name}
      style={[
        {
          width: size,
          height: size,
        },
        style,
      ]}
    />
  );
}