# Scroll-Based Media Preview Swap

This document is the implementation contract for the frontend agent. The examples use React and TypeScript and are designed to be copied into the frontend repository as reusable modules.

## Required behavior

For image attachments:

1. Render the backend-generated preview first.
2. Reserve the final aspect ratio before either image loads.
3. Observe the media container with one shared `IntersectionObserver` pool.
4. When the image is within `800px` of the viewport, queue the original image for loading.
5. Allow no more than three original images to load concurrently.
6. Decode the original before changing the visible `src`.
7. Swap to the original with a short opacity transition.
8. Keep or restore the preview if the original fails.

For video attachments, do not treat the original video as an image. Use the preview as the `<video poster>` and keep `preload="none"`.

Suggested file structure:

```text
src/features/attachments/media/
  attachment-media.types.ts
  image-load-queue.ts
  viewport-observer.ts
  use-progressive-image.ts
  ProgressiveAttachmentImage.tsx
  ProgressiveAttachmentImage.module.css
  AttachmentMedia.tsx
```

## 1. API types

Create `attachment-media.types.ts`:

```ts
export type PreviewStatus =
  | "NOT_APPLICABLE"
  | "PENDING"
  | "PROCESSING"
  | "READY"
  | "FAILED";

export type AttachmentType =
  | "IMAGE"
  | "VIDEO"
  | "AUDIO"
  | "PDF"
  | "EXCEL"
  | "PPT"
  | "OTHERS";

export interface AttachmentPreview {
  status: PreviewStatus;
  url: string | null;
  contentType: "image/jpeg" | "image/png" | null;
  sizeBytes: number | null;
  width: number | null;
  height: number | null;
}

export interface AttachmentMediaModel {
  name: string;
  contentType: string;
  sizeBytes: number;
  publicURL: string;
  type: AttachmentType;
  thumbnailURL: string | null;
  preview: AttachmentPreview | null;
}
```

## 2. Shared viewport observer

Do not create one `IntersectionObserver` per chat message. Create `viewport-observer.ts`:

```ts
type NearViewportCallback = () => void;

interface ObserverPool {
  observer: IntersectionObserver;
  callbacks: Map<Element, NearViewportCallback>;
}

const pools = new Map<string, ObserverPool>();

function createPool(rootMargin: string): ObserverPool {
  const callbacks = new Map<Element, NearViewportCallback>();

  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;

        const callback = callbacks.get(entry.target);
        observer.unobserve(entry.target);
        callbacks.delete(entry.target);
        callback?.();
      }

      if (callbacks.size === 0) {
        observer.disconnect();
        pools.delete(rootMargin);
      }
    },
    {
      root: null,
      rootMargin,
      threshold: 0.01,
    },
  );

  return { observer, callbacks };
}

export function observeNearViewport(
  element: Element,
  callback: NearViewportCallback,
  rootMargin = "800px 0px",
): () => void {
  // SSR, tests, and older browsers should load normally.
  if (typeof IntersectionObserver === "undefined") {
    callback();
    return () => undefined;
  }

  const pool = pools.get(rootMargin) ?? createPool(rootMargin);
  pools.set(rootMargin, pool);
  pool.callbacks.set(element, callback);
  pool.observer.observe(element);

  return () => {
    pool.observer.unobserve(element);
    pool.callbacks.delete(element);

    if (pool.callbacks.size === 0) {
      pool.observer.disconnect();
      pools.delete(rootMargin);
    }
  };
}
```

`800px` starts loading slightly before the user reaches the image. Reduce it to `400px` for slower networks or increase it to `1200px` for very fast scrolling.

## 3. Deduplicated image load queue

Loading every original image at the same time can saturate the browser connection. Create `image-load-queue.ts`:

```ts
const MAX_CONCURRENT_LOADS = 3;

const cache = new Map<string, Promise<void>>();
const pending: Array<() => void> = [];
let activeLoads = 0;

function decodeImage(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.decoding = "async";

    image.onload = () => {
      if (typeof image.decode !== "function") {
        resolve();
        return;
      }

      // Some browsers report a successful load but reject decode(). In that
      // case, assigning the already-loaded URL is still safe.
      image.decode().then(resolve, resolve);
    };

    image.onerror = () => {
      reject(new Error(`Could not load attachment image: ${url}`));
    };

    image.src = url;
  });
}

function pumpQueue(): void {
  while (activeLoads < MAX_CONCURRENT_LOADS && pending.length > 0) {
    activeLoads += 1;
    pending.shift()?.();
  }
}

function enqueue(url: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    pending.push(() => {
      decodeImage(url)
        .then(resolve, reject)
        .finally(() => {
          activeLoads -= 1;
          pumpQueue();
        });
    });

    pumpQueue();
  });
}

export function preloadOriginalImage(url: string): Promise<void> {
  const cached = cache.get(url);
  if (cached) return cached;

  const request = enqueue(url);
  cache.set(url, request);

  // Failed URLs may be retried if the component is mounted again later.
  void request.catch(() => cache.delete(url));
  return request;
}
```

