/**
 * Settings tab — user preferences.
 *
 * Sections:
 *   Appearance — accent color theme picker
 *   Closet     — show/hide archived items (Sold/Donated/Lost)
 *   Display    — currency symbol, temperature unit (token-ready)
 *   Calendar   — week start day
 */

import { ScrollView, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AccentKey, AccentPalettes, FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { useSettings } from '@/context/SettingsContext';
import { contrastingTextColor } from '@/utils/color';

// ---------------------------------------------------------------------------
// Accent palette picker data
// ---------------------------------------------------------------------------

const ACCENT_OPTIONS: { key: AccentKey; label: string }[] = [
  { key: 'amber',    label: 'Amber'    },
  { key: 'coral',    label: 'Coral'    },
  { key: 'sage',     label: 'Sage'     },
  { key: 'sky',      label: 'Sky'      },
  { key: 'lavender', label: 'Lavender' },
  { key: 'rose',     label: 'Rose'     },
];

// ---------------------------------------------------------------------------
// Option row configs
// ---------------------------------------------------------------------------

const CURRENCY_OPTIONS = [
  { value: '$', label: '$ — Dollar' },
  { value: '£', label: '£ — Pound' },
  { value: '€', label: '€ — Euro' },
  { value: '¥', label: '¥ — Yen / Yuan' },
  { value: '₩', label: '₩ — Won' },
];

const TEMP_OPTIONS = [
  { value: 'F', label: '°F — Fahrenheit' },
  { value: 'C', label: '°C — Celsius' },
];

const WEEK_START_OPTIONS = [
  { value: 0, label: 'Sunday' },
  { value: 1, label: 'Monday' },
];

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function SectionHeader({ title }: { title: string }) {
  return (
    <Text style={styles.sectionHeader}>{title}</Text>
  );
}

function SettingRow({
  label,
  children,
  last = false,
}: {
  label: string;
  children: React.ReactNode;
  last?: boolean;
}) {
  return (
    <View style={[styles.row, !last && styles.rowBorder]}>
      <Text style={styles.rowLabel}>{label}</Text>
      <View style={styles.rowControl}>{children}</View>
    </View>
  );
}

function ChipGroup<T extends string | number>({
  options,
  selected,
  onSelect,
}: {
  options: { value: T; label: string }[];
  selected: T;
  onSelect: (v: T) => void;
}) {
  const { accent } = useAccent();
  return (
    <View style={styles.chipGroup}>
      {options.map((opt) => {
        const active = opt.value === selected;
        return (
          <TouchableOpacity
            key={String(opt.value)}
            style={[styles.chip, active && { backgroundColor: accent.primary, borderColor: accent.primary }]}
            onPress={() => onSelect(opt.value)}
            activeOpacity={0.75}
          >
            <Text
              style={[
                styles.chipText,
                active && { color: contrastingTextColor(accent.primary), fontWeight: FontWeight.semibold },
              ]}
            >
              {opt.label}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Accent swatch picker
// ---------------------------------------------------------------------------

function AccentPicker() {
  const { accentKey, setAccent } = useAccent();

  return (
    <View style={styles.swatchRow}>
      {ACCENT_OPTIONS.map((opt) => {
        const palette = AccentPalettes[opt.key];
        const active = opt.key === accentKey;
        return (
          <TouchableOpacity
            key={opt.key}
            onPress={() => setAccent(opt.key)}
            activeOpacity={0.8}
            accessibilityRole="radio"
            accessibilityLabel={opt.label}
            accessibilityState={{ selected: active }}
            style={styles.swatchWrapper}
          >
            <View
              style={[
                styles.swatch,
                { backgroundColor: palette.primary },
                active && styles.swatchActive,
              ]}
            />
            <Text style={[styles.swatchLabel, active && { color: palette.primary }]}>
              {opt.label}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
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
          <Text style={styles.accentLabel}>Accent Color</Text>
          <AccentPicker />
        </View>

        {/* ── Closet ─────────────────────────────────────────────── */}
        <SectionHeader title="Closet" />
        <View style={styles.card}>
          <SettingRow label="Show archived items" last>
            <Switch
              value={settings.showArchivedItems}
              onValueChange={(v) => setSetting('showArchivedItems', v)}
              trackColor={{ false: Palette.surface3, true: AccentPalettes[settings.accentKey].primary }}
              thumbColor={Palette.white}
            />
          </SettingRow>
          <Text style={styles.hint}>
            When off, items marked Sold, Donated, or Lost are hidden from your Closet view.
            You can still filter for them explicitly.
          </Text>
        </View>

        {/* ── Display ────────────────────────────────────────────── */}
        <SectionHeader title="Display" />
        <View style={styles.card}>
          <View style={styles.stackRow}>
            <Text style={styles.rowLabel}>Currency</Text>
            <ChipGroup
              options={CURRENCY_OPTIONS}
              selected={settings.currencySymbol}
              onSelect={(v) => setSetting('currencySymbol', v)}
            />
          </View>
          <View style={[styles.stackRow, styles.stackRowBorder]}>
            <Text style={styles.rowLabel}>Temperature</Text>
            <ChipGroup
              options={TEMP_OPTIONS}
              selected={settings.temperatureUnit}
              onSelect={(v) => setSetting('temperatureUnit', v as 'F' | 'C')}
            />
          </View>
        </View>

        {/* ── Calendar ────────────────────────────────────────────── */}
        <SectionHeader title="Calendar" />
        <View style={styles.card}>
          <View style={styles.stackRow}>
            <Text style={styles.rowLabel}>Week starts on</Text>
            <ChipGroup
              options={WEEK_START_OPTIONS}
              selected={settings.weekStartDay}
              onSelect={(v) => setSetting('weekStartDay', v as 0 | 1)}
            />
          </View>
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

  // Section header
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

  // Card container
  card: {
    backgroundColor: Palette.surface1,
    borderRadius: Radius.lg,
    borderWidth: 1,
    borderColor: Palette.border,
    overflow: 'hidden',
  },

  // Standard row (label + right-side control)
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
  },
  rowControl: {
    flexShrink: 0,
  },
  rowValue: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },

  // Stack row: label on top, chips below (for wider chip groups)
  stackRow: {
    paddingHorizontal: Spacing[4],
    paddingTop: Spacing[4],
    paddingBottom: Spacing[3],
    gap: Spacing[2],
  },
  stackRowBorder: {
    borderTopWidth: 1,
    borderTopColor: Palette.borderMuted,
  },

  // Hint text below a row
  hint: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    lineHeight: FontSize.xs * 1.6,
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[3],
  },

  // Accent swatch grid
  accentLabel: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    paddingHorizontal: Spacing[4],
    paddingTop: Spacing[4],
    paddingBottom: Spacing[3],
  },
  swatchRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[4],
    gap: Spacing[3],
  },
  swatchWrapper: {
    alignItems: 'center',
    gap: Spacing[1],
    minWidth: 48,
  },
  swatch: {
    width: 36,
    height: 36,
    borderRadius: Radius.full,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  swatchActive: {
    borderColor: Palette.white,
    transform: [{ scale: 1.15 }],
  },
  swatchLabel: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
    textAlign: 'center',
  },

  // Chip row
  chipGroup: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing[2],
  },
  chip: {
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface3,
  },
  chipText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },
});
