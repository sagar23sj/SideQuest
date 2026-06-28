# Default bucket cover photos

The `app/src/main/res/drawable-nodpi/bucket_photo_*.jpg` files are the bundled
default cover photos shown for buckets whose name matches a known domain
(travel, cooking, shopping, wellness, learning, home, art, finance, coffee,
movies) plus a generic `default`. They ship in the APK, so covers are topical,
high-quality, and work fully offline.

## Source & licensing
Fetched during build prep from:
- **Unsplash** (https://unsplash.com) — the [Unsplash License](https://unsplash.com/license)
  permits free use, including commercially, with no attribution required.
- A few may fall back to **LoremFlickr** (https://loremflickr.com), which serves
  Creative Commons Flickr photos; CC-BY images require attribution.

## Before a commercial / Play Store release
To keep licensing unambiguous, replace any LoremFlickr-sourced files with images
you own or that are clearly under the Unsplash License (or another license that
allows redistribution without attribution). Swapping is trivial: overwrite the
`bucket_photo_<domain>.jpg` file with a new JPEG of the same name — no code
change needed (mapping lives in `coverDrawableFor` in
`app/src/main/kotlin/com/sidequest/ui/board/BucketVisuals.kt`).
