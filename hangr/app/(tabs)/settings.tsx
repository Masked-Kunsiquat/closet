/**
 * Settings tab — user preferences.
 *
 * Sections:
 *   Appearance — accent color theme picker (swatches)
 *   Closet     — show/hide archived items
 *   Display    — currency symbol, temperature unit
 *   Calendar   — week start day
 *   About      — app info
 */

import { useState } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  AccentKey,
  AccentPalettes,
  FontSize,
  FontWeight,
  Palette,
  Radius,
  Spacing,
} from '@/constants/tokens';
import { PhosphorIcon } from '@/components/PhosphorIcon';
import { useAccent } from '@/context/AccentContext';
import { useSettings } from '@/context/SettingsContext';

// ---------------------------------------------------------------------------
// Option data
// ---------------------------------------------------------------------------

const ACCENT_OPTIONS: { value: AccentKey; label: string }[] = [
  { value: 'amber',    label: 'Amber'    },
  { value: 'coral',    label: 'Coral'    },
  { value: 'sage',     label: 'Sage'     },
  { value: 'sky',      label: 'Sky'      },
  { value: 'lavender', label: 'Lavender' },
  { value: 'rose',     label: 'Rose'     },
];

const CURRENCY_OPTIONS = [
  { value: '$', label: '$ — Dollar'   },
  { value: '£', label: '£ — Pound'    },
  { value: '€', label: '€ — Euro'     },
  { value: '¥', label: '¥ — Yen / Yuan' },
  { value: '₩', label: '₩ — Won'      },
];

const TEMP_OPTIONS = [
  { value: 'F', label: '°F — Fahrenheit' },
  { value: 'C', label: '°C — Celsius'    },
];

const WEEK_START_OPTIONS: { value: 0 | 1; label: string }[] = [
  { value: 0, label: 'Sunday'  },
  { value: 1, label: 'Monday'  },
];

// ---------------------------------------------------------------------------
// SelectModal — generic picker sheet
// ---------------------------------------------------------------------------

function SelectModal<T extends string | number>({
  visible,
  title,
  options,
  selected,
  onSelect,
  onClose,
  dotColor,
}: {
  visible: boolean;
  title: string;
  options: { value: T; label: string }[];
  selected: T;
  onSelect: (v: T) => void;
  onClose: () => void;
  dotColor?: (value: T) => string;
}) {
  const { accent } = useAccent();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} />
      <View style={styles.sheet}>
        <View style={styles.sheetHandle} />
        <Text style={styles.sheetTitle}>{title}</Text>
        {options.map((opt, i) => {
          const active = opt.value === selected;
          return (
            <Pressable
              key={String(opt.value)}
              style={[styles.sheetOption, i < options.length - 1 && styles.sheetOptionBorder]}
              onPress={() => { onSelect(opt.value); onClose(); }}
              accessibilityRole="radio"
              accessibilityState={{ selected: active }}
            >
              <View style={styles.sheetOptionLeft}>
                {dotColor && (
                  <View style={[styles.dot, { backgroundColor: dotColor(opt.value) }]} />
                )}
                <Text style={[styles.sheetOptionText, active && { color: accent.primary, fontWeight: FontWeight.semibold }]}>
                  {opt.label}
                </Text>
              </View>
              {active && (
                <Text style={[styles.sheetOptionCheck, { color: accent.primary }]}>✓</Text>
              )}
            </Pressable>
          );
        })}
        <View style={{ height: Spacing[4] }} />
      </View>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// SettingSelectRow — label + current value + chevron, opens modal
// ---------------------------------------------------------------------------

