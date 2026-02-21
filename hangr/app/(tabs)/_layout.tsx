import { Tabs } from 'expo-router';

import { HapticTab } from '@/components/haptic-tab';
import { IconSymbol } from '@/components/ui/icon-symbol';
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
            <IconSymbol size={28} name="tshirt" color={color} />
          ),
        }}
      />
    </Tabs>
  );
}
