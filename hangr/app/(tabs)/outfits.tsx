/**
 * Outfits tab — saved outfits list + FAB to builder.
 */

import { useCallback, useState } from 'react';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import {
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { PhosphorIcon } from '@/components/PhosphorIcon';
import { OutfitRowSkeleton } from '@/components/ui/SkeletonLoader';
import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { OutfitWithMeta } from '@/db/types';
import { useOutfits } from '@/hooks/useOutfits';
import { contrastingTextColor } from '@/utils/color';
import { toImageUri } from '@/utils/image';

/**
 * Renders the Outfits screen with a header showing the outfit count, a refreshable list of outfits (or an empty state when none exist), and a floating action button to create a new outfit.
 *
 * @returns The React element representing the Outfits screen.
 */
export default function OutfitsScreen() {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const { outfits, loading, error, refresh } = useOutfits();
  const [isRefreshing, setIsRefreshing] = useState(false);

  const handleRefresh = useCallback(async () => {
    setIsRefreshing(true);
    try { await refresh(); } finally { setIsRefreshing(false); }
  }, [refresh]);

  if (error) {
    return (
      <View style={[styles.container, styles.errorContainer, { paddingTop: insets.top }]}>
        <Text style={styles.errorText}>Failed to load outfits.{'\n'}{error}</Text>
        <TouchableOpacity style={styles.errorButton} onPress={handleRefresh}>
          <Text style={styles.errorButtonText}>Retry</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>Outfits</Text>
        {!loading && (
          <Text style={styles.count}>
            {outfits.length} outfit{outfits.length !== 1 ? 's' : ''}
          </Text>
        )}
      </View>

      {loading && outfits.length === 0 ? (
        <View style={[styles.list, { paddingTop: Spacing[2] }]}>
          {Array.from({ length: 6 }).map((_, i) => (
            <View key={i}>
              <OutfitRowSkeleton />
              {i < 5 && <View style={styles.separator} />}
            </View>
          ))}
        </View>
      ) : !loading && outfits.length === 0 ? (
        <EmptyOutfits accent={accent.primary} />
      ) : (
        <FlatList
          data={outfits}
          keyExtractor={(o) => String(o.id)}
          contentContainerStyle={styles.list}
          onRefresh={handleRefresh}
          refreshing={isRefreshing}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          renderItem={({ item }) => (
            <OutfitRow
              outfit={item}
              onPress={() => router.push({ pathname: '/outfit/[id]', params: { id: item.id } })}
            />
          )}
        />
      )}

      {/* FAB */}
      <TouchableOpacity
        style={[styles.fab, { backgroundColor: accent.primary, bottom: insets.bottom + Spacing[4] }]}
        onPress={() => router.push('/outfit/new')}
        activeOpacity={0.85}
      >
        <Text style={[styles.fabIcon, { color: contrastingTextColor(accent.primary) }]}>+</Text>
      </TouchableOpacity>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Outfit row
/**
 * Renders a tappable list row displaying an outfit's thumbnail, name, and item count.
 *
 * @param outfit - Outfit data; expected to include `id`, `name`, `cover_image`, and `item_count` (used to show the thumbnail, title, and item count).
 * @param onPress - Callback invoked when the row is pressed.
 * @returns A React element representing the outfit row for use in a list.
 */

function OutfitRow({ outfit, onPress }: { outfit: OutfitWithMeta; onPress: () => void }) {
  return (
    <Pressable
      style={styles.row}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={outfit.name ?? undefined}
    >
      {/* Cover thumbnail */}
      <View style={styles.thumb}>
        {outfit.cover_image ? (
          <Image
            source={{ uri: toImageUri(outfit.cover_image)! }}
            style={styles.thumbImage}
            contentFit="cover"
            transition={150}
          />
        ) : (
          <View style={styles.thumbPlaceholder}>
            <Text style={styles.thumbEmoji}>👗</Text>
          </View>
        )}
      </View>

      <View style={styles.meta}>
        <Text style={styles.outfitName} numberOfLines={1}>
          {outfit.name ?? 'Untitled Outfit'}
        </Text>
        <Text style={styles.outfitSub}>
          {outfit.item_count} item{outfit.item_count !== 1 ? 's' : ''}
        </Text>
      </View>

      <PhosphorIcon name="caret-right" size={20} color={Palette.textDisabled} />
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// Empty state
/**
 * Renders the empty state shown when there are no outfits.
 *
 * Displays an emoji, title, subtitle, and a call-to-action button styled with the provided accent color.
 * Pressing the button navigates to the new-outfit screen.
 *
 * @param accent - Color used as the CTA button background
 * @returns The empty-state React element for the Outfits screen
 */

function EmptyOutfits({ accent }: { accent: string }) {
  const router = useRouter();
  return (
    <View style={styles.empty}>
      <Text style={styles.emptyEmoji}>👔</Text>
      <Text style={styles.emptyTitle}>No outfits yet</Text>
      <Text style={styles.emptySubtitle}>Build your first outfit from your closet.</Text>
      <TouchableOpacity
        style={[styles.emptyButton, { backgroundColor: accent }]}
        onPress={() => router.push('/outfit/new')}
        activeOpacity={0.85}
      >
        <Text style={[styles.emptyButtonText, { color: contrastingTextColor(accent) }]}>Build Outfit</Text>
      </TouchableOpacity>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.surface0,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingTop: Spacing[4],
    paddingBottom: Spacing[2],
  },
  title: {
    color: Palette.textPrimary,
    fontSize: FontSize['2xl'],
    fontWeight: FontWeight.bold,
  },
  count: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },


  // List
  list: {
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[16],
  },
  separator: {
    height: 1,
    backgroundColor: Palette.borderMuted,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing[3],
    gap: Spacing[3],
  },
  thumb: {
    width: 64,
    height: 80,
    borderRadius: Radius.sm,
    overflow: 'hidden',
    backgroundColor: Palette.surface2,
    flexShrink: 0,
  },
  thumbImage: {
    width: '100%',
    height: '100%',
  },
  thumbPlaceholder: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  thumbEmoji: {
    fontSize: 28,
  },
  meta: {
    flex: 1,
    gap: 2,
  },
  outfitName: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
  },
  outfitSub: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },

  // FAB
  fab: {
    position: 'absolute',
    right: Spacing[5],
    width: 56,
    height: 56,
    borderRadius: Radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.4,
    shadowRadius: 6,
  },
  fabIcon: {
    fontSize: 28,
    lineHeight: 30,
  },

  // Empty
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing[8],
  },
  emptyEmoji: {
    fontSize: 64,
    marginBottom: Spacing[4],
  },
  emptyTitle: {
    color: Palette.textPrimary,
    fontSize: FontSize.xl,
    fontWeight: FontWeight.semibold,
    marginBottom: Spacing[2],
    textAlign: 'center',
  },
  emptySubtitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'center',
    marginBottom: Spacing[6],
  },
  emptyButton: {
    paddingHorizontal: Spacing[6],
    paddingVertical: Spacing[3],
    borderRadius: Radius.md,
  },
  emptyButtonText: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
  errorContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing[4],
    padding: Spacing[6],
  },
  errorText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    textAlign: 'center',
  },
  errorButton: {
    paddingHorizontal: Spacing[5],
    paddingVertical: Spacing[2],
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  errorButtonText: {
    color: Palette.textPrimary,
    fontSize: FontSize.sm,
  },
});
