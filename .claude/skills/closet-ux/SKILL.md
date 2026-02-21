---
name: closet-ux
description: UX and UI review for the Closet app. Use when designing screens, building components, reviewing layouts, choosing interactions, or making any visual or UX decision.
allowed-tools: Read, Grep, Glob
---

# Closet — UX & UI Standards

## App Personality
Personal, calm, well-crafted. This is a wardrobe app — it should feel like opening a well-organized closet, not a spreadsheet. Clean over clever. Functional over decorative.

## Theming
- **Dark mode by default**
- Design tokens for all colors, spacing, typography — never hardcode values
- Accent color themes: coral, ocean, etc. (user-selectable)
- Consistent spacing scale — don't freehand margins/padding

## Typography
- Clear hierarchy: screen title → section header → body → label → caption
- Never mix font weights randomly — use the scale intentionally
- Labels on form fields, not just placeholders (placeholders disappear on input)

## Navigation & Screens
Core screens:
- **Closet** — gallery/grid + list view, filterable/sortable
- **Item Detail** — full item view with wear history
- **Outfit Builder** — multi-item select, optional name, save
- **Outfit Log / Journal** — calendar view with chips per date → tap → outfit grid
- **Stats Dashboard** — analytics
- **Settings** — theme, units, preferences

## Component Standards
- Every list/grid must have an **empty state** — never show a blank screen
- Loading states must be accounted for — skeletons preferred over spinners for content
- Destructive actions (delete, status change to Sold/Donated/Lost) require confirmation
- Forms: validate inline, not just on submit
- Images: always show a placeholder when image is missing or loading

## Closet Gallery
- Grid is default, list is secondary
- Filter/sort controls should be accessible without deep navigation
- Items marked as favorite (`is_favorite`) get a subtle indicator — not loud

## Item Card (Grid)
- Image dominant
- Item name visible
- Subtle wear count or favorite indicator acceptable
- Status badges for non-Active items (Sold, Donated, Lost)

## Calendar / Journal View
- Chips/counters per date showing outfit count
- Tap date → outfit image grid for that day
- OOTD should be visually distinguished
- Empty days should look intentionally empty, not broken

## Stats Dashboard
- Pie/donut charts for composition breakdowns (color, category, brand, etc.)
- Time range filter: All Time / Past Month / Past 3 Months / Past Year
- Most/least/never worn lists (top 10–15)
- "X% of closet worn" as a headline stat
- Closet total value with category breakdown

## Micro-interactions & Polish
- Swipe actions on list items are fine but must have visible affordances
- Icon + label on nav tabs — icons alone are ambiguous
- Haptics on meaningful actions (save, log outfit, mark OOTD)
- Transitions should feel intentional — not instant, not sluggish

## Things to Avoid
- Overloading any single screen with too many actions
- Hiding primary actions in menus when they're used frequently
- Inconsistent spacing or random font sizes
- Showing raw database IDs or technical strings to the user
- Alert dialogs for non-destructive actions
- Placeholder-only form fields