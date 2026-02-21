/**
 * Day Detail screen — /log/YYYY-MM-DD
 *
 * Lists all outfit logs for a given date.
 * Lets user mark/unmark OOTD and delete individual logs.
 */

import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import {
  Alert,
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
import { getDatabase } from '@/db';
import { clearOotd, deleteOutfitLog, setOotd } from '@/db/queries';
import { OutfitLogWithMeta } from '@/db/types';
import { useLogsForDate } from '@/hooks/useOutfitLog';

/**
 * Convert an ISO date string (YYYY-MM-DD) into a localized, human-readable date.
 *
 * @param iso - Date in `YYYY-MM-DD` format
 * @returns A locale-formatted date string like "March 21, 2025"
 */
function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, {
    year: 'numeric', month: 'long', day: 'numeric',
  });
}

/**
 * Renders the Day Detail screen showing outfit logs for a specific date, including header, empty state, and a list of logs with OOTD and delete actions.
 *
 * The component reads the target date from the route, fetches logs for that date, and provides UI for toggling an outfit as OOTD (with replacement confirmation) and deleting logs (with confirmation). Navigation to an outfit detail is available when a log references an outfit.
 *
 * @returns The screen component that displays and manages outfit logs for the provided date.
 */
export default function DayDetailScreen() {
  const { date } = useLocalSearchParams<{ date: string }>();
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const { logs, loading, refresh } = useLogsForDate(date);

  const handleToggleOotd = async (log: OutfitLogWithMeta) => {
    const db = await getDatabase();
    if (log.is_ootd === 1) {
      await clearOotd(db, log.id);
    } else {
      // Check if another log on this date is already OOTD
      const currentOotd = logs.find((l) => l.is_ootd === 1);
      if (currentOotd) {
        Alert.alert(
          'Replace OOTD?',
          'Another outfit is already marked OOTD for this day. Replace it?',
          [
            { text: 'Cancel', style: 'cancel' },
            {
              text: 'Replace', style: 'destructive',
              onPress: async () => {
                await setOotd(db, log.id, date);
                refresh();
              },
            },
          ]
        );
        return;
      }
      await setOotd(db, log.id, date);
    }
    refresh();
  };

  const handleDeleteLog = (log: OutfitLogWithMeta) => {
    Alert.alert('Remove log?', 'This removes the wear entry for this day.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove', style: 'destructive',
        onPress: async () => {
          const db = await getDatabase();
          await deleteOutfitLog(db, log.id);
          refresh();
        },
      },
    ]);
  };

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={10}>
          <Text style={styles.back}>← Back</Text>
        </TouchableOpacity>
        <View style={styles.headerCenter}>
          <Text style={styles.headerDate}>{formatDate(date)}</Text>
          <Text style={styles.headerCount}>
            {logs.length} log{logs.length !== 1 ? 's' : ''}
          </Text>
        </View>
        <View style={{ minWidth: 60 }} />
      </View>

      {!loading && logs.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyEmoji}>📅</Text>
          <Text style={styles.emptyTitle}>No logs for this day</Text>
          <Text style={styles.emptySubtitle}>Log an outfit from the Outfits tab.</Text>
        </View>
      ) : (
        <FlatList
          data={logs}
          keyExtractor={(l) => String(l.id)}
          contentContainerStyle={styles.list}
          onRefresh={refresh}
          refreshing={loading}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          renderItem={({ item }) => (
            <LogRow
              log={item}
              accent={accent.primary}
              onToggleOotd={() => handleToggleOotd(item)}
              onDelete={() => handleDeleteLog(item)}
              onPressOutfit={() => {
                if (item.outfit_id) router.push(`/outfit/${item.outfit_id}` as any);
              }}
            />
          )}
        />
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Log row
/**
 * Renders a single outfit log row with thumbnail, metadata, and action controls.
 *
 * Shows a pressable cover thumbnail (or placeholder), outfit name, item count and notes,
 * plus buttons to toggle OOTD and delete the log. When the log is marked OOTD the badge
 * uses the provided accent color.
 *
 * @param log - The outfit log and associated metadata to display
 * @param accent - Accent color used for the active OOTD badge
 * @param onToggleOotd - Called when the OOTD badge is pressed
 * @param onDelete - Called when the delete action is pressed
 * @param onPressOutfit - Called when the thumbnail area is pressed
 * @returns A JSX element representing the log row
 */

function LogRow({
  log,
  accent,
  onToggleOotd,
  onDelete,
  onPressOutfit,
}: {
  log: OutfitLogWithMeta;
  accent: string;
  onToggleOotd: () => void;
  onDelete: () => void;
  onPressOutfit: () => void;
}) {
  return (
    <View style={styles.logRow}>
      {/* Cover thumbnail */}
      <Pressable style={styles.logThumb} onPress={onPressOutfit}>
        {log.cover_image ? (
          <Image source={{ uri: log.cover_image }} style={styles.logThumbImage} contentFit="cover" />
        ) : (
          <View style={styles.logThumbPlaceholder}>
            <Text style={styles.logThumbEmoji}>👗</Text>
          </View>
        )}
      </Pressable>

      {/* Meta */}
      <View style={styles.logMeta}>
        <Text style={styles.logOutfitName} numberOfLines={1}>
          {log.outfit_name ?? 'Untitled Outfit'}
        </Text>
        <Text style={styles.logItemCount}>
          {log.item_count} item{log.item_count !== 1 ? 's' : ''}
          {log.notes ? ` · ${log.notes}` : ''}
        </Text>
      </View>

      {/* Actions */}
      <View style={styles.logActions}>
        <TouchableOpacity onPress={onToggleOotd} hitSlop={8}>
          <View style={[
            styles.ootdBadge,
            log.is_ootd === 1 && { backgroundColor: accent },
          ]}>
            <Text style={[
              styles.ootdBadgeText,
              log.is_ootd === 1 && { color: '#000' },
            ]}>
              OOTD
            </Text>
          </View>
        </TouchableOpacity>

        <TouchableOpacity onPress={onDelete} hitSlop={8} style={styles.deleteBtn}>
          <Text style={styles.deleteBtnText}>✕</Text>
        </TouchableOpacity>
      </View>
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
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  back: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    minWidth: 60,
  },
  headerCenter: {
    alignItems: 'center',
  },
  headerDate: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
  headerCount: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    marginTop: 2,
  },
  list: {
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[3],
    paddingBottom: Spacing[16],
  },
  separator: {
    height: 1,
    backgroundColor: Palette.borderMuted,
  },

  // Log row
  logRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing[3],
    gap: Spacing[3],
  },
  logThumb: {
    width: 56,
    height: 72,
    borderRadius: Radius.sm,
    overflow: 'hidden',
    backgroundColor: Palette.surface2,
    flexShrink: 0,
  },
  logThumbImage: {
    width: '100%',
    height: '100%',
  },
  logThumbPlaceholder: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  logThumbEmoji: {
    fontSize: 24,
  },
  logMeta: {
    flex: 1,
    gap: 2,
  },
  logOutfitName: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
  },
  logItemCount: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },
  logActions: {
    alignItems: 'flex-end',
    gap: Spacing[2],
    flexShrink: 0,
  },
  ootdBadge: {
    paddingHorizontal: Spacing[2],
    paddingVertical: 3,
    borderRadius: Radius.sm,
    backgroundColor: Palette.surface3,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  ootdBadgeText: {
    color: Palette.textDisabled,
    fontSize: FontSize.xs,
    fontWeight: FontWeight.semibold,
    letterSpacing: 0.5,
  },
  deleteBtn: {
    padding: Spacing[1],
  },
  deleteBtnText: {
    color: Palette.textDisabled,
    fontSize: FontSize.sm,
  },

  // Empty
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing[8],
  },
  emptyEmoji: {
    fontSize: 48,
    marginBottom: Spacing[3],
  },
  emptyTitle: {
    color: Palette.textPrimary,
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
    marginBottom: Spacing[2],
    textAlign: 'center',
  },
  emptySubtitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'center',
  },
});