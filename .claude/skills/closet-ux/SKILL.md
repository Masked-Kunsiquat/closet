---
name: closet-ux
description: UX and UI review for the Closet app. Use when designing screens, building components, reviewing layouts, choosing interactions, or making any visual or UX decision.
allowed-tools: Read, Grep, Glob
---

# Closet — UX & UI Standards

## App Personality
Personal, calm, well-crafted. This is a wardrobe app — it should feel like opening a well-organized closet, not a spreadsheet. Clean over clever. Functional over decorative.

## Theming
- **Material 3, dark-first palette**
- Dynamic color (Material You) enabled on Android 12+
- Accent palettes: Amber (default), Coral, Sage, Sky, Lavender, Rose — user-selectable
- All color/theme tokens in `core/ui/theme/` — never hardcode colors, always use the theme
- Consistent spacing — don't freehand margins/padding

## Typography
- Use Material 3 type scale (`MaterialTheme.typography.*`) — never hardcode font sizes
- Clear hierarchy: `titleLarge` → `titleMedium` → `bodyMedium` → `bodySmall` → `labelSmall`
- Labels on form fields, not just placeholders (placeholders disappear on input)

## Navigation & Screens
Bottom navigation with 5 tabs (Chat tab hidden when AI is disabled):
- **Closet** — gallery/grid + list view, filterable/sortable
- **Outfits** — outfit list, outfit builder, OOTD journal/calendar
- **Stats** — analytics dashboard
- **Recommendations** — outfit suggestions
- **Chat** — RAG wardrobe chat (AI-enabled only)

Additional screens reached via navigation: item detail, add/edit item form, settings, AI settings, backup/restore.

## Component Standards
- Every list/grid must have an **empty state** — never show a blank screen
- Loading states: skeletons preferred over spinners for content-heavy screens
- Destructive actions (delete, status change to Sold/Donated/Lost) require `AlertDialog` confirmation
- Forms: validate inline where possible, not only on submit
- Images: always show a placeholder (`Icons.Default.Checkroom`) when image is missing or loading

## Closet Gallery
- Grid is default, list is secondary
- Filter/sort controls accessible without deep navigation
- Items marked as favourite get a subtle indicator — not loud
- Status badges for non-Active items (Sold, Donated, Lost)

## Item Card (Grid)
- Image dominant
- Item name visible, truncated gracefully
- Subtle wear count or favourite indicator acceptable

## Calendar / Journal View
- Chips/counters per date showing outfit count
- Tap date → outfit detail sheet for that day
- OOTD visually distinguished
- Empty days look intentionally empty, not broken

## Stats Dashboard
- Vico column charts for wear breakdowns
- Time range filter: All Time / This Month / Last 3 Months / This Year
- Most/least/never worn lists
- Breakdowns by category, colour, occasion

## Chat Screen
- Scrolling message list, auto-scrolls to latest
- `OutfitMiniCard` for outfit suggestions (2×2 image grid + item names + reason + action row)
- Item rail (`LazyRow` of `ItemChip`) for item-reference responses
- Welcome screen with suggestion chips when conversation is empty
- "Index building" notice banner when embedding index isn't ready

## Settings Screen
- Sections grouped into cards: Appearance, Wardrobe, AI, Backup, About
- Single-value picker settings use a bottom sheet or dialog — no chip groups for selection
- `Switch` only for true boolean toggles
- Accent colour row: show colour dot next to current value

## Micro-interactions & Polish
- Haptics on meaningful actions (save, log outfit, mark OOTD)
- Transitions intentional — not instant, not sluggish
- Icons from `Icons.Default.*` or `Icons.Outlined.*` (Material Icons) — no custom icon strings

## Things to Avoid
- Overloading any single screen with too many actions
- Hiding primary actions in menus when they're used frequently
- Hardcoded colors — always use `MaterialTheme.colorScheme.*`
- Showing raw database IDs or technical strings to the user
- `AlertDialog` for non-destructive actions
- Placeholder-only form fields