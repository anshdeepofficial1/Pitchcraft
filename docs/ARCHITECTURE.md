# Pitchcraft architecture

Android MediaStore is the source of truth for originals.

Pitchcraft stores only references and derived local metadata:
- MediaStore IDs and content URIs
- favorites
- face boxes / thumbnails
- MobileFaceNet embeddings
- People cluster centroids and names
- scan state

Face pipeline:
MediaStore image -> memory-safe working bitmap -> ML Kit face detection -> aligned face crop -> MobileFaceNet embedding -> L2 normalization -> local cosine clustering -> People.

The grouping step does not require a separate neural model. It compares face embeddings locally. This keeps the stack small and avoids unrelated AI models.

The People worker runs as a WorkManager foreground job, skips images already indexed, persists progress, and can resume later.
