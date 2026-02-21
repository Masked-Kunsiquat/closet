import { DarkTheme, ThemeProvider } from '@react-navigation/native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import 'react-native-reanimated';

import { AccentProvider } from '@/context/AccentContext';
import { getDatabase } from '@/db';

export const unstable_settings = {
  anchor: '(tabs)',
};

export default function RootLayout() {
  const [dbReady, setDbReady] = useState(false);

  useEffect(() => {
    getDatabase().then(() => setDbReady(true));
  }, []);

  // Don't render any screens until the DB is initialized and seeded.
  if (!dbReady) return null;

  return (
    <AccentProvider>
      <ThemeProvider value={DarkTheme}>
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="item/add" />
          <Stack.Screen name="item/[id]" />
          <Stack.Screen name="item/[id]/edit" />
        </Stack>
        <StatusBar style="light" />
      </ThemeProvider>
    </AccentProvider>
  );
}
