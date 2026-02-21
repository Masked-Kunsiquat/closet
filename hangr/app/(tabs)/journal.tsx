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

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/** Returns YYYY-MM string for the given year+month (0-indexed month). */
function toYearMonth(year: number, month: number): string {
  return `${year}-${String(month + 1).padStart(2, '0')}`;
}

/** Returns today as YYYY-MM-DD in local time. */
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

type CalendarDayMap = Record<string, CalendarDay>;

export default function JournalScreen() {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const today = new Date();
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth()); // 0-indexed

  const yearMonth = toYearMonth(year, month);
  const { days, loading, refresh } = useCalendarMonth(yearMonth);

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
    setMonth((m) => {
      if (m === 0) { setYear((y) => y - 1); return 11; }
      return m - 1;
    });
  }, []);

  const goToNextMonth = useCallback(() => {
    setMonth((m) => {
      if (m === 11) { setYear((y) => y + 1); return 0; }
      return m + 1;
    });
  }, []);

  // Total logs in this month
  const totalLogs = useMemo(() => days.reduce((s, d) => s + d.log_count, 0), [days]);

  const todayStr = todayIso();

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <ScrollView
        contentContainerStyle={[styles.scroll, { paddingBottom: insets.bottom + Spacing[8] }]}
        onScrollEndDrag={refresh}
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
          <TouchableOpacity onPress={goToPrevMonth} hitSlop={12} style={styles.navBtn}>
            <Text style={styles.navArrow}>‹</Text>
          </TouchableOpacity>

          <Text style={styles.monthLabel}>
            {MONTH_NAMES[month]} {year}
          </Text>

          <TouchableOpacity onPress={goToNextMonth} hitSlop={12} style={styles.navBtn}>
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
          onPressDay={(date) => router.push(`/log/${date}`)}
        />
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Calendar grid
// ---------------------------------------------------------------------------

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
// ---------------------------------------------------------------------------

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

  return (
    <Pressable
      style={[styles.cell, isToday && styles.cellToday]}
      onPress={onPress}
      disabled={!hasLogs && !isToday}
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
          <Text style={styles.logChipText}>{data!.log_count}</Text>
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
    fontWeight: FontWeight.normal,
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
    color: '#000',
    fontSize: 10,
    fontWeight: FontWeight.bold,
    lineHeight: 12,
  },
  ootdStar: {
    fontSize: 9,
    color: '#F59E0B',
    lineHeight: 11,
  },
});
