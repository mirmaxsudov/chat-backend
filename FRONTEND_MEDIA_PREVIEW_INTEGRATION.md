# Frontend Media Preview Integration

The backend keeps every original image and video unchanged. It asynchronously creates one smaller preview image:

- `IMAGE`: resized and compressed image preview.
- `VIDEO`: resized and compressed poster frame only. The video itself is not transcoded.
- Other attachment types: no preview.

Large source images are decoded with subsampling, so the JVM does not allocate the full-resolution raster just to create a small preview. A configurable `MEDIA_PREVIEW_MAX_SOURCE_PIXELS` hard limit (250 million pixels by default) remains as decompression-bomb protection.

## API contract

Message attachment payloads contain both the existing original URL and the preview state:

```ts
type PreviewStatus =
  | "NOT_APPLICABLE"
  | "PENDING"
  | "PROCESSING"
  | "READY"
  | "FAILED";

type AttachmentPreview = {
  status: PreviewStatus;
  url: string | null;
  contentType: "image/jpeg" | "image/png" | null;
  sizeBytes: number | null;
  width: number | null;
  height: number | null;
};

type AttachmentMessage = {
  name: string;
  contentType: string;
  sizeBytes: number;
  publicURL: string; // Original image or video.
  type: "IMAGE" | "VIDEO" | "AUDIO" | "PDF" | "EXCEL" | "PPT" | "OTHERS";
  thumbnailURL: string | null; // Compatibility alias for preview.url.
  preview: AttachmentPreview | null;
};
```

Example ready image:

```json
{
  "name": "photo.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 4821451,
  "publicURL": "http://localhost:8080/api/v1/attachment/ATTACHMENT_ID",
  "type": "IMAGE",
  "thumbnailURL": "http://localhost:8080/api/v1/attachment/ATTACHMENT_ID/preview",
  "preview": {
    "status": "READY",
    "url": "http://localhost:8080/api/v1/attachment/ATTACHMENT_ID/preview",
    "contentType": "image/jpeg",
    "sizeBytes": 84210,
    "width": 640,
    "height": 427
  }
}
```

The preview object is `null` for non-image/video attachments. While generation is incomplete, its status is `PENDING` or `PROCESSING` and its URL is `null`.

## Image rendering flow

For copy-ready React/TypeScript code, use [FRONTEND_SCROLL_PREVIEW_SWAP_IMPLEMENTATION.md](./FRONTEND_SCROLL_PREVIEW_SWAP_IMPLEMENTATION.md).

1. Reserve space using `preview.width` and `preview.height` to avoid layout shift.
2. Load `preview.url` when its status is `READY`.
3. Use `IntersectionObserver` with approximately `600px` root margin.
4. When the image approaches the viewport, start loading `publicURL` into a separate `Image` object.
5. Call `await image.decode()`.
6. Swap to the original only after decoding succeeds.
7. Keep the preview when the original fails.

```ts
async function preloadOriginal(url: string): Promise<void> {
  const image = new Image();
  image.src = url;
  await image.decode();
}
```

Use a short opacity transition when replacing the preview. Do not download every original image when the chat initially renders.

## Video rendering flow

Use the preview as the video poster and avoid preloading the full video:

```tsx
<video
  src={attachment.publicURL}
  poster={attachment.preview?.status === "READY" ? attachment.preview.url ?? undefined : undefined}
  preload="none"
  controls
/>
```

Load video content only when the user plays it or when your existing player intentionally preloads visible media.

## Pending previews

Preview generation happens after upload completion. A message can therefore briefly contain `PENDING` or `PROCESSING`.

For visible attachments only, refetch the chat/message using bounded backoff:

```text
1 second → 2 seconds → 5 seconds → stop after 30 seconds
```

While waiting, render an aspect-ratio placeholder. If the status becomes `FAILED`, stop polling and lazy-load the original only when visible.

## Preview endpoint

The preferred endpoint is:

```text
GET /api/v1/attachments/{attachmentId}/preview
HEAD /api/v1/attachments/{attachmentId}/preview
```

The older `/thumbnail` path remains available as an alias. Ready preview responses are immutable-cacheable for one year.
