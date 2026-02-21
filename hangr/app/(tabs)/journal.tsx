/**
 * Journal tab — monthly calendar view.
 *
 * Each cell shows:
 *   - A counter chip (number of outfit logs)
 *   - A gold star if the day has an OOTD
 *
 * Tapping a day navigates to /log/YYYY-MM-DD.
 * Month navigation via prev/next arrows.
 */

import { useRouter } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { CalendarDay } from '@/db/types';
import { useCalendarMonth } from '@/hooks/useOutfitLog';
import { contrastingTextColor } from '@/utils/color';

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/**
 * Format a year and 0-indexed month into a `YYYY-MM` string.
 *
 * @param year - The full year (e.g., `2026`)
 * @param month - Month as 0-indexed (0 = January, 11 = December)
 * @returns A string in the format `YYYY-MM` where the month is one-based and zero-padded
 */
function toYearMonth(year: number, month: number): string {
  return `${year}-${String(month + 1).padStart(2, '0')}`;
}

/**
 * Get today's date formatted as YYYY-MM-DD in the local timezone.
 *
 * @returns The current local date as `YYYY-MM-DD`
 */
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

type CalendarDayMap = Record<string, CalendarDay>;

/**
 * Render the Journal screen with a monthly calendar view, month navigation, and pull-to-refresh.
 *
 * Displays a calendar grid for the selected year/month where each day cell shows a log count chip and an OOTD indicator when applicable. Provides previous/next month controls, highlights today, supports pull-to-refresh, and shows an error view with a retry action when calendar data fails to load. Tapping a day with logs navigates to that day's log page.
 *
 * @returns The Journal screen React element.
 */
export default function JournalScreen() {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const today = new Date();
  const [ym, setYm] = useState({ year: today.getFullYear(), month: today.getMonth() });
  const { year, month } = ym;

  const yearMonth = toYearMonth(year, month);
  const { days, loading, error, refresh } = useCalendarMonth(yearMonth);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const handleRefresh = useCallback(async () => {
    setIsRefreshing(true);
    try {
      await refresh();
    } finally {
      setIsRefreshing(false);
    }
  }, [refresh]);

  // Build a lookup map: date string → CalendarDay
  const dayMap = useMemo<CalendarDayMap>(() => {
    const map: CalendarDayMap = {};
    for (const d of days) map[d.date] = d;
    return map;
  }, [days]);

  // Build the grid: array of date strings (or null for padding)
  const gridCells = useMemo<(string | null)[]>(() => {
    const firstDay = new Date(year, month, 1).getDay(); // 0=Sun
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const cells: (string | null)[] = Array(firstDay).fill(null);
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push(`${yearMonth}-${String(d).padStart(2, '0')}`);
    }
    return cells;
  }, [year, month, yearMonth]);

  const goToPrevMonth = useCallback(() => {
    setYm(({ year: y, month: m }) =>
      m === 0 ? { year: y - 1, month: 11 } : { year: y, month: m - 1 }
    );
  }, []);

  const goToNextMonth = useCallback(() => {
    setYm(({ year: y, month: m }) =>
      m === 11 ? { year: y + 1, month: 0 } : { year: y, month: m + 1 }
    );
  }, []);

  // Total logs in this month
  const totalLogs = useMemo(() => days.reduce((s, d) => s + d.log_count, 0), [days]);

  const todayStr = todayIso();

  if (error) {
    return (
      <View style={[styles.container, styles.errorContainer, { paddingTop: insets.top }]}>
        <Text style={styles.errorText}>Failed to load journal.{'\n'}{error}</Text>
        <TouchableOpacity style={styles.errorButton} onPress={handleRefresh}>
          <Text style={styles.errorButtonText}>Retry</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <ScrollView
        contentContainerStyle={[styles.scroll, { paddingBottom: insets.bottom + Spacing[8] }]}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} />}
      >
        {/* Page header */}
        <View style={styles.pageHeader}>
          <Text style={styles.pageTitle}>Journal</Text>
          {!loading && totalLogs > 0 && (
            <Text style={styles.pageCount}>{totalLogs} log{totalLogs !== 1 ? 's' : ''} this month</Text>
          )}
        </View>

        {/* Month navigation */}
        <View style={styles.monthNav}>
          <TouchableOpacity
            onPress={goToPrevMonth}
            hitSlop={12}
            style={styles.navBtn}
            accessibilityRole="button"
            accessibilityLabel="Previous month"
          >
            <Text style={styles.navArrow}>‹</Text>
          </TouchableOpacity>

          <Text style={styles.monthLabel}>
            {MONTH_NAMES[month]} {year}
          </Text>

          <TouchableOpacity
            onPress={goToNextMonth}
            hitSlop={12}
            style={styles.navBtn}
            accessibilityRole="button"
            accessibilityLabel="Next month"
          >
            <Text style={styles.navArrow}>›</Text>
          </TouchableOpacity>
        </View>

        {/* Weekday header row */}
        <View style={styles.weekdayRow}>
          {WEEKDAY_LABELS.map((l) => (
            <View key={l} style={styles.weekdayCell}>
              <Text style={styles.weekdayText}>{l}</Text>
            </View>
          ))}
        </View>

        {/* Calendar grid */}
        <CalendarGrid
          cells={gridCells}
          dayMap={dayMap}
          today={todayStr}
          accent={accent.primary}
          onPressDay={(date) => router.push(`/log/${date}` as any)}
        />
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Calendar grid
/**
 * Render a 7-column calendar grid of DayCell components for the provided month cells.
 *
 * @param cells - Flat array of date strings or `null` values (padding) representing the month laid out row-wise.
 * @param dayMap - Map from ISO date string to CalendarDay data used to populate each cell.
 * @param today - ISO date string for the current day; used to mark the "today" cell.
 * @param accent - Accent color used for today/indicator styling.
 * @param onPressDay - Callback invoked with an ISO date string when a populated day cell is pressed.
 * @returns A React element containing rows of DayCell components arranged into a 7-column calendar grid.
 */