This cache deduplicates requests when the same attachment is rendered more than once, such as in a message list and a reply preview.

## 4. Reusable progressive-image hook

Create `use-progressive-image.ts`:

```ts
import { useCallback, useEffect, useState } from "react";

import { preloadOriginalImage } from "./image-load-queue";
import { observeNearViewport } from "./viewport-observer";

export type ProgressiveImagePhase =
  | "placeholder"
  | "preview"
  | "loading-original"
  | "original"
  | "error";

interface UseProgressiveImageOptions {
  previewUrl: string | null;
  originalUrl: string;
  rootMargin?: string;
}

interface UseProgressiveImageResult {
  containerRef: (node: HTMLElement | null) => void;
  sourceUrl: string | null;
  phase: ProgressiveImagePhase;
  onImageError: () => void;
}

export function useProgressiveImage({
  previewUrl,
  originalUrl,
  rootMargin = "800px 0px",
}: UseProgressiveImageOptions): UseProgressiveImageResult {
  const [container, setContainer] = useState<HTMLElement | null>(null);
  const [isNearViewport, setIsNearViewport] = useState(false);
  const [sourceUrl, setSourceUrl] = useState<string | null>(previewUrl);
  const [phase, setPhase] = useState<ProgressiveImagePhase>(
    previewUrl ? "preview" : "placeholder",
  );

  useEffect(() => {
    setSourceUrl(previewUrl);
    setPhase(previewUrl ? "preview" : "placeholder");
  }, [originalUrl, previewUrl]);

  useEffect(() => {
    if (!container || isNearViewport) return;

    return observeNearViewport(
      container,
      () => setIsNearViewport(true),
      rootMargin,
    );
  }, [container, isNearViewport, rootMargin]);

  useEffect(() => {
    if (!isNearViewport || !originalUrl) return;

    if (originalUrl === previewUrl) {
      setSourceUrl(originalUrl);
      setPhase("original");
      return;
    }

    let disposed = false;
    setPhase("loading-original");

    void preloadOriginalImage(originalUrl).then(
      () => {
        if (disposed) return;
        setSourceUrl(originalUrl);
        setPhase("original");
      },
      () => {
        if (disposed) return;
        setSourceUrl(previewUrl);
        setPhase(previewUrl ? "preview" : "error");
      },
    );

    return () => {
      disposed = true;
    };
  }, [isNearViewport, originalUrl, previewUrl]);

  const onImageError = useCallback(() => {
    if (sourceUrl === originalUrl && previewUrl) {
      setSourceUrl(previewUrl);
      setPhase("preview");
      return;
    }

    setSourceUrl(null);
    setPhase("error");
  }, [originalUrl, previewUrl, sourceUrl]);

  return {
    containerRef: setContainer,
    sourceUrl,
    phase,
    onImageError,
  };
}
```

The cleanup flag prevents an image that finished loading after unmount from changing React state.

## 5. Reusable image component

Create `ProgressiveAttachmentImage.tsx`:

```tsx
import type { CSSProperties } from "react";

import { useProgressiveImage } from "./use-progressive-image";
import styles from "./ProgressiveAttachmentImage.module.css";

interface ProgressiveAttachmentImageProps {
  previewUrl: string | null;
  originalUrl: string;
  previewWidth?: number | null;
  previewHeight?: number | null;
  alt: string;
  className?: string;
  rootMargin?: string;
  onClick?: () => void;
}

export function ProgressiveAttachmentImage({
  previewUrl,
  originalUrl,
  previewWidth,
  previewHeight,
  alt,
  className,
  rootMargin,
  onClick,
}: ProgressiveAttachmentImageProps) {
  const { containerRef, sourceUrl, phase, onImageError } =
    useProgressiveImage({ previewUrl, originalUrl, rootMargin });

  const aspectRatio =
    previewWidth && previewHeight
      ? `${previewWidth} / ${previewHeight}`
      : "4 / 3";

  const frameStyle = { aspectRatio } satisfies CSSProperties;
  const frameClassName = [styles.frame, className]
    .filter(Boolean)
    .join(" ");

  return (
    <figure
      ref={containerRef}
      className={frameClassName}
      style={frameStyle}
      data-phase={phase}
      aria-busy={phase === "loading-original"}
    >
      {sourceUrl ? (
        <img
          className={styles.image}
          src={sourceUrl}
          alt={alt}
          decoding="async"
          loading="lazy"
          onError={onImageError}
          onClick={onClick}
        />
      ) : (
        <div className={styles.placeholder} aria-label={`Loading ${alt}`} />
      )}

      {phase === "loading-original" && (
        <span className={styles.qualityBadge} aria-hidden="true">
          HD
        </span>
      )}
    </figure>
  );
}
```

