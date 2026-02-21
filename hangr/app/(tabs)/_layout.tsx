import { Tabs } from 'expo-router';

import { HapticTab } from '@/components/haptic-tab';
import { PhosphorIcon } from '@/components/PhosphorIcon';
import { Palette } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';

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
            <PhosphorIcon name="dot" size={26} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}
