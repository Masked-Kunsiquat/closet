# demo-backup

A dev-only command-line tool that builds a demo-wardrobe `.hangr` backup from the Kaggle
"Fashion Product Images (Small)" dataset. Restore the file through the app's normal Backup &
Restore to get a realistic closet for screenshots and testing. Nothing here ships in the app.

It builds the database with the app's real Room setup (`core/data`), inserts items through
`ClothingRepository` (so `semantic_description` and the colour/season/occasion junctions are
correct), and writes the same archive layout `BackupRepository` produces:
`closet.db`, a flat `images/`, and `manifest.json` (no `prefs/`, no API keys). Item embeddings
are left empty; the app regenerates them after a restore.

## Get the dataset

1. Sign in to Kaggle and download "Fashion Product Images (Small)" (author: paramaggarwal).
2. Unzip it somewhere outside this repository, for example `~/Datasets/fashion-product-images-small/`,
   so that it contains `styles.csv` and `images/<id>.jpg`.

Do not commit the dataset, any of its images, or a generated `.hangr`. The rights to the product
photos are unclear, so they stay on your machine.

## Run

From `app-android/`:

```
./gradlew :tools:demo-backup:run --args="--dataset <dir> --out <file.hangr> [--count 300] [--seed 42] [--no-history]"
```

Use absolute paths; Gradle runs the tool from `tools/demo-backup/`. On Windows, forward slashes
(`C:/Users/you/...`) avoid quoting trouble.

| Flag | Meaning |
| --- | --- |
| `--dataset` | Directory with `styles.csv` and `images/` |
| `--out` | The `.hangr` file to write |
| `--count` | Items in the closet (default 300) |
| `--seed` | Seed for sampling and history; the same seed gives the same closet (default 42) |
| `--no-history` | Skip the demo outfits and wear logs |

Items are sampled per category (at least 10 per category where available), then filled in
proportion to the pool. With history on, you also get about 25 outfits and about 120 wear logs
over the last 180 days, at most one outfit-of-the-day per date. Boys and Girls rows, article types
with no matching app category, and rows without a photo are skipped; the summary lists each count.

About 65% of items also get details, so price, cost-per-wear, brand and filter screens have data:

- a price ending in .99, in a per-category range;
- a purchase date 180 days to 2 years back, so it falls before the wear history;
- one of 10 invented brands (about 80% of those items), so some brands can't be deleted;
- one or two category-appropriate materials (jeans are always denim);
- a pattern taken from the product name ("Check" → Checkered) or mostly Solid;
- some favorites (about 12%), some dirty (about 15%), and a few retired: Donated or Sold.

The other ~35% stay bare, so the add/edit form still has empty fields to fill in by hand.

## Restore it in the app

Copy the `.hangr` to the device, then open Settings, Backup & Restore, Restore, and pick the file.
Restore replaces the current wardrobe, so use a test install or take a backup first.

## Tests

`./gradlew :tools:demo-backup:test` (also part of `./gradlew test`). The tests use a tiny
generated fixture, not the real dataset, and pin the manifest field names against the app's
`BackupManifest`.
