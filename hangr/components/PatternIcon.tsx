import { Image, ImageStyle } from 'expo-image';
import { StyleProp } from 'react-native';

// ---------------------------------------------------------------------------
// Static map of every pattern SVG bundled in assets/patterns/.
// Key = pattern name lowercased, spaces/slashes replaced with hyphens
// (matches the filename without .svg).
// ---------------------------------------------------------------------------

const PATTERN_MAP: Record<string, ReturnType<typeof require>> = {
  'solid':        require('@/assets/patterns/solid.svg'),
  'striped':      require('@/assets/patterns/striped.svg'),
  'plaid-tartan': require('@/assets/patterns/plaid-tartan.svg'),
  'checkered':    require('@/assets/patterns/checkered.svg'),
  'floral':       require('@/assets/patterns/floral.svg'),
  'geometric':    require('@/assets/patterns/geometric.svg'),
  'animal-print': require('@/assets/patterns/animal-print.svg'),
  'abstract':     require('@/assets/patterns/abstract.svg'),
  'tie-dye':      require('@/assets/patterns/tie-dye.svg'),
  'camouflage':   require('@/assets/patterns/camouflage.svg'),
  'paisley':      require('@/assets/patterns/paisley.svg'),
  'polka-dot':    require('@/assets/patterns/polka-dot.svg'),
  'houndstooth':  require('@/assets/patterns/houndstooth.svg'),
  'graphic':      require('@/assets/patterns/graphic.svg'),
  'color-block':  require('@/assets/patterns/color-block.svg'),
  'ombre':        require('@/assets/patterns/ombre.svg'),
  'other':        require('@/assets/patterns/other.svg'),
};

/** Derive the map key from a pattern display name (e.g. "Plaid/Tartan" → "plaid-tartan"). */
export function patternKey(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/-+$/, '');
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

type Props = {
  /** Pattern display name as stored in the DB (e.g. "Plaid/Tartan"). */
  name: string;
  size?: number;
  color?: string;
  style?: StyleProp<ImageStyle>;
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function PatternIcon({ name, size = 24, color = '#FFFFFF', style }: Props) {
  const source = PATTERN_MAP[patternKey(name)];
  if (!source) return null;

  return (
    <Image
      source={source}
      style={[{ width: size, height: size, tintColor: color }, style]}
      contentFit="contain"
    />
  );
}