Create `ProgressiveAttachmentImage.module.css`:

```css
.frame {
  position: relative;
  width: 100%;
  margin: 0;
  overflow: hidden;
  isolation: isolate;
  border-radius: 0.875rem;
  background: color-mix(in srgb, currentColor 8%, transparent);
}

.image,
.placeholder {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.image {
  display: block;
  object-fit: cover;
  opacity: 1;
  transition: opacity 180ms ease-out;
}

.frame[data-phase="loading-original"] .image {
  opacity: 0.96;
}

.placeholder {
  background:
    linear-gradient(
      100deg,
      transparent 20%,
      color-mix(in srgb, currentColor 10%, transparent) 45%,
      transparent 70%
    ),
    color-mix(in srgb, currentColor 6%, transparent);
  background-size: 220% 100%;
  animation: media-placeholder-sweep 1.4s linear infinite;
}

.qualityBadge {
  position: absolute;
  right: 0.625rem;
  bottom: 0.625rem;
  padding: 0.1875rem 0.375rem;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 0.375rem;
  color: white;
  background: rgb(0 0 0 / 58%);
  font: 600 0.625rem/1 ui-sans-serif, sans-serif;
  letter-spacing: 0.08em;
  backdrop-filter: blur(8px);
}

@keyframes media-placeholder-sweep {
  to {
    background-position: -220% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .image {
    transition: none;
  }

  .placeholder {
    animation: none;
  }
}
```

Replace the CSS tokens with the frontend application's existing theme tokens when integrating.

## 6. Attachment-level integration

Create `AttachmentMedia.tsx`:

```tsx
import type { AttachmentMediaModel } from "./attachment-media.types";
import { ProgressiveAttachmentImage } from "./ProgressiveAttachmentImage";

interface AttachmentMediaProps {
  attachment: AttachmentMediaModel;
  onOpenImage?: () => void;
}

function readyPreviewUrl(attachment: AttachmentMediaModel): string | null {
  if (attachment.preview?.status !== "READY") return null;
  return attachment.preview.url;
}

export function AttachmentMedia({
  attachment,
  onOpenImage,
}: AttachmentMediaProps) {
  const previewUrl = readyPreviewUrl(attachment);

  if (attachment.type === "IMAGE") {
    return (
      <ProgressiveAttachmentImage
        previewUrl={previewUrl}
        originalUrl={attachment.publicURL}
        previewWidth={attachment.preview?.width}
        previewHeight={attachment.preview?.height}
        alt={attachment.name}
        onClick={onOpenImage}
      />
    );
  }

  if (attachment.type === "VIDEO") {
    return (
      <video
        src={attachment.publicURL}
        poster={previewUrl ?? undefined}
        preload="none"
        controls
        playsInline
        aria-label={attachment.name}
      />
    );
  }

  return null;
}
```

## 7. Pending preview state

When `preview.status` is `PENDING` or `PROCESSING`, `preview.url` is `null`. Render the aspect-ratio placeholder and refresh the visible message using the application's existing query layer:

```text
1 second -> 2 seconds -> 5 seconds -> stop after 30 seconds
```

Do not poll separately for every off-screen message. Poll only a visible attachment or invalidate/refetch the containing chat query after an upload completes.

If the preview reaches `FAILED`, stop polling. The progressive component can still load the original when its container approaches the viewport.

## 8. Integration rules

- Use `preview.url` only when `preview.status === "READY"`.
- Use `publicURL` as the original/high-quality image URL.
- Never start fetching all original images when the chat query resolves.
- Do not use `thumbnailURL` for new code; it is only a compatibility alias.
- Do not append changing query parameters to immutable preview URLs.
- Preserve the preview if loading the original fails.
- Keep video `preload="none"`; only the poster image should load during scrolling.
- The lightbox may request the original immediately because opening it is explicit user intent.

## 9. Acceptance checklist

- On initial chat render, network tools show preview requests but no off-screen original-image requests.
- Scrolling toward an image starts its original request around `800px` before visibility.
- No more than three original images load concurrently.
- The visible `src` changes only after the original has loaded and decoded.
- A failed original request leaves the preview visible.
- Scrolling away or unmounting causes no React state-update warnings.
- Image space is reserved before loading; the message list does not jump.
- The same original URL is not downloaded twice by duplicate components.
- Video attachments load only their poster until the user plays the video.
- Reduced-motion users do not receive shimmer or swap animation.

