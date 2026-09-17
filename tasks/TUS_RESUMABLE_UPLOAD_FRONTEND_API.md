# Resumable File Upload API (TUS)

This document describes how the frontend uploads, resumes, cancels, and downloads files through the backend's TUS-compatible API.

## Quick reference

| Purpose | Method | Path | Authentication | Success |
|---|---|---|---|---|
| Discover capabilities | `OPTIONS` | `/files` or `/files/{uploadId}` | No | `204 No Content` |
| Create an upload | `POST` | `/files` | Bearer token | `201 Created` |
| Upload the next chunk | `PATCH` | `/files/{uploadId}` | Bearer token | `204 No Content` |
| Read the current offset | `HEAD` | `/files/{uploadId}` | Bearer token | `200 OK` |
| Cancel/delete an upload | `DELETE` | `/files/{uploadId}` | Bearer token | `204 No Content` |
| Download a completed file | `GET` | `/files/{uploadId}` | Bearer token | `200 OK` or `206 Partial Content` |

The API uses TUS protocol version `1.0.0` and advertises the `creation` and `termination` extensions.

Unless stated otherwise, every upload request must contain:

```http
Authorization: Bearer <access-token>
Tus-Resumable: 1.0.0
```

The examples below use this base URL:

```text
http://localhost:8080
```

Use the environment-specific API base URL in the real frontend.

## Recommended frontend integration

