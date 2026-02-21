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

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { OutfitWithMeta } from '@/db/types';
import { useOutfits } from '@/hooks/useOutfits';
import { contrastingTextColor } from '@/utils/color';

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

      {!loading && outfits.length === 0 ? (
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
              onPress={() => router.push(`/outfit/${item.id}` as any)}
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
// ---------------------------------------------------------------------------

function OutfitRow({ outfit, onPress }: { outfit: OutfitWithMeta; onPress: () => void }) {
  return (
    <Pressable style={styles.row} onPress={onPress}>
      {/* Cover thumbnail */}
      <View style={styles.thumb}>
        {outfit.cover_image ? (
          <Image
            source={{ uri: outfit.cover_image }}
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

      <Text style={styles.chevron}>›</Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

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
  chevron: {
    color: Palette.textDisabled,
    fontSize: FontSize.xl,
    flexShrink: 0,
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
