/**
 * Extracts dominant colors from a local image URI.
 *
 * Requires react-native-image-colors (native module, dev build only).
 * Until a dev build is set up, this is a no-op stub that always returns [].
 *
 * To activate: `npx expo install react-native-image-colors`, then replace
 * this file with the real implementation below.
 *
 * Real implementation (swap in when ready):
 *
 *   import { getColors } from 'react-native-image-colors';
 *
 *   export async function extractImageColors(imageUri: string): Promise<string[]> {
 *     try {
 *       const result = await getColors(imageUri, { fallback: '#808080', cache: false });
 *       const hexes: string[] = [];
 *       if (result.platform === 'android') {
 *         hexes.push(result.dominant, result.vibrant, result.muted);
 *       } else if (result.platform === 'ios') {
 *         hexes.push(result.primary, result.secondary, result.detail);
 *       } else if (result.platform === 'web') {
 *         hexes.push(result.dominant);
 *       }
 *       return [...new Set(hexes.filter((h) => h && h !== '#808080'))];
 *     } catch {
 *       return [];
 *     }
 *   }
 */
export async function extractImageColors(_imageUri: string): Promise<string[]> {
  return [];
}