Use [`tus-js-client`](https://github.com/tus/tus-js-client) instead of implementing the protocol manually.

```bash
npm install tus-js-client
```

### TypeScript upload helper

```ts
import * as tus from "tus-js-client";

const API_BASE_URL = "http://localhost:8080";

export interface StartUploadOptions {
  file: File;
  accessToken: string;
  onProgress?: (uploadedBytes: number, totalBytes: number) => void;
  onSuccess?: (result: { uploadUrl: string; uploadId: string }) => void;
  onError?: (error: Error) => void;
}

export async function startResumableUpload({
  file,
  accessToken,
  onProgress,
  onSuccess,
  onError,
}: StartUploadOptions): Promise<tus.Upload> {
  if (file.size <= 0) {
    throw new Error("Empty files are not supported by the upload API");
  }

  const upload = new tus.Upload(file, {
    endpoint: `${API_BASE_URL}/files`,
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    metadata: {
      filename: file.name,
      contentType: file.type || "application/octet-stream",
    },
    chunkSize: 10 * 1024 * 1024,
    retryDelays: [0, 1_000, 3_000, 5_000, 10_000],
    removeFingerprintOnSuccess: true,
    onProgress(bytesUploaded, bytesTotal) {
      onProgress?.(bytesUploaded, bytesTotal);
    },
    onError(error) {
      onError?.(error);
    },
    onSuccess() {
      if (!upload.url) {
        onError?.(new Error("Upload completed without an upload URL"));
        return;
      }

      const uploadUrl = upload.url;
      const uploadId = new URL(uploadUrl).pathname.split("/").filter(Boolean).at(-1);

      if (!uploadId) {
        onError?.(new Error("Could not extract the upload ID"));
        return;
      }

      onSuccess?.({ uploadUrl, uploadId });
    },
  });

  const previousUploads = await upload.findPreviousUploads();
  if (previousUploads.length > 0) {
    upload.resumeFromPreviousUpload(previousUploads[0]);
  }

  upload.start();
  return upload;
}
```

`tus-js-client` automatically creates the upload, sends chunks in order, performs `HEAD` requests when resuming, and stores a local upload fingerprint so the same file can be resumed after a page refresh.

### Pause, resume, and cancel

Keep the returned `tus.Upload` instance in component or application state.

```ts
// Pause locally. The server-side upload and uploaded chunks remain available.
await upload.abort();

// Continue the same Upload instance.
upload.start();

// Permanently cancel and ask the server to delete the upload.
await upload.abort(true);
```

Calling `abort(true)` uses the advertised TUS `termination` extension. After deletion, the old upload URL cannot be resumed.

## Upload lifecycle

```text
Select file
    |
    v
POST /files ---------------> Location: /files/{uploadId}
    |
    v
PATCH chunk at offset 0 ---> Upload-Offset: next byte
    |
    v
PATCH remaining chunks ----> Upload-Offset: file size
    |
    v
Backend assembles file in MinIO
    |
    v
GET /files/{uploadId} is available
```

An upload is complete when the response's `Upload-Offset` equals the original `Upload-Length`. There is no separate finalize endpoint. The final `PATCH` performs MinIO assembly and creates the durable attachment metadata row before returning, so it can take longer than intermediate chunk requests. Its `Upload-Attachment-Id` response header is the UUID to use when linking the uploaded file to a message or another entity.

The frontend should retain the upload URL or upload ID after success. The creation response does not contain a JSON body.

## Metadata

Metadata is sent in the `Upload-Metadata` header during creation. Values are UTF-8 strings encoded with Base64.

The backend currently uses these case-sensitive keys:

| Key | Purpose | Example |
|---|---|---|
| `filename` | Sets the download filename through `Content-Disposition` | `photo.jpg` |
| `contentType` | Sets the download `Content-Type` | `image/jpeg` |

`tus-js-client` performs the Base64 encoding automatically when metadata is supplied through its `metadata` option.

Manual header example:

```http
Upload-Metadata: filename cGhvdG8uanBn,contentType aW1hZ2UvanBlZw==
```

Unknown metadata keys are stored with the upload but currently have no effect on downloads.

## Protocol endpoints

### 1. Discover server capabilities

```http
OPTIONS /files HTTP/1.1
```

Authentication is not required for `OPTIONS` requests.

Example response:

```http
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
Tus-Version: 1.0.0
Tus-Extension: creation,termination
Tus-Max-Size: 10737418240
```

`Tus-Max-Size` is expressed in bytes and comes from backend configuration. The default is 10 GiB, but the frontend should use the value returned by the server instead of hard-coding it.

### 2. Create an upload

```http
POST /files HTTP/1.1
Authorization: Bearer <access-token>
Tus-Resumable: 1.0.0
Upload-Length: 7340032
Upload-Metadata: filename cGhvdG8uanBn,contentType aW1hZ2UvanBlZw==
```

Do not send the file bytes or `multipart/form-data` in this request.

Example response:

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/files/5c102c85-c1ed-4586-b7a1-9bba16eb93bf
Tus-Resumable: 1.0.0
Tus-Version: 1.0.0
Upload-Offset: 0
Upload-Length: 7340032
```

Read and store the `Location` response header. Browsers are allowed to read it through the configured CORS policy.

Creation constraints:

- `Upload-Length` is required.
- The length must be greater than zero. Empty files are not supported.
- The length must not exceed `Tus-Max-Size`.
- Metadata values must contain valid Base64.

### 3. Upload a chunk

Send raw binary bytes to the URL returned in `Location`.

```http
PATCH /files/5c102c85-c1ed-4586-b7a1-9bba16eb93bf HTTP/1.1
Authorization: Bearer <access-token>
Tus-Resumable: 1.0.0
Upload-Offset: 0
Content-Type: application/offset+octet-stream
Content-Length: 5242880

<5 MiB of raw file bytes>
```

Example response:

```http
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
Tus-Version: 1.0.0
Upload-Offset: 5242880
```

Rules:

- `Content-Type` must be `application/offset+octet-stream`.
- Send raw bytes, not JSON, Base64, or form data.
- `Upload-Offset` must exactly equal the server's current offset.
- The chunk must contain at least one byte.
- The browser must send a valid `Content-Length`. When a `Blob`, `File`, or `ArrayBuffer` is used as the request body, the browser normally supplies it automatically.
- The chunk must not extend beyond the declared `Upload-Length`.
- Chunks must be uploaded sequentially for a given upload URL.

Do not send simultaneous `PATCH` requests for the same upload. The backend serializes them and rejects stale offsets with `409 Conflict`.

Only the final successful `PATCH` includes the durable attachment identifier:

```http
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
Upload-Offset: 7340032
Upload-Attachment-Id: 7ce84d91-d17d-4f32-99c2-ad9ab29f6766
```

### 4. Get the resumable offset

Use `HEAD` after a network interruption or before continuing a saved upload URL.

```http
HEAD /files/5c102c85-c1ed-4586-b7a1-9bba16eb93bf HTTP/1.1
Authorization: Bearer <access-token>
Tus-Resumable: 1.0.0
```

Example response:

```http
HTTP/1.1 200 OK
Tus-Resumable: 1.0.0
Tus-Version: 1.0.0
Upload-Offset: 5242880
Upload-Length: 7340032
Cache-Control: no-store
```

Continue reading the local file at `Upload-Offset` and send the next `PATCH` from that byte. A `HEAD` response also includes `Upload-Attachment-Id` after the upload has completed.

### 5. Cancel or delete an upload

```http
DELETE /files/5c102c85-c1ed-4586-b7a1-9bba16eb93bf HTTP/1.1
Authorization: Bearer <access-token>
Tus-Resumable: 1.0.0
```

Example response:

```http
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
Tus-Version: 1.0.0
```

Deletion removes uploaded chunks, any uncommitted final MinIO object, and the in-memory upload record. Once an upload has completed and produced an attachment, this endpoint returns `409 Conflict`; delete completed attachments through their owning domain workflow instead.

## Downloading a completed file

The download endpoint uses the same upload URL:

```http
GET /files/{uploadId}
Authorization: Bearer <access-token>
```

The `Tus-Resumable` header is not required for downloads.

### Download with `fetch`

```ts
export async function downloadUpload(
  uploadId: string,
  accessToken: string,
): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}/files/${uploadId}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Download failed with status ${response.status}`);
  }

  return response.blob();
}
```