function CalendarGrid({
  cells,
  dayMap,
  today,
  accent,
  onPressDay,
}: {
  cells: (string | null)[];
  dayMap: CalendarDayMap;
  today: string;
  accent: string;
  onPressDay: (date: string) => void;
}) {
  // Chunk into rows of 7
  const rows: (string | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) {
    rows.push(cells.slice(i, i + 7));
    // Pad last row
    while (rows[rows.length - 1].length < 7) rows[rows.length - 1].push(null);
  }

  return (
    <View style={styles.grid}>
      {rows.map((row, ri) => (
        <View key={ri} style={styles.gridRow}>
          {row.map((date, ci) => (
            <DayCell
              key={date ?? `empty-${ri}-${ci}`}
              date={date}
              data={date ? dayMap[date] : undefined}
              isToday={date === today}
              accent={accent}
              onPress={date ? () => onPressDay(date) : undefined}
            />
          ))}
        </View>
      ))}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Individual day cell
/**
 * Render a calendar day cell that displays the day number and, when present, a log count chip and an OOTD star; the cell can be pressed when it represents today or has logs.
 *
 * @param date - The date for the cell in `YYYY-MM-DD` format, or `null` for an empty/padding cell.
 * @param data - Optional calendar metadata for the date (used to derive `log_count` and `has_ootd`).
 * @param isToday - Whether this cell represents today; affects styling and enabled state.
 * @param accent - Accent color used for highlighting and the log count chip background.
 * @param onPress - Optional callback invoked when the cell is pressed.
 * @returns A React element representing the day cell.
 */


function DayCell({
  date,
  data,
  isToday,
  accent,
  onPress,
}: {
  date: string | null;
  data?: CalendarDay;
  isToday: boolean;
  accent: string;
  onPress?: () => void;
}) {
  if (!date) {
    return <View style={styles.cell} />;
  }

  const dayNumber = Number(date.split('-')[2]);
  const hasLogs = !!data && data.log_count > 0;
  const isOotd = !!data && data.has_ootd === 1;

  const parts = [String(dayNumber)];
  if (hasLogs) parts.push(`${data!.log_count} outfit log${data!.log_count !== 1 ? 's' : ''}`);
  if (isOotd) parts.push('Outfit of the Day');
  const a11yLabel = parts.join(', ');

  return (
    <Pressable
      style={[styles.cell, isToday && styles.cellToday]}
      onPress={onPress}
      disabled={!hasLogs && !isToday}
      accessibilityRole="button"
      accessibilityLabel={a11yLabel}
      accessibilityState={{ disabled: !hasLogs && !isToday }}
    >
      {/* Day number */}
      <Text style={[
        styles.dayNumber,
        isToday && { color: accent, fontWeight: FontWeight.bold },
        !hasLogs && !isToday && { color: Palette.textDisabled },
      ]}>
        {dayNumber}
      </Text>

      {/* Log count chip */}
      {hasLogs && (
        <View style={[styles.logChip, { backgroundColor: accent }]}>
          <Text style={[styles.logChipText, { color: contrastingTextColor(accent) }]}>{data!.log_count}</Text>
        </View>
      )}

      {/* OOTD star */}
      {isOotd && (
        <Text style={styles.ootdStar}>★</Text>
      )}
    </Pressable>
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
    padding: Spacing[4],
  },

  // Page header
  pageHeader: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    marginBottom: Spacing[5],
  },
  pageTitle: {
    color: Palette.textPrimary,
    fontSize: FontSize['2xl'],
    fontWeight: FontWeight.bold,
  },
  pageCount: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },

  // Month nav
  monthNav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: Spacing[4],
  },
  navBtn: {
    padding: Spacing[2],
  },
  navArrow: {
    color: Palette.textPrimary,
    fontSize: FontSize['2xl'],
    lineHeight: FontSize['2xl'],
  },
  monthLabel: {
    color: Palette.textPrimary,
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
  },

  // Weekday header
  weekdayRow: {
    flexDirection: 'row',
    marginBottom: Spacing[2],
  },
  weekdayCell: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: Spacing[1],
  },
  weekdayText: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    fontWeight: FontWeight.medium,
  },

  // Grid
  grid: {
    gap: Spacing[1],
  },
  gridRow: {
    flexDirection: 'row',
    gap: Spacing[1],
  },

  // Day cell
  cell: {
    flex: 1,
    minHeight: 56,
    borderRadius: Radius.sm,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: Spacing[2],
    gap: 2,
  },
  cellToday: {
    backgroundColor: Palette.surface2,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  dayNumber: {
    color: Palette.textPrimary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.regular,
  },
  logChip: {
    borderRadius: Radius.full,
    minWidth: 18,
    height: 18,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 4,
  },
  logChipText: {
    fontSize: 10,
    fontWeight: FontWeight.bold,
    lineHeight: 12,
  },
  ootdStar: {
    fontSize: 9,
    color: '#F59E0B',
    lineHeight: 11,
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