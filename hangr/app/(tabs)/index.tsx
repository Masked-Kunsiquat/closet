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
import { ClothingItemWithMeta } from '@/db/types';
import { useClothingItems } from '@/hooks/useClothingItems';

const CARD_GAP = Spacing[2];

export default function ClosetScreen() {
  const { items, loading, refresh } = useClothingItems();
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>Closet</Text>
        <Text style={styles.count}>
          {loading ? '' : `${items.length} item${items.length !== 1 ? 's' : ''}`}
        </Text>
      </View>

      {/* Grid / Empty state */}
      {!loading && items.length === 0 ? (
        <EmptyState />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => String(item.id)}
          numColumns={2}
          contentContainerStyle={styles.grid}
          columnWrapperStyle={styles.row}
          onRefresh={refresh}
          refreshing={loading}
          renderItem={({ item }) => (
            <ItemCard item={item} onPress={() => router.push(`/item/${item.id}`)} />
          )}
        />
      )}

      {/* FAB */}
      <TouchableOpacity
        style={[
          styles.fab,
          { backgroundColor: accent.primary, bottom: insets.bottom + Spacing[4] },
        ]}
        onPress={() => router.push('/item/add')}
        activeOpacity={0.85}
      >
        <Text style={styles.fabIcon}>+</Text>
      </TouchableOpacity>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Item card
// ---------------------------------------------------------------------------

function ItemCard({ item, onPress }: { item: ClothingItemWithMeta; onPress: () => void }) {
  return (
    <Pressable style={styles.card} onPress={onPress}>
      <View style={styles.imageContainer}>
        {item.image_path ? (
          <Image
            source={{ uri: item.image_path }}
            style={styles.image}
            contentFit="cover"
            transition={150}
          />
        ) : (
          <View style={styles.imagePlaceholder}>
            <Text style={styles.placeholderEmoji}>{categoryEmoji(item.category_name)}</Text>
          </View>
        )}

        {item.is_favorite === 1 && (
          <View style={styles.favBadge}>
            <Text style={styles.favIcon}>♥</Text>
          </View>
        )}

        {item.status !== 'Active' && (
          <View style={styles.statusBadge}>
            <Text style={styles.statusText}>{item.status}</Text>
          </View>
        )}
      </View>

      <View style={styles.cardLabel}>
        <Text style={styles.itemName} numberOfLines={1}>{item.name}</Text>
        {item.category_name && (
          <Text style={styles.itemCategory} numberOfLines={1}>{item.category_name}</Text>
        )}
      </View>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

function EmptyState() {
  const { accent } = useAccent();
  const router = useRouter();

  return (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyEmoji}>👕</Text>
      <Text style={styles.emptyTitle}>Your closet is empty</Text>
      <Text style={styles.emptySubtitle}>Add your first item to get started.</Text>
      <TouchableOpacity
        style={[styles.emptyButton, { backgroundColor: accent.primary }]}
        onPress={() => router.push('/item/add')}
        activeOpacity={0.85}
      >
        <Text style={styles.emptyButtonText}>Add Item</Text>
      </TouchableOpacity>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function categoryEmoji(name: string | null): string {
  switch (name) {
    case 'Tops':                  return '👕';
    case 'Bottoms':               return '👖';
    case 'Outerwear':             return '🧥';
    case 'Dresses & Jumpsuits':   return '👗';
    case 'Footwear':              return '👟';
    case 'Accessories':           return '⌚';
    case 'Bags':                  return '👜';
    case 'Activewear':            return '🏃';
    case 'Underwear & Intimates': return '🧦';
    case 'Swimwear':              return '🩱';
    default:                      return '🧺';
  }
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
    paddingBottom: Spacing[3],
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
  grid: {
    paddingHorizontal: Spacing[3],
    paddingBottom: Spacing[16],
  },
  row: {
    gap: CARD_GAP,
    marginBottom: CARD_GAP,
  },
  card: {
    flex: 1,
    backgroundColor: Palette.surface1,
    borderRadius: Radius.md,
    overflow: 'hidden',
  },
  imageContainer: {
    aspectRatio: 3 / 4,
    width: '100%',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  imagePlaceholder: {
    width: '100%',
    height: '100%',
    backgroundColor: Palette.surface2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  placeholderEmoji: {
    fontSize: 40,
  },
  favBadge: {
    position: 'absolute',
    top: Spacing[2],
    right: Spacing[2],
  },
  favIcon: {
    fontSize: 14,
    color: '#FB7185',
  },
  statusBadge: {
    position: 'absolute',
    bottom: Spacing[2],
    left: Spacing[2],
    backgroundColor: 'rgba(0,0,0,0.65)',
    paddingHorizontal: Spacing[2],
    paddingVertical: 2,
    borderRadius: Radius.sm,
  },
  statusText: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    fontWeight: FontWeight.medium,
  },
  cardLabel: {
    padding: Spacing[2],
  },
  itemName: {
    color: Palette.textPrimary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
  itemCategory: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    marginTop: 2,
  },
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
    color: '#000',
    fontSize: 28,
    lineHeight: 30,
  },
  emptyContainer: {
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
    color: '#000',
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
});
