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

import { FilterPanel } from '@/components/closet/FilterPanel';
import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { ClothingItemWithMeta } from '@/db/types';
import { useClothingItems } from '@/hooks/useClothingItems';
import { useClosetView } from '@/hooks/useClosetView';

const CARD_GAP = Spacing[2];

export default function ClosetScreen() {
  const { items, loading, refresh } = useClothingItems();
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const {
    viewMode,
    setViewMode,
    sortKey,
    setSortKey,
    filters,
    applyFilters,
    clearFilters,
    filterPanelOpen,
    setFilterPanelOpen,
    activeFilterCount,
    filteredAndSorted,
  } = useClosetView(items);

  const visibleItems = filteredAndSorted;

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>Closet</Text>
        <Text style={styles.count}>
          {loading
            ? ''
            : visibleItems.length === items.length
            ? `${items.length} item${items.length !== 1 ? 's' : ''}`
            : `${visibleItems.length} of ${items.length}`}
        </Text>
      </View>

      {/* Toolbar: filter + sort + view toggle */}
      <View style={styles.toolbar}>
        <TouchableOpacity
          style={[
            styles.filterButton,
            activeFilterCount > 0 && { borderColor: accent.primary },
          ]}
          onPress={() => setFilterPanelOpen(true)}
          activeOpacity={0.75}
        >
          <Text
            style={[
              styles.filterButtonText,
              activeFilterCount > 0 && { color: accent.primary },
            ]}
          >
            {activeFilterCount > 0 ? `Filters · ${activeFilterCount}` : 'Filter & Sort'}
          </Text>
        </TouchableOpacity>

        {activeFilterCount > 0 && (
          <TouchableOpacity onPress={clearFilters} hitSlop={8} style={styles.clearButton}>
            <Text style={styles.clearButtonText}>✕</Text>
          </TouchableOpacity>
        )}

        <View style={styles.spacer} />

        <View style={styles.viewToggle}>
          <TouchableOpacity
            style={[styles.toggleBtn, viewMode === 'grid' && styles.toggleBtnActive]}
            onPress={() => setViewMode('grid')}
            activeOpacity={0.75}
          >
            <Text style={styles.toggleIcon}>⊞</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.toggleBtn, viewMode === 'list' && styles.toggleBtnActive]}
            onPress={() => setViewMode('list')}
            activeOpacity={0.75}
          >
            <Text style={styles.toggleIcon}>☰</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Content */}
      {!loading && items.length === 0 ? (
        <EmptyCloset />
      ) : !loading && visibleItems.length === 0 ? (
        <EmptyFilter onClear={clearFilters} />
      ) : viewMode === 'grid' ? (
        <FlatList
          key="grid"
          data={visibleItems}
          keyExtractor={(item) => String(item.id)}
          numColumns={2}
          contentContainerStyle={styles.grid}
          columnWrapperStyle={styles.gridRow}
          onRefresh={refresh}
          refreshing={loading}
          renderItem={({ item }) => (
            <GridCard item={item} onPress={() => router.push(`/item/${item.id}`)} />
          )}
        />
      ) : (
        <FlatList
          key="list"
          data={visibleItems}
          keyExtractor={(item) => String(item.id)}
          contentContainerStyle={styles.list}
          onRefresh={refresh}
          refreshing={loading}
          renderItem={({ item }) => (
            <ListRow item={item} onPress={() => router.push(`/item/${item.id}`)} />
          )}
          ItemSeparatorComponent={() => <View style={styles.listSeparator} />}
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

      {/* Filter panel */}
      <FilterPanel
        visible={filterPanelOpen}
        onClose={() => setFilterPanelOpen(false)}
        currentFilters={filters}
        currentSort={sortKey}
        onApply={(newFilters, newSort) => {
          applyFilters(newFilters);
          setSortKey(newSort);
        }}
      />
    </View>
  );
}

// ---------------------------------------------------------------------------
// Grid card
// ---------------------------------------------------------------------------

function GridCard({ item, onPress }: { item: ClothingItemWithMeta; onPress: () => void }) {
  return (
    <Pressable style={styles.card} onPress={onPress}>
      <View style={styles.cardImageContainer}>
        {item.image_path ? (
          <Image
            source={{ uri: item.image_path }}
            style={styles.cardImage}
            contentFit="cover"
            transition={150}
          />
        ) : (
          <View style={styles.cardImagePlaceholder}>
            <Text style={styles.cardPlaceholderEmoji}>{categoryEmoji(item.category_name)}</Text>
          </View>
        )}

        {/* Subtle amber star for favorites */}
        {item.is_favorite === 1 && (
          <View style={styles.favStar}>
            <Text style={styles.favStarText}>★</Text>
          </View>
        )}

        {item.status !== 'Active' && (
          <View style={styles.statusBadge}>
            <Text style={styles.statusText}>{item.status}</Text>
          </View>
        )}
      </View>

      <View style={styles.cardLabel}>
        <Text style={styles.cardName} numberOfLines={1}>{item.name}</Text>
        {item.category_name && (
          <Text style={styles.cardCategory} numberOfLines={1}>{item.category_name}</Text>
        )}
      </View>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// List row
// ---------------------------------------------------------------------------

function ListRow({ item, onPress }: { item: ClothingItemWithMeta; onPress: () => void }) {
  return (
    <Pressable style={styles.listRow} onPress={onPress}>
      <View style={styles.listThumb}>
        {item.image_path ? (
          <Image
            source={{ uri: item.image_path }}
            style={styles.listThumbImage}
            contentFit="cover"
            transition={150}
          />
        ) : (
          <View style={styles.listThumbPlaceholder}>
            <Text style={styles.listThumbEmoji}>{categoryEmoji(item.category_name)}</Text>
          </View>
        )}
      </View>

      <View style={styles.listMeta}>
        <View style={styles.listNameRow}>
          <Text style={styles.listName} numberOfLines={1}>{item.name}</Text>
          {item.is_favorite === 1 && (
            <Text style={styles.listFavStar}>★</Text>
          )}
        </View>
        {item.brand ? (
          <Text style={styles.listSubline} numberOfLines={1}>{item.brand}</Text>
        ) : item.category_name ? (
          <Text style={styles.listSubline} numberOfLines={1}>{item.category_name}</Text>
        ) : null}
      </View>

      <View style={styles.listRight}>
        {item.wear_count > 0 && (
          <Text style={styles.listWearCount}>{item.wear_count}×</Text>
        )}
        {item.status !== 'Active' && (
          <View style={styles.listStatusPill}>
            <Text style={styles.listStatusText}>{item.status}</Text>
          </View>
        )}
      </View>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// Empty states
// ---------------------------------------------------------------------------

function EmptyCloset() {
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

function EmptyFilter({ onClear }: { onClear: () => void }) {
  return (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyEmoji}>🔍</Text>
      <Text style={styles.emptyTitle}>No items match</Text>
      <Text style={styles.emptySubtitle}>Try adjusting your filters.</Text>
      <TouchableOpacity style={styles.clearFiltersButton} onPress={onClear} activeOpacity={0.8}>
        <Text style={styles.clearFiltersText}>Clear Filters</Text>
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

  // Header
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

  // Toolbar
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[3],
    gap: Spacing[2],
  },
  filterButton: {
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface2,
  },
  filterButtonText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
  clearButton: {
    padding: Spacing[1],
  },
  clearButtonText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },
  spacer: {
    flex: 1,
  },
  viewToggle: {
    flexDirection: 'row',
    backgroundColor: Palette.surface2,
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.border,
    overflow: 'hidden',
  },
  toggleBtn: {
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
  },
  toggleBtnActive: {
    backgroundColor: Palette.surface3,
  },
  toggleIcon: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },

  // Grid
  grid: {
    paddingHorizontal: Spacing[3],
    paddingBottom: Spacing[16],
  },
  gridRow: {
    gap: CARD_GAP,
    marginBottom: CARD_GAP,
  },
  card: {
    flex: 1,
    backgroundColor: Palette.surface1,
    borderRadius: Radius.md,
    overflow: 'hidden',
  },
  cardImageContainer: {
    aspectRatio: 3 / 4,
    width: '100%',
  },
  cardImage: {
    width: '100%',
    height: '100%',
  },
  cardImagePlaceholder: {
    width: '100%',
    height: '100%',
    backgroundColor: Palette.surface2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardPlaceholderEmoji: {
    fontSize: 40,
  },
  favStar: {
    position: 'absolute',
    top: Spacing[2],
    right: Spacing[2],
  },
  favStarText: {
    fontSize: 13,
    color: '#F59E0B',
    opacity: 0.9,
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
  cardName: {
    color: Palette.textPrimary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
  cardCategory: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    marginTop: 2,
  },

  // List
  list: {
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[16],
  },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing[3],
    gap: Spacing[3],
  },
  listSeparator: {
    height: 1,
    backgroundColor: Palette.borderMuted,
  },
  listThumb: {
    width: 56,
    height: 72,
    borderRadius: Radius.sm,
    overflow: 'hidden',
    backgroundColor: Palette.surface2,
    flexShrink: 0,
  },
  listThumbImage: {
    width: '100%',
    height: '100%',
  },
  listThumbPlaceholder: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  listThumbEmoji: {
    fontSize: 24,
  },
  listMeta: {
    flex: 1,
    gap: 2,
  },
  listNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[1],
  },
  listName: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
    flex: 1,
  },
  listFavStar: {
    fontSize: 11,
    color: '#F59E0B',
    opacity: 0.9,
    flexShrink: 0,
  },
  listSubline: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },
  listRight: {
    alignItems: 'flex-end',
    gap: Spacing[1],
    flexShrink: 0,
  },
  listWearCount: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
  listStatusPill: {
    backgroundColor: Palette.surface3,
    paddingHorizontal: Spacing[2],
    paddingVertical: 2,
    borderRadius: Radius.full,
  },
  listStatusText: {
    color: Palette.textDisabled,
    fontSize: FontSize.xs,
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
    color: '#000',
    fontSize: 28,
    lineHeight: 30,
  },

  // Empty states
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
  clearFiltersButton: {
    paddingHorizontal: Spacing[5],
    paddingVertical: Spacing[3],
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  clearFiltersText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
});