function SettingSelectRow<T extends string | number>({
  label,
  options,
  selected,
  onSelect,
  last = false,
}: {
  label: string;
  options: { value: T; label: string }[];
  selected: T;
  onSelect: (v: T) => void;
  last?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const currentLabel = options.find((o) => o.value === selected)?.label ?? String(selected);

  return (
    <>
      <TouchableOpacity
        style={[styles.row, !last && styles.rowBorder]}
        onPress={() => setOpen(true)}
        activeOpacity={0.7}
      >
        <Text style={styles.rowLabel}>{label}</Text>
        <View style={styles.rowRight}>
          <Text style={styles.rowValue} numberOfLines={1}>{currentLabel}</Text>
          <PhosphorIcon name="caret-right" size={18} color={Palette.textDisabled} />
        </View>
      </TouchableOpacity>
      <SelectModal
        visible={open}
        title={label}
        options={options}
        selected={selected}
        onSelect={onSelect}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// AccentSelectRow — accent color as a select row with color dots
// ---------------------------------------------------------------------------

function AccentSelectRow() {
  const { accentKey, setAccent } = useAccent();
  const [open, setOpen] = useState(false);
  const currentLabel = ACCENT_OPTIONS.find((o) => o.value === accentKey)?.label ?? accentKey;
  const currentColor = AccentPalettes[accentKey].primary;

  return (
    <>
      <TouchableOpacity
        style={styles.row}
        onPress={() => setOpen(true)}
        activeOpacity={0.7}
      >
        <Text style={styles.rowLabel}>Accent Color</Text>
        <View style={styles.rowRight}>
          <View style={[styles.dot, { backgroundColor: currentColor }]} />
          <Text style={styles.rowValue}>{currentLabel}</Text>
          <PhosphorIcon name="caret-right" size={18} color={Palette.textDisabled} />
        </View>
      </TouchableOpacity>
      <SelectModal
        visible={open}
        title="Accent Color"
        options={ACCENT_OPTIONS}
        selected={accentKey}
        onSelect={(v) => setAccent(v)}
        onClose={() => setOpen(false)}
        dotColor={(v) => AccentPalettes[v].primary}
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

export default function SettingsScreen() {
  const insets = useSafeAreaInsets();
  const { settings, setSetting } = useSettings();

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <ScrollView
        contentContainerStyle={[styles.scroll, { paddingBottom: insets.bottom + Spacing[8] }]}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.title}>Settings</Text>

        {/* ── Appearance ─────────────────────────────────────────── */}
        <SectionHeader title="Appearance" />
        <View style={styles.card}>
          <AccentSelectRow />
        </View>

        {/* ── Closet ─────────────────────────────────────────────── */}
        <SectionHeader title="Closet" />
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Show archived items</Text>
            <Switch
              value={settings.showArchivedItems}
              onValueChange={(v) => setSetting('showArchivedItems', v)}
              trackColor={{ false: Palette.surface3, true: AccentPalettes[settings.accentKey].primary }}
              thumbColor={Palette.white}
            />
          </View>
          <Text style={styles.hint}>
            When off, Sold/Donated/Lost items are hidden from your Closet view.
          </Text>
        </View>

        {/* ── Display ────────────────────────────────────────────── */}
        <SectionHeader title="Display" />
        <View style={styles.card}>
          <SettingSelectRow
            label="Currency"
            options={CURRENCY_OPTIONS}
            selected={settings.currencySymbol}
            onSelect={(v) => setSetting('currencySymbol', v)}
          />
          <SettingSelectRow
            label="Temperature"
            options={TEMP_OPTIONS}
            selected={settings.temperatureUnit}
            onSelect={(v) => setSetting('temperatureUnit', v as 'F' | 'C')}
            last
          />
        </View>

        {/* ── Calendar ────────────────────────────────────────────── */}
        <SectionHeader title="Calendar" />
        <View style={styles.card}>
          <SettingSelectRow
            label="Week starts on"
            options={WEEK_START_OPTIONS}
            selected={settings.weekStartDay}
            onSelect={(v) => setSetting('weekStartDay', v)}
            last
          />
        </View>

        {/* ── About ──────────────────────────────────────────────── */}
        <SectionHeader title="About" />
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>App</Text>
            <Text style={styles.rowValue}>hangr</Text>
          </View>
          <View style={[styles.row, styles.rowBorder]}>
            <Text style={styles.rowLabel}>Storage</Text>
            <Text style={styles.rowValue}>Local · no cloud</Text>
          </View>
          <View style={[styles.row, styles.rowBorder]}>
            <Text style={styles.rowLabel}>Telemetry</Text>
            <Text style={styles.rowValue}>None</Text>
          </View>
        </View>
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// SectionHeader
// ---------------------------------------------------------------------------

function SectionHeader({ title }: { title: string }) {
  return <Text style={styles.sectionHeader}>{title}</Text>;
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
  title: {
    color: Palette.textPrimary,
    fontSize: FontSize['2xl'],
    fontWeight: FontWeight.bold,
    marginBottom: Spacing[5],
  },

  sectionHeader: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    fontWeight: FontWeight.semibold,
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginTop: Spacing[5],
    marginBottom: Spacing[2],
    paddingHorizontal: Spacing[1],
  },

  card: {
    backgroundColor: Palette.surface1,
    borderRadius: Radius.lg,
    borderWidth: 1,
    borderColor: Palette.border,
    overflow: 'hidden',
  },

  // Row: label left, control/value right
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[4],
  },
  rowBorder: {
    borderTopWidth: 1,
    borderTopColor: Palette.borderMuted,
  },
  rowLabel: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    flexShrink: 1,
  },
  rowRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[2],
    flexShrink: 0,
    maxWidth: '55%',
  },
  rowValue: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'right',
    flexShrink: 1,
  },

  hint: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    lineHeight: FontSize.xs * 1.6,
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[3],
  },

  // Color dot (used in accent row + modal options)
  dot: {
    width: 14,
    height: 14,
    borderRadius: Radius.full,
    flexShrink: 0,
  },

  // Select modal (bottom sheet)
  backdrop: {
    flex: 1,
    backgroundColor: Palette.overlay,
  },
  sheet: {
    backgroundColor: Palette.surface1,
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    borderWidth: 1,
    borderColor: Palette.border,
    paddingTop: Spacing[2],
  },
  sheetHandle: {
    width: 36,
    height: 4,
    borderRadius: Radius.full,
    backgroundColor: Palette.border,
    alignSelf: 'center',
    marginBottom: Spacing[3],
  },
  sheetTitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.semibold,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[3],
  },
  sheetOption: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[4],
  },
  sheetOptionBorder: {
    borderBottomWidth: 1,
    borderBottomColor: Palette.borderMuted,
  },
  sheetOptionLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[3],
  },
  sheetOptionText: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
  },
  sheetOptionCheck: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.bold,
  },
});
