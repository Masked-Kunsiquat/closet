import { PropsWithChildren, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { FontSize, FontWeight, Palette, Spacing } from '@/constants/tokens';
import { PhosphorIcon } from '@/components/PhosphorIcon';

/**
 * Collapsible container that toggles visibility of its children when the header is pressed.
 *
 * @param title - Text displayed in the header row.
 * @param children - Content rendered below the header when expanded.
 * @returns The rendered JSX element for the collapsible component.
 */
export function Collapsible({ children, title }: PropsWithChildren & { title: string }) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <View>
      <TouchableOpacity
        style={styles.heading}
        onPress={() => setIsOpen((v) => !v)}
        activeOpacity={0.8}
        accessibilityRole="button"
        accessibilityState={{ expanded: isOpen }}
      >
        <PhosphorIcon name="caret-up-down" size={14} color={Palette.textSecondary} />
        <Text style={styles.title}>{title}</Text>
      </TouchableOpacity>
      {isOpen && <View style={styles.content}>{children}</View>}
    </View>
  );
}

const styles = StyleSheet.create({
  heading: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[2],
  },
  title: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
  content: {
    marginTop: Spacing[1],
    marginLeft: Spacing[5],
  },
});