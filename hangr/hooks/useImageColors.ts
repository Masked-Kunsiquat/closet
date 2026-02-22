/**
 * Extracts dominant colors from a local image URI using react-native-image-colors.
 *
 * Returns an array of hex strings (e.g. ["#1B2A4A", "#C8A97A"]) representing
 * the most prominent colors in the image, or an empty array if extraction fails
 * or the native module is unavailable (e.g. Expo Go).
 *
 * Requires a dev build — react-native-image-colors has native code and will not
 * run in Expo Go. The hook fails gracefully so the rest of the form is unaffected.
 */
export async function extractImageColors(imageUri: string): Promise<string[]> {
  try {
    // Dynamic require so the module being absent (Expo Go) doesn't crash at import time.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { getColors } = require('react-native-image-colors') as typeof import('react-native-image-colors');
    const result = await getColors(imageUri, { fallback: '#808080', cache: false });

    const hexes: string[] = [];
    if (result.platform === 'android') {
      hexes.push(result.dominant, result.vibrant, result.muted);
    } else if (result.platform === 'ios') {
      hexes.push(result.primary, result.secondary, result.detail);
    } else if (result.platform === 'web') {
      hexes.push(result.dominant);
    }

    // Filter out fallback grey and deduplicate
    return [...new Set(hexes.filter((h) => h && h !== '#808080'))];
  } catch {
    // Module not installed or native extraction failed — silently no-op
    return [];
  }
}
