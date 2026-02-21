import { Image, ImageStyle } from 'expo-image';
import { StyleProp } from 'react-native';

// ---------------------------------------------------------------------------
// Static map of every Phosphor SVG bundled in assets/icons/.
// Key = icon name as stored in the database (matches filename without .svg).
// ---------------------------------------------------------------------------

const ICON_MAP: Record<string, ReturnType<typeof require>> = {
  'caret-left':          require('@/assets/icons/caret-left.svg'),
  'caret-right':         require('@/assets/icons/caret-right.svg'),
  'barbell':             require('@/assets/icons/barbell.svg'),
  'belt':                require('@/assets/icons/belt.svg'),
  'bookmark-simple':     require('@/assets/icons/bookmark-simple.svg'),
  'briefcase':           require('@/assets/icons/briefcase.svg'),
  'calendar-dots':       require('@/assets/icons/calendar-dots.svg'),
  'chart-bar':           require('@/assets/icons/chart-bar.svg'),
  'cheers':              require('@/assets/icons/cheers.svg'),
  'coat-hanger':         require('@/assets/icons/coat-hanger.svg'),
  'coffee':              require('@/assets/icons/coffee.svg'),
  'couch':               require('@/assets/icons/couch.svg'),
  'crown-simple':        require('@/assets/icons/crown-simple.svg'),
  'dress':               require('@/assets/icons/dress.svg'),
  'flower':              require('@/assets/icons/flower.svg'),
  'goggles':             require('@/assets/icons/goggles.svg'),
  'handbag':             require('@/assets/icons/handbag.svg'),
  'heart':               require('@/assets/icons/heart.svg'),
  'hoodie':              require('@/assets/icons/hoodie.svg'),
  'island':              require('@/assets/icons/island.svg'),
  'leaf':                require('@/assets/icons/leaf.svg'),
  'mountains':           require('@/assets/icons/mountains.svg'),
  'pants':               require('@/assets/icons/pants.svg'),
  'person-simple-run':   require('@/assets/icons/person-simple-run.svg'),
  'person-simple-swim':  require('@/assets/icons/person-simple-swim.svg'),
  'sneaker':             require('@/assets/icons/sneaker.svg'),
  'snowflake':           require('@/assets/icons/snowflake.svg'),
  'sock':                require('@/assets/icons/sock.svg'),
  'dot':                 require('@/assets/icons/dot.svg'),
  'star':                require('@/assets/icons/star.svg'),
  'sun':                 require('@/assets/icons/sun.svg'),
  'thermometer':         require('@/assets/icons/thermometer.svg'),
  't-shirt':             require('@/assets/icons/t-shirt.svg'),
  'watch':               require('@/assets/icons/watch.svg'),
  'gear-six':            require('@/assets/icons/gear-six.svg'),
};

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

type Props = {
  name: string;
  size?: number;
  /** Tint color applied over the SVG. Defaults to white. */
  color?: string;
  style?: StyleProp<ImageStyle>;
};

// ---------------------------------------------------------------------------
// Component
//
// Uses expo-image for SVG rendering — it handles SVG natively on iOS/Android
// and via an img tag on web. No react-native-svg transformer needed.
// Falls back silently if the icon name isn't in the map.
/**
 * Render a Phosphor SVG icon from the internal icon map.
 *
 * Renders an Image configured to display the named icon; returns `null` when the provided
 * `name` is not found in the icon map.
 *
 * @param name - Key of the icon to render (must exist in the internal ICON_MAP)
 * @param size - Width and height in pixels for the icon (defaults to 24)
 * @param color - Tint color applied to the SVG (defaults to `#FFFFFF`)
 * @param style - Additional Image styles to merge with the default size and tint
 * @returns A React element that displays the requested icon, or `null` if the icon name is unknown
 */

export function PhosphorIcon({ name, size = 24, color = '#FFFFFF', style }: Props) {
  const source = ICON_MAP[name];
  if (!source) return null;

  return (
    <Image
      source={source}
      style={[{ width: size, height: size, tintColor: color }, style]}
      contentFit="contain"
    />
  );
}