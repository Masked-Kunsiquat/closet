import { Paths } from 'expo-file-system';

/**
 * Reconstruct a displayable URI from a stored image path.
 *
 * Stored paths are always relative (e.g. "images/1234567890.jpg").
 * At read time, prepend documentDirectory to get a valid file:// URI.
 *
 * Handles legacy absolute file:// URIs gracefully so existing data
 * is not broken during any transition.
 */
export function toImageUri(imagePath: string | null | undefined): string | null {
  if (!imagePath) return null;
  if (imagePath.startsWith('file://') || imagePath.startsWith('http')) {
    return imagePath;
  }
  const base = Paths.document.uri.replace(/\/+$/, '');
  const relative = imagePath.replace(/^\/+/, '');
  return `${base}/${relative}`;
}
