/**
 * SkeletonLoader — animated shimmer placeholder for loading states.
 *
 * Uses a looping opacity animation to create a pulse effect.
 * No third-party dependencies.
 */

import { useEffect, useRef } from 'react';
import { Animated, StyleSheet, View, ViewStyle } from 'react-native';

import { Duration, Palette, Radius, Spacing } from '@/constants/tokens';

// ---------------------------------------------------------------------------
// Base shimmer block
// ---------------------------------------------------------------------------

type ShimmerProps = {
  width?: number | string;
  height?: number;
  radius?: number;
  style?: ViewStyle;
};

function Shimmer({ width = '100%', height = 16, radius = Radius.sm, style }: ShimmerProps) {
  const opacity = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 0.35, duration: Duration.slow, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 1, duration: Duration.slow, useNativeDriver: true }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [opacity]);

  return (
    <Animated.View
      style={[
        {
          width: width as number,
          height,
          borderRadius: radius,
          backgroundColor: Palette.surface3,
          opacity,
        },
        style,
      ]}
    />
  );
}

// ---------------------------------------------------------------------------
// Grid card skeleton (mirrors GridCard layout)
// ---------------------------------------------------------------------------

export function GridCardSkeleton() {
  return (
    <View style={styles.card}>
      <Shimmer width="100%" height={0} style={styles.cardImageSkeleton} radius={0} />
      <View style={styles.cardLabel}>
        <Shimmer width="75%" height={12} />
        <Shimmer width="50%" height={10} style={{ marginTop: 6 }} />
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// List row skeleton (mirrors ListRow layout)
// ---------------------------------------------------------------------------

export function ListRowSkeleton() {
  return (
    <View style={styles.listRow}>
      <Shimmer width={56} height={72} radius={Radius.sm} />
      <View style={styles.listMeta}>
        <Shimmer width="60%" height={14} />
        <Shimmer width="40%" height={11} style={{ marginTop: 6 }} />
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Outfit row skeleton (mirrors OutfitRow layout in outfits.tsx)
// ---------------------------------------------------------------------------

export function OutfitRowSkeleton() {
  return (
    <View style={styles.outfitRow}>
      <Shimmer width={64} height={80} radius={Radius.sm} />
      <View style={styles.outfitMeta}>
        <Shimmer width="55%" height={14} />
        <Shimmer width="30%" height={11} style={{ marginTop: 6 }} />
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Grid skeleton grid (used in Closet screen)
// ---------------------------------------------------------------------------

export function ClosetGridSkeleton() {
  return (
    <View style={styles.grid}>
      {Array.from({ length: 6 }).map((_, i) => (
        <View key={i} style={styles.gridCell}>
          <GridCardSkeleton />
        </View>
      ))}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  card: {
    flex: 1,
    backgroundColor: Palette.surface1,
    borderRadius: Radius.md,
    overflow: 'hidden',
  },
  cardImageSkeleton: {
    aspectRatio: 3 / 4,
    width: '100%',
    height: undefined,
  },
  cardLabel: {
    padding: Spacing[2],
    gap: 4,
  },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing[3],
    gap: Spacing[3],
  },
  listMeta: {
    flex: 1,
    gap: 4,
  },
  outfitRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing[3],
    gap: Spacing[3],
  },
  outfitMeta: {
    flex: 1,
    gap: 4,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: Spacing[3],
    gap: Spacing[2],
  },
  gridCell: {
    width: '48%',
  },
});
