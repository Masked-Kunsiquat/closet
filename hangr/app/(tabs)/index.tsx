import { StyleSheet, Text, View } from 'react-native';

import { Palette } from '@/constants/tokens';

export default function ClosetScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>hangr</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.surface0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: Palette.textSecondary,
    fontSize: 15,
  },
});
