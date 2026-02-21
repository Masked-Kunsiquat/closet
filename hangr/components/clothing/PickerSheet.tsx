/**
 * PickerSheet — reusable multi-select bottom sheet for ItemForm.
 *
 * Slides up from the bottom using the same Modal + Animated pattern as FilterPanel.
 * Renders a scrollable chip grid; selection is toggled in place and committed
 * immediately (no draft state — the parent owns all selection state).
 */

import { useEffect, useRef } from 'react';
import {
  Animated,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  useWindowDimensions,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { contrastingTextColor } from '@/utils/color';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

type PickerSheetProps<T extends { id: number; name: string }> = {
  visible: boolean;
  onClose: () => void;
  title: string;
  items: T[];
  selectedIds: number[];
  onToggle: (id: number) => void;
  /** Optional leading dot/swatch renderer per item (used for color chips). */
  renderDot?: (item: T) => React.ReactNode;
};

// ---------------------------------------------------------------------------
// Component
/**
 * Presents a multi-select chip picker in an animated bottom sheet.
 *
 * Selection changes are applied immediately via `onToggle`; there is no
 * internal draft — the parent owns all state. Close the sheet by tapping
 * the backdrop, pressing "Done", or the OS back gesture.
 *
 * @param visible - Whether the sheet is visible
 * @param onClose - Called when the sheet should close
 * @param title - Label shown in the sheet header
 * @param items - Items to display as selectable chips
 * @param selectedIds - Currently selected item IDs
 * @param onToggle - Called with an item's id when its chip is tapped
 * @param renderDot - Optional renderer for a leading swatch per chip
 */
// ---------------------------------------------------------------------------

export function PickerSheet<T extends { id: number; name: string }>({
  visible,
  onClose,
  title,
  items,
  selectedIds,
  onToggle,
  renderDot,
}: PickerSheetProps<T>) {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();
  const slideAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(slideAnim, {
      toValue: visible ? 1 : 0,
      duration: 280,
      useNativeDriver: true,
    }).start();
  }, [visible, slideAnim]);

  const translateY = slideAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [screenHeight, 0],
  });

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
    >
      {/* Backdrop */}
      <Pressable style={styles.backdrop} onPress={onClose} />

      {/* Sheet */}
      <Animated.View
        style={[
          styles.sheet,
          { maxHeight: screenHeight * 0.6, paddingBottom: insets.bottom + Spacing[2], transform: [{ translateY }] },
        ]}
      >
        {/* Handle */}
        <View style={styles.handle} />

        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>{title}</Text>
          <TouchableOpacity onPress={onClose} hitSlop={12}>
            <Text style={[styles.doneText, { color: accent.primary }]}>Done</Text>
          </TouchableOpacity>
        </View>

        {/* Chips */}
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.chips}
        >
          {items.map((item) => {
            const selected = selectedIds.includes(item.id);
            return (
              <Pressable
                key={item.id}
                style={[styles.chip, selected && { backgroundColor: accent.primary, borderColor: accent.primary }]}
                onPress={() => onToggle(item.id)}
                accessibilityRole="checkbox"
                accessibilityState={{ checked: selected }}
                accessibilityLabel={item.name}
              >
                {renderDot?.(item)}
                <Text
                  style={[
                    styles.chipText,
                    selected && { fontWeight: FontWeight.semibold, color: contrastingTextColor(accent.primary) },
                  ]}
                >
                  {item.name}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </Animated.View>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  sheet: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: Palette.surface1,
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    paddingTop: Spacing[2],
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: Radius.full,
    backgroundColor: Palette.border,
    alignSelf: 'center',
    marginBottom: Spacing[3],
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  title: {
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
    color: Palette.textPrimary,
  },
  doneText: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing[2],
    padding: Spacing[4],
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[1],
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface2,
  },
  chipText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },
});
