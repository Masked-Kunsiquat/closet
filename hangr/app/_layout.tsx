import { DarkTheme, ThemeProvider } from '@react-navigation/native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import 'react-native-reanimated';

import { AccentProvider } from '@/context/AccentContext';
import { getDatabase } from '@/db';

export const unstable_settings = {
  anchor: '(tabs)',
};

export default function RootLayout() {
  const [dbReady, setDbReady] = useState(false);
  const [dbError, setDbError] = useState<string | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  const initDb = useCallback(() => {
    setDbError(null);
    setIsInitializing(true);
    getDatabase()
      .then(() => { setDbReady(true); setIsInitializing(false); })
      .catch((e) => { setDbError(String(e)); setIsInitializing(false); });
  }, []);

  useEffect(() => { initDb(); }, [initDb]);

  // Don't render any screens until the DB is initialized and seeded.
  if (dbError) {
    return (
      <View style={errStyles.container}>
        <Text style={errStyles.title}>Failed to open database</Text>
        <Text style={errStyles.detail}>{dbError}</Text>
        <TouchableOpacity style={errStyles.button} onPress={initDb}>
          <Text style={errStyles.buttonText}>Retry</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (isInitializing || !dbReady) return null;

  return (
    <AccentProvider>
      <ThemeProvider value={DarkTheme}>
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="item/add" />
          <Stack.Screen name="item/[id]" />
          <Stack.Screen name="item/[id]/edit" />
          <Stack.Screen name="outfit/new" />
          <Stack.Screen name="outfit/[id]" />
          <Stack.Screen name="log/[date]" />
        </Stack>
        <StatusBar style="light" />
      </ThemeProvider>
    </AccentProvider>
  );
}

const errStyles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
    gap: 16,
  },
  title: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
    textAlign: 'center',
  },
  detail: {
    color: '#999',
    fontSize: 13,
    textAlign: 'center',
  },
  button: {
    marginTop: 8,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    backgroundColor: '#F59E0B',
  },
  buttonText: {
    color: '#000',
    fontSize: 15,
    fontWeight: '600',
  },
});
