# Pitchcraft

Pitchcraft is a local-first Android gallery rebuilt from scratch.

The original photos and videos remain in Android MediaStore. Pitchcraft does not create a second photo vault and does not upload originals to a backend.

## Included in the first test build

- Premium white and blue gallery UI inspired by the supplied reference screens.
- Photos timeline with date sections and recent Moments.
- Folder albums, Favorites, Videos, Screenshots, Downloads and Camera collections.
- Explore and local search.
- Full-screen image viewer and in-app video playback.
- Share, Favorite, system-editor handoff, safe move-to-trash and file details.
- Local People index with visible progress.
- Local face detection with ML Kit.
- Local identity embeddings with MobileFaceNet through ONNX Runtime.
- Local face grouping by cosine distance.
- Rename, merge and hide People groups.
- Incremental scanning: photos already indexed are skipped.
- Manual rebuild of the complete face index.

## Privacy

No image-generation model is included.
No photo is sent to Pitchcraft servers.
The only network request used by People indexing is the one-time download of the MobileFaceNet model. The model is SHA-256 verified and cached in app-private storage; all photo inference happens locally afterward.

## Build

GitHub Actions produces a debug APK on every push to main.

For local Android Studio builds use JDK 17, Android SDK 36 and Gradle 8.13.
