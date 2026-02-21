/**
 * Stats tab — wardrobe analytics dashboard.
 *
 * Headline metrics: % of closet worn, total value.
 * Worn item lists: most worn, least worn, never worn (top 15 each).
 * Breakdown charts: category, color, brand, material, occasion, season.
 * Time range filter: All Time / 1 Month / 3 Months / 1 Year (segmented control at top).
 *
 * Charts are native horizontal bar rows — no third-party library.
 */

import { Image } from 'expo-image';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { BreakdownRow, ColorBreakdownRow, StatItem } from '@/db/types';
import { useStats } from '@/hooks/useStats';

// ---------------------------------------------------------------------------
// Time range
// ---------------------------------------------------------------------------

type TimeRange = 'all' | 'month' | '3months' | 'year';

const RANGES: { label: string; value: TimeRange }[] = [
  { label: 'All Time', value: 'all' },
  { label: '1 Month', value: 'month' },
  { label: '3 Months', value: '3months' },
  { label: '1 Year', value: 'year' },
];

function toFromDate(range: TimeRange): string | null {
  if (range === 'all') return null;
  const now = new Date();
  const originalDay = now.getDate();

  if (range === 'year') {
    return new Date(now.getFullYear() - 1, now.getMonth(), originalDay).toISOString().slice(0, 10);
  }

  const monthsBack = range === 'month' ? 1 : 3;
  const targetYear = now.getMonth() < monthsBack
    ? now.getFullYear() - 1
    : now.getFullYear();
  const targetMonth = ((now.getMonth() - monthsBack) % 12 + 12) % 12;
  const lastDay = new Date(targetYear, targetMonth + 1, 0).getDate();
  const clampedDay = Math.min(originalDay, lastDay);
  return new Date(targetYear, targetMonth, clampedDay).toISOString().slice(0, 10);
}


// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function SectionHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <View style={styles.sectionHeader}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {subtitle ? <Text style={styles.sectionSubtitle}>{subtitle}</Text> : null}
    </View>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.statCard}>
      <Text style={styles.statCardValue}>{value}</Text>
      <Text style={styles.statCardLabel}>{label}</Text>
    </View>
  );
}

