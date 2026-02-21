import { Tabs } from 'expo-router';

import { HapticTab } from '@/components/haptic-tab';
import { PhosphorIcon } from '@/components/PhosphorIcon';
import { Palette } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';

/**
 * Renders the app's bottom tab navigator with Closet, Outfits, and Journal tabs.
 *
 * The navigator uses the current accent color for the active tab tint, applies
 * app surface and border colors for the tab bar, hides screen headers, and
 * substitutes tab buttons with the HapticTab component. Each tab displays a
 * PhosphorIcon: "t-shirt" for Closet, "coat-hanger" for Outfits, and
 * "calendar-dots" for Journal.
 *
 * @returns A configured Tabs navigator containing the three tab screens.
 */
export default function TabLayout() {
  const { accent } = useAccent();

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: accent.primary,
        tabBarInactiveTintColor: Palette.textSecondary,
        tabBarStyle: { backgroundColor: Palette.surface1, borderTopColor: Palette.border },
        headerShown: false,
        tabBarButton: HapticTab,
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Closet',
          tabBarIcon: ({ color }) => (
            <PhosphorIcon name="t-shirt" size={26} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="outfits"
        options={{
          title: 'Outfits',
          tabBarIcon: ({ color }) => (
            <PhosphorIcon name="coat-hanger" size={26} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="journal"
        options={{
          title: 'Journal',
          tabBarIcon: ({ color }) => (
            <PhosphorIcon name="calendar-dots" size={26} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}