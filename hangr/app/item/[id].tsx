import type { ReactNode } from 'react';
import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { getDatabase } from '@/db';
import { deleteClothingItem } from '@/db/queries';
import { useClothingItem } from '@/hooks/useClothingItem';

export default function ItemDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const itemId = parseInt(id ?? '', 10);
  const { item, loading, error } = useClothingItem(itemId);
  const { accent } = useAccent();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const handleDelete = () => {
    Alert.alert(
      'Delete Item',
      `Remove "${item?.name}" from your closet? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              const db = await getDatabase();
              await deleteClothingItem(db, itemId);
              router.replace('/');
            } catch (e) {
              console.error('[deleteClothingItem]', e);
              Alert.alert('Error', 'Could not delete the item. Please try again.');
            }
          },
        },
      ]
    );
  };

  if (!Number.isFinite(itemId) || itemId <= 0) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Text style={styles.muted}>Item not found.</Text>
      </View>
    );
  }

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Text style={styles.muted}>Loading…</Text>
      </View>
    );
  }

  if (error || !item) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Text style={styles.muted}>
          {error ? `Failed to load item: ${error}` : 'Item not found.'}
        </Text>
      </View>
    );
  }

  const costPerWear =
    item.costPerWear != null ? `$${item.costPerWear.toFixed(2)}` : '—';

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12}>
          <Text style={styles.back}>‹ Back</Text>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => router.push(`/item/${itemId}/edit`)}
          hitSlop={12}
        >
          <Text style={[styles.editLink, { color: accent.primary }]}>Edit</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        {/* Hero image */}
        <View style={styles.heroContainer}>
          {item.image_path ? (
            <Image
              source={{ uri: item.image_path }}
              style={styles.hero}
              contentFit="cover"
            />
          ) : (
            <View style={[styles.hero, styles.heroPlaceholder]}>
              <Text style={styles.heroPlaceholderText}>No photo</Text>
            </View>
          )}

          {item.is_favorite === 1 && (
            <View style={styles.favBadge}>
              <Text style={styles.favIcon}>♥</Text>
            </View>
          )}
        </View>

        {/* Title block */}
        <View style={styles.titleBlock}>
          <Text style={styles.name}>{item.name}</Text>
          {item.brand && <Text style={styles.brand}>{item.brand}</Text>}
          {item.status && item.status !== 'Active' && (
            <View style={[styles.statusPill, { borderColor: Palette.border }]}>
              <Text style={styles.statusText}>{item.status}</Text>
            </View>
          )}
        </View>

        {/* Stats row */}
        <View style={styles.statsRow}>
          <StatBlock label="Times Worn" value={String(item.wearCount)} accent={accent.primary} />
          <View style={styles.statDivider} />
          <StatBlock label="Cost / Wear" value={costPerWear} accent={accent.primary} />
          {item.purchase_price != null && (
            <>
              <View style={styles.statDivider} />
              <StatBlock
                label="Paid"
                value={`$${item.purchase_price.toFixed(2)}`}
                accent={accent.primary}
              />
            </>
          )}
        </View>

        {/* Details */}
        <Section title="Details">
          <DetailRow label="Category" value={item.category_name ?? '—'} />
          <DetailRow label="Wash Status" value={item.wash_status} />
          {item.purchase_date && <DetailRow label="Purchased" value={item.purchase_date} />}
          {item.purchase_location && (
            <DetailRow label="From" value={item.purchase_location} />
          )}
          {(item.waist != null || item.inseam != null) && (
            <DetailRow
              label="Waist / Inseam"
              value={[item.waist, item.inseam]
                .map((v) => (v != null ? `${v}"` : '—'))
                .join(' / ')}
            />
          )}
        </Section>

        {item.notes ? (
          <Section title="Notes">
            <Text style={styles.notes}>{item.notes}</Text>
          </Section>
        ) : null}

        {/* Delete */}
        <TouchableOpacity style={styles.deleteButton} onPress={handleDelete} activeOpacity={0.8}>
          <Text style={styles.deleteText}>Delete Item</Text>
        </TouchableOpacity>

        <View style={{ height: insets.bottom + Spacing[4] }} />
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function StatBlock({ label, value, accent }: { label: string; value: string; accent: string }) {
  return (
    <View style={styles.statBlock}>
      <Text style={[styles.statValue, { color: accent }]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {children}
    </View>
  );
}

function DetailRow({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <View style={styles.detailRow}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
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
  centered: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  muted: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[3],
  },
  back: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  editLink: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
  },
  scroll: {
    paddingBottom: Spacing[8],
  },
  heroContainer: {
    width: '100%',
    aspectRatio: 3 / 4,
    maxHeight: 420,
    position: 'relative',
  },
  hero: {
    width: '100%',
    height: '100%',
  },
  heroPlaceholder: {
    backgroundColor: Palette.surface2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroPlaceholderText: {
    color: Palette.textDisabled,
    fontSize: FontSize.md,
  },
  favBadge: {
    position: 'absolute',
    top: Spacing[3],
    right: Spacing[3],
    backgroundColor: 'rgba(0,0,0,0.5)',
    borderRadius: Radius.full,
    padding: Spacing[2],
  },
  favIcon: {
    fontSize: 18,
    color: '#FB7185',
  },
  titleBlock: {
    paddingHorizontal: Spacing[4],
    paddingTop: Spacing[4],
    paddingBottom: Spacing[2],
    gap: Spacing[1],
  },
  name: {
    color: Palette.textPrimary,
    fontSize: FontSize['2xl'],
    fontWeight: FontWeight.bold,
  },
  brand: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  statusPill: {
    alignSelf: 'flex-start',
    borderWidth: 1,
    borderRadius: Radius.full,
    paddingHorizontal: Spacing[3],
    paddingVertical: 2,
    marginTop: Spacing[1],
  },
  statusText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },
  statsRow: {
    flexDirection: 'row',
    marginHorizontal: Spacing[4],
    marginVertical: Spacing[4],
    backgroundColor: Palette.surface1,
    borderRadius: Radius.lg,
    padding: Spacing[4],
  },
  statBlock: {
    flex: 1,
    alignItems: 'center',
    gap: Spacing[1],
  },
  statValue: {
    fontSize: FontSize.xl,
    fontWeight: FontWeight.bold,
  },
  statLabel: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    textAlign: 'center',
  },
  statDivider: {
    width: 1,
    backgroundColor: Palette.border,
    marginVertical: Spacing[1],
  },
  section: {
    marginHorizontal: Spacing[4],
    marginBottom: Spacing[5],
  },
  sectionTitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: Spacing[3],
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: Spacing[2],
    borderBottomWidth: 1,
    borderBottomColor: Palette.borderMuted,
  },
  detailLabel: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  detailValue: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
  },
  notes: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    lineHeight: FontSize.md * 1.6,
  },
  deleteButton: {
    marginHorizontal: Spacing[4],
    marginTop: Spacing[6],
    paddingVertical: Spacing[4],
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.error,
    alignItems: 'center',
  },
  deleteText: {
    color: Palette.error,
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
  },
});