function TimeRangeFilter({
  selected,
  onSelect,
}: {
  selected: TimeRange;
  onSelect: (r: TimeRange) => void;
}) {
  const { accent } = useAccent();
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.rangeRow}
    >
      {RANGES.map((r) => {
        const active = r.value === selected;
        return (
          <Pressable
            key={r.value}
            onPress={() => onSelect(r.value)}
            style={[
              styles.rangeChip,
              active && { backgroundColor: accent.primary },
            ]}
            accessibilityRole="button"
            accessibilityLabel={r.label}
            accessibilityState={{ selected: active }}
          >
            <Text
              style={[
                styles.rangeChipText,
                active && styles.rangeChipTextActive,
              ]}
            >
              {r.label}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

function WornItemRow({ item, showCount }: { item: StatItem; showCount: boolean }) {
  const { accent } = useAccent();
  const imageUri = item.image_path
    ? item.image_path.startsWith('file://') || item.image_path.startsWith('http')
      ? item.image_path
      : `file://${item.image_path}`
    : null;
  return (
    <View style={styles.wornRow}>
      <View style={styles.wornThumb}>
        {imageUri ? (
          <Image
            source={{ uri: imageUri }}
            style={styles.wornThumbImage}
            contentFit="cover"
          />
        ) : (
          <View style={styles.wornThumbPlaceholder} />
        )}
      </View>
      <Text style={styles.wornName} numberOfLines={1}>
        {item.name}
      </Text>
      {showCount ? (
        <View style={[styles.wornBadge, { backgroundColor: accent.subtle }]}>
          <Text style={[styles.wornBadgeText, { color: accent.primary }]}>
            {item.wear_count}×
          </Text>
        </View>
      ) : null}
    </View>
  );
}

function WornSection({
  title,
  subtitle,
  items,
  showCount,
}: {
  title: string;
  subtitle?: string;
  items: StatItem[];
  showCount: boolean;
}) {
  return (
    <View style={styles.section}>
      <SectionHeader title={title} subtitle={subtitle} />
      {items.length === 0 ? (
        <Text style={styles.emptyText}>No data for this period.</Text>
      ) : (
        items.map((item) => (
          <WornItemRow key={item.id} item={item} showCount={showCount} />
        ))
      )}
    </View>
  );
}

function BarRow({
  label,
  count,
  maxCount,
  barColor,
}: {
  label: string;
  count: number;
  maxCount: number;
  barColor: string;
}) {
  const ratio = maxCount > 0 ? count / maxCount : 0;
  return (
    <View style={styles.barRow}>
      <Text style={styles.barLabel} numberOfLines={1}>
        {label}
      </Text>
      <View style={styles.barTrack}>
        <View
          style={[
            styles.barFill,
            { width: `${ratio * 100}%` as `${number}%`, backgroundColor: barColor },
          ]}
        />
      </View>
      <Text style={styles.barCount}>{count}</Text>
    </View>
  );
}

function BreakdownSection({
  title,
  data,
  barColor,
}: {
  title: string;
  data: BreakdownRow[];
  barColor: string;
}) {
  const maxCount = data.reduce((m, row) => Math.max(m, row.count), 0);
  return (
    <View style={styles.section}>
      <SectionHeader title={title} />
      {data.length === 0 ? (
        <Text style={styles.emptyText}>No data.</Text>
      ) : (
        data.map((row, index) => (
          <BarRow
            key={`${row.label}-${index}`}
            label={row.label}
            count={row.count}
            maxCount={maxCount}
            barColor={barColor}
          />
        ))
      )}
    </View>
  );
}

function ColorBreakdownSection({ data }: { data: ColorBreakdownRow[] }) {
  const { accent } = useAccent();
  const maxCount = data.reduce((m, row) => Math.max(m, row.count), 0);
  return (
    <View style={styles.section}>
      <SectionHeader title="By Color" />
      {data.length === 0 ? (
        <Text style={styles.emptyText}>No data.</Text>
      ) : (
        data.map((row, index) => (
          <BarRow
            key={`${row.label}-${row.hex ?? ''}-${index}`}
            label={row.label}
            count={row.count}
            maxCount={maxCount}
            barColor={row.hex ?? accent.primary}
          />
        ))
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

export default function StatsScreen() {
  const insets = useSafeAreaInsets();
  const { accent } = useAccent();
  const [range, setRange] = useState<TimeRange>('all');
  const fromDate = useMemo(() => toFromDate(range), [range]);
  const { data, loading, error, refresh } = useStats(fromDate);

  const pctWorn =
    data && data.overview.total_items > 0
      ? Math.round((data.overview.worn_items / data.overview.total_items) * 100)
      : null;

  const totalValueStr =
    data?.overview.total_value != null
      ? `$${data.overview.total_value.toFixed(2)}`
      : '—';

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={
          <RefreshControl
            refreshing={loading && !!data}
            onRefresh={refresh}
            tintColor={accent.primary}
          />
        }
      >
        {/* Screen title */}
        <Text style={styles.screenTitle}>Stats</Text>

        {/* Time range filter */}
        <TimeRangeFilter selected={range} onSelect={setRange} />

        {/* Loading / error states */}
        {loading && data === null ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={accent.primary} />
          </View>
        ) : error !== null ? (
          <View style={styles.errorContainer}>
            <Text style={styles.errorText}>Something went wrong loading stats.</Text>
            <Pressable
              onPress={refresh}
              style={styles.retryButton}
              accessibilityRole="button"
              accessibilityLabel="Retry"
            >
              <Text style={[styles.retryText, { color: accent.primary }]}>Retry</Text>
            </Pressable>
          </View>
        ) : data !== null ? (
          <>
            {/* Headline row */}
            <View style={styles.headlineRow}>
              <StatCard
                label="of closet worn"
                value={pctWorn !== null ? `${pctWorn}%` : '—'}
              />
              <StatCard label="total value" value={totalValueStr} />
            </View>

            {/* Worn item lists */}
            <WornSection
              title="Most Worn"
              subtitle="Top 15 by wear count"
              items={data.mostWorn}
              showCount
            />
            <WornSection
              title="Least Worn"
              subtitle="Worn at least once"
              items={data.leastWorn}
              showCount
            />
            <WornSection
              title="Never Worn"
              subtitle={range === 'all' ? undefined : 'In this period'}
              items={data.neverWorn}
              showCount={false}
            />

            {/* Breakdown charts */}
            <BreakdownSection
              title="By Category"
              data={data.byCategory}
              barColor={accent.primary}
            />
            <ColorBreakdownSection data={data.byColor} />
            <BreakdownSection
              title="By Brand"
              data={data.byBrand}
              barColor={accent.primary}
            />
            <BreakdownSection
              title="By Material"
              data={data.byMaterial}
              barColor={accent.primary}
            />
            <BreakdownSection
              title="By Occasion"
              data={data.byOccasion}
              barColor={accent.primary}
            />
            <BreakdownSection
              title="By Season"
              data={data.bySeason}
              barColor={accent.primary}
            />
          </>
        ) : null}

        <View style={{ height: insets.bottom + Spacing[4] }} />
      </ScrollView>
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
  scroll: {
    paddingHorizontal: Spacing[4],
    paddingTop: Spacing[4],
  },
  screenTitle: {
    fontSize: FontSize['2xl'],
    fontWeight: FontWeight.bold,
    color: Palette.textPrimary,
    marginBottom: Spacing[3],
  },

  // Time range filter
  rangeRow: {
    flexDirection: 'row',
    gap: Spacing[2],
    marginBottom: Spacing[4],
  },
  rangeChip: {
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    borderRadius: Radius.full,
    backgroundColor: Palette.surface3,
  },
  rangeChipText: {
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    color: Palette.textSecondary,
  },
  rangeChipTextActive: {
    color: Palette.black,
  },

  // Loading / error
  loadingContainer: {
    flex: 1,
    paddingVertical: Spacing[16],
    alignItems: 'center',
    justifyContent: 'center',
  },
  errorContainer: {
    paddingVertical: Spacing[8],
    alignItems: 'center',
    gap: Spacing[3],
  },
  errorText: {
    fontSize: FontSize.md,
    color: Palette.error,
    textAlign: 'center',
  },
  retryButton: {
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[2],
  },
  retryText: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },

  // Headline / stat cards
  headlineRow: {
    flexDirection: 'row',
    gap: Spacing[3],
    marginBottom: Spacing[4],
  },
  statCard: {
    flex: 1,
    backgroundColor: Palette.surface2,
    borderRadius: Radius.md,
    padding: Spacing[4],
    alignItems: 'center',
    gap: Spacing[1],
  },
  statCardValue: {
    fontSize: FontSize['2xl'],
    fontWeight: FontWeight.bold,
    color: Palette.textPrimary,
  },
  statCardLabel: {
    fontSize: FontSize.sm,
    color: Palette.textSecondary,
    textAlign: 'center',
  },

  // Section
  section: {
    marginBottom: Spacing[6],
  },
  sectionHeader: {
    marginBottom: Spacing[2],
  },
  sectionTitle: {
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
    color: Palette.textPrimary,
  },
  sectionSubtitle: {
    fontSize: FontSize.sm,
    color: Palette.textSecondary,
    marginTop: Spacing[1],
  },
  emptyText: {
    fontSize: FontSize.sm,
    color: Palette.textDisabled,
    paddingVertical: Spacing[2],
  },

  // Worn item rows
  wornRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing[2],
    gap: Spacing[3],
  },
  wornThumb: {
    width: 40,
    height: 40,
    borderRadius: Radius.sm,
    overflow: 'hidden',
    backgroundColor: Palette.surface3,
  },
  wornThumbImage: {
    width: 40,
    height: 40,
  },
  wornThumbPlaceholder: {
    flex: 1,
    backgroundColor: Palette.surface3,
  },
  wornName: {
    flex: 1,
    fontSize: FontSize.md,
    color: Palette.textPrimary,
  },
  wornBadge: {
    paddingHorizontal: Spacing[2],
    paddingVertical: Spacing[1],
    borderRadius: Radius.full,
  },
  wornBadgeText: {
    fontSize: FontSize.sm,
    fontWeight: FontWeight.semibold,
  },

  // Bar chart rows
  barRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing[2],
    gap: Spacing[2],
  },
  barLabel: {
    flexBasis: 90,
    flexShrink: 1,
    fontSize: FontSize.sm,
    color: Palette.textPrimary,
  },
  barTrack: {
    flex: 1,
    height: 12,
    backgroundColor: Palette.surface3,
    borderRadius: Radius.full,
    overflow: 'hidden',
  },
  barFill: {
    height: 12,
    borderRadius: Radius.full,
  },
  barCount: {
    flexBasis: 32,
    flexShrink: 0,
    fontSize: FontSize.sm,
    color: Palette.textSecondary,
    textAlign: 'right',
  },
});