Because the endpoint requires an `Authorization` header, a protected image or video generally cannot be used directly as `<img src="...">` or `<video src="...">`. Fetch it as a `Blob` and create a temporary object URL:

```ts
const blob = await downloadUpload(uploadId, accessToken);
const objectUrl = URL.createObjectURL(blob);

imageElement.src = objectUrl;

// Later, when the component no longer needs the object URL:
URL.revokeObjectURL(objectUrl);
```

For large media, prefer authenticated range requests rather than downloading the entire object into memory.

### Full download response

```http
HTTP/1.1 200 OK
Accept-Ranges: bytes
Content-Type: image/jpeg
Content-Length: 7340032
Content-Disposition: attachment; filename="photo.jpg"
```

If `contentType` was not supplied, the response uses `application/octet-stream`. If `filename` was not supplied, `Content-Disposition` is omitted.

### Range downloads

The endpoint supports one byte range at a time:

```http
GET /files/{uploadId} HTTP/1.1
Authorization: Bearer <access-token>
Range: bytes=0-1048575
```

Example response:

```http
HTTP/1.1 206 Partial Content
Accept-Ranges: bytes
Content-Range: bytes 0-1048575/7340032
Content-Length: 1048576
```

Supported forms:

```text
bytes=0-1023     first 1024 bytes
bytes=1024-      from byte 1024 to the end
bytes=-1024      final 1024 bytes
```

Multiple ranges such as `bytes=0-10,20-30` are not supported.

## Error handling

TUS protocol and storage errors use this JSON structure:

```json
{
  "timestamp": "2026-09-16T07:15:30.123Z",
  "status": 409,
  "error": "Conflict",
  "message": "Invalid Upload-Offset. Expected 5242880 but received 0"
}
```

TUS error responses also include:

```http
Tus-Resumable: 1.0.0
```

Common statuses:

| Status | Meaning | Frontend action |
|---|---|---|
| `400 Bad Request` | Missing/invalid length or offset, invalid metadata, chunk too large, or malformed range | Treat as a non-retryable client error after validating the request |
| `401 Unauthorized` | Missing, invalid, or expired access token | Refresh/re-authenticate, then retry with a valid token |
| `404 Not Found` | The upload ID is unknown or was deleted | Remove the saved resume entry and start a new upload |
| `409 Conflict` | Wrong offset, upload already complete, or download requested before completion | Run `HEAD`; resume from its offset, or treat an already completed upload as success |
| `412 Precondition Failed` | `Tus-Resumable` is missing or not `1.0.0` | Fix the client/protocol version; do not blindly retry |
| `413 Content Too Large` | File exceeds `Tus-Max-Size` | Reject the file in the UI |
| `415 Unsupported Media Type` | A `PATCH` did not use `application/offset+octet-stream` | Correct the content type; do not retry the unchanged request |
| `416 Range Not Satisfiable` | Download range starts outside the file | Request a valid range or restart the download |
| `500 Internal Server Error` | MinIO/storage operation failed | Retry with backoff; use `HEAD` before retrying an upload chunk |

For an ambiguous `PATCH` failure, do not assume the chunk failed. The server may have accepted it while the response was lost. Send `HEAD` first and continue from the returned `Upload-Offset`. `tus-js-client` handles this recovery behavior.

Authentication failures are produced by the application's general security handler and may use the application's standard API error shape rather than the TUS error JSON shown above.

## Progress and UI recommendations

- Validate `file.size > 0` before starting.
- Discover or cache `Tus-Max-Size`, then reject oversized files before `POST`.
- Display progress as `bytesUploaded / bytesTotal`.
- Show a distinct paused/offline state rather than marking temporary network errors as failed.
- Use exponential or staged retry delays.
- Keep the file's TUS fingerprint until completion so refresh recovery works.
- Remove the saved fingerprint after successful completion or permanent deletion.
- Persist the returned upload ID with the chat message or attachment draft only after the upload succeeds.
- Prevent duplicate parallel uploads when a component is mounted more than once.
- Treat the upload as successful only when the final `PATCH` returns and the offset equals the file size.

## Current backend limitations

The frontend must account for these implementation limits:

- Upload state is currently stored in backend memory. A network interruption or browser refresh is resumable while the same backend process remains alive, but a backend restart loses the upload registry even though chunk objects may still exist in MinIO.
- Upload state is not shared across multiple backend instances. Requests for one upload must reach the same instance unless the backend storage design is changed.
- Deferred upload length (`Upload-Defer-Length`) is not supported.
- Upload concatenation (`Upload-Concat`) is not supported.
- Upload checksums (`Upload-Checksum`) are not supported.
- Empty files are not supported.
- There is no endpoint to list a user's uploads.
- There is no separate completion callback or JSON attachment object. Completion is represented by the final offset reaching the declared length, and the attachment UUID is returned in `Upload-Attachment-Id`.
- Upload creation, resume, deletion, and direct download are restricted to the authenticated user who created the upload. Access for recipients should be provided by a domain-specific attachment download endpoint after the attachment is linked to a message.

## CORS requirements

The backend currently allows the configured frontend origin (development defaults to `http://localhost:3000`) and exposes the headers required by TUS. A deployed frontend origin must be added to `app.security.cors.allowed-origins` on the backend.

Allowed custom request headers include:

```text
Authorization
Content-Type
Tus-Resumable
Upload-Length
Upload-Offset
Upload-Attachment-Id
Upload-Metadata
```

Readable response headers include:

```text
Location
Tus-Resumable
Tus-Version
Tus-Extension
Tus-Max-Size
Upload-Length
Upload-Offset
```

## Manual implementation checklist

If `tus-js-client` cannot be used, the custom client must:

1. Send `OPTIONS /files` and validate TUS `1.0.0` support.
2. Send `POST /files` with `Upload-Length`, encoded metadata, authorization, and `Tus-Resumable`.
3. Save the absolute `Location` header.
4. Slice the local `File` into sequential chunks.
5. Send each slice as raw `application/offset+octet-stream` data.
6. Use the latest server-returned `Upload-Offset` for the next request.
7. Use `HEAD` after every ambiguous network failure.
8. Never retry a `PATCH` at a guessed or stale offset.
9. Consider the upload complete only when offset equals total file size.
10. Send `DELETE` when the user permanently cancels the upload.
