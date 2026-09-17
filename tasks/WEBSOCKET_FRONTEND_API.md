# WebSocket API for the Frontend Agent

Contract derived from the current backend source on 2026-09-03. Examples use illustrative IDs and timestamps. This document describes implemented behavior; frontend recommendations are identified separately.

## 1. Integration overview

The backend uses **STOMP over SockJS**. There is exactly **one client subscription destination** and **one application event type**:

| Purpose | Transport / destination | Direction | Payload |
| --- | --- | --- | --- |
| Establish transport | SockJS endpoint `/ws` | Client opens connection | Managed by the SockJS client |
| Authenticate | STOMP `CONNECT` | Client to server | `Authorization: Bearer <accessToken>` header; empty body |
| Receive messages for all the user's chats | STOMP `SUBSCRIBE` to `/user/queue/messages` | Client subscribes; server sends `MESSAGE` frames | `RealtimeMessageEvent` JSON |
| Create a message | HTTP `POST /api/v1/chats/{chatId}/messages` | Client to server | `{ "text": "Hello" }` |
| Load or recover message history | HTTP `GET /api/v1/chats/{chatId}/messages` | Client to server | Query parameters; no request body |

**Every STOMP `SEND` frame is rejected.** There are no `/app/...` message handlers and no supported `/topic/...` subscriptions. Use REST to send messages and the private queue to receive committed messages.

## 2. Connection and authentication

| Setting | Value |
| --- | --- |
| Local backend origin | `http://localhost:8080` |
| SockJS URL passed to the frontend transport client | `http://localhost:8080/ws` |
| REST base URL | `http://localhost:8080/api/v1` |
| Default allowed browser origin | `http://localhost:3000` |
| Origin configuration | Backend `CORS_ALLOWED_ORIGINS` |
| Server STOMP heartbeat configuration | Send / receive: `10000` / `10000` milliseconds; negotiated with the client |
| User destination prefix | `/user` |
| Internal broker destination prefix | `/queue` |
| Inbound STOMP message size limit | 16 KiB |
| Outbound send buffer limit | 64 KiB |
| Transport send time limit | 15 seconds |

Use the deployed backend origin in production, with HTTPS for the SockJS URL. `/ws` is registered with `.withSockJS()`; let a SockJS-compatible client manage transport paths and framing. It is not a Socket.IO endpoint or a raw JSON WebSocket endpoint.

Obtain `data.accessToken` and `data.expiresAt` from `POST /api/v1/auth/login`. See `AUTH_AND_GET_ME_API.md` for the complete authentication contract. There is currently no refresh-token endpoint.

The `/ws` HTTP handshake routes are publicly accessible, but the subsequent STOMP connection requires a valid JWT. Put the token in the **STOMP CONNECT headers**, even when the HTTP handshake succeeds. The interceptor reads the exact header name `Authorization` and requires the exact prefix `Bearer `.

### CONNECT request

The following are logical STOMP frames, before SockJS transport encoding. `<NUL>` denotes the frame terminator, not literal text to transmit. Let the STOMP client encode frames.

```text
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer <accessToken>
heart-beat:10000,10000

<NUL>
```

### CONNECT success response

An illustrative protocol response for a STOMP 1.2 client:

```text
CONNECTED
version:1.2
heart-beat:10000,10000

<NUL>
```

Additional protocol headers may be present. There is no application JSON response body. Wait for the STOMP client's connected callback before subscribing.

The JWT subject identifies the user. The frontend does not send a user ID or chat ID to select whose queue to receive. JWT validation happens at `CONNECT`; the interceptor does not revalidate token expiry on every subsequent frame. Frontend recommendation: track the login response's `expiresAt`, close the connection when the login expires, and require login again. Reconnecting requires a valid token.

## 3. Complete subscription and event catalog

### `/user/queue/messages`

Subscribe once per connection to receive events across all of the authenticated user's supported chats. Do not create a subscription per chat.

#### Subscription request

```text
SUBSCRIBE
id:messages-0
destination:/user/queue/messages
ack:auto

<NUL>
```

| Input | Meaning |
| --- | --- |
| `id` | Client-selected subscription ID, unique within the connection |
| `destination` | Must equal `/user/queue/messages` exactly |
| `ack` | Use `auto`; no application delivery/read acknowledgment is implemented |
| Body | Empty |

There is no initial application response, chat snapshot, or history replay when subscribing. Future events arrive in STOMP `MESSAGE` frames associated with the subscription ID. Parse their body as JSON. Protocol headers such as `message-id` are not the persisted chat message ID; use `message.id` from the JSON body.

Do not subscribe to `/queue/messages`, `/user/{userId}/queue/messages`, `/topic/messages`, or a destination containing a chat ID. The interceptor rejects every subscription destination except the exact private queue above. `/queue/messages` is only the publisher's internal destination.

### Event: `MESSAGE_CREATED`

**Trigger:** a successful REST message creation transaction commits.

**Recipients:** active, non-deleted chat members whose user records are not deleted. For a direct chat, both the sender and the peer receive the event. For Saved Messages, only its owner receives the event. User-targeted publishing can reach the user's subscribed sessions, including other tabs/devices connected to the same backend instance.

#### Direct message response received by the peer

This is the JSON body of the STOMP `MESSAGE` frame, without a REST response wrapper:

```json
{
  "type": "MESSAGE_CREATED",
  "chatId": "11111111-1111-4111-8111-111111111111",
  "chatType": "DIRECT",
  "message": {
    "id": "22222222-2222-4222-8222-222222222222",
    "seq": 42,
    "senderId": "33333333-3333-4333-8333-333333333333",
    "text": "Hello!",
    "createdAt": "2026-09-03T14:30:00.123456",
    "mine": false
  }
}
```

The sender receives the same event with `message.mine: true`. Other fields are unchanged.

#### Saved Messages response received by the owner

```json
{
  "type": "MESSAGE_CREATED",
  "chatId": "44444444-4444-4444-8444-444444444444",
  "chatType": "SAVED",
  "message": {
    "id": "55555555-5555-4555-8555-555555555555",
    "seq": 1,
    "senderId": "33333333-3333-4333-8333-333333333333",
    "text": "Remember this note",
    "createdAt": "2026-09-03T14:35:00.123456",
    "mine": true
  }
}
```

#### Field contract

| Field | JSON type | Meaning |
| --- | --- | --- |
| `type` | string | Currently always `MESSAGE_CREATED` |
| `chatId` | string (UUID) | Chat to update in frontend state |
| `chatType` | string | Current send flow supports `DIRECT` and `SAVED` |
| `message` | object | Persisted message, with the fields below |
| `message.id` | string (UUID) | Stable persisted message ID; use for deduplication |
| `message.seq` | integer | Increasing sequence within this chat; first message uses `1` |
| `message.senderId` | string (UUID) | User who sent the message |
| `message.text` | string | Text after backend removal of leading/trailing whitespace |
| `message.createdAt` | string | Java `LocalDateTime` serialized as an ISO-style date/time without timezone or offset; fractional precision may vary |
| `message.mine` | boolean | Whether the sender is the user receiving this payload |

`GROUP` and `CHANNEL` exist in the backend enum, but message creation currently accepts only `DIRECT` and `SAVED`. Their presence in a generated enum/schema does not imply implemented group/channel messaging.

Do not assume `createdAt` is UTC or append `Z`: no timezone is included in this contract. Order messages by `seq`. The backend uses a Java `long` for `seq`; JSON transports it as a number. JavaScript's safe-integer limit applies if sequences ever exceed `Number.MAX_SAFE_INTEGER`.

## 4. REST request that produces the event

### Send a message

```http
POST /api/v1/chats/11111111-1111-4111-8111-111111111111/messages
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "text": "Hello!"
}
```

`text` is required, must not be blank, and has a maximum length of 4096 Java string characters (UTF-16 code units). Validation happens before stripping leading/trailing whitespace. Do not submit sender IDs, message IDs, timestamps, or sequence numbers; the server assigns them.

#### Success: `200 OK`

```json
{
  "success": true,
  "message": "Message sent",
  "data": {
    "id": "22222222-2222-4222-8222-222222222222",
    "seq": 42,
    "senderId": "33333333-3333-4333-8333-333333333333",
    "text": "Hello!",
    "createdAt": "2026-09-03T14:30:00.123456",
    "mine": true
  }
}
```

The response's `data` is the same message represented inside the realtime event. The REST response does not contain `chatId`; retain it from the request. Either the REST response or the realtime event may reach the frontend first. Merge both by `(chatId, message.id)`.

A committed message remains saved even if realtime delivery fails. A REST success does not prove that the peer received or read it. There is no client message ID or idempotency key implemented: automatically retrying a POST after an ambiguous network failure can create a second message.

### Obtain a chat ID and load chat metadata

All requests below require `Authorization: Bearer <accessToken>`.

| Request | Body / parameters | Successful result |
| --- | --- | --- |
| `POST /api/v1/chats/dm` | JSON `{ "username": "alice" }`; nonblank, max 64 characters; trimmed, optional leading `@` removed, case-insensitive lookup | `200`, `{ success: true, message: "Direct chat retrieved", data: ChatResponse }` |
| `POST /api/v1/chats/saved` | No body | `200`, `{ success: true, message: "Saved messages chat retrieved", data: ChatResponse }` |
| `GET /api/v1/chats/{chatId}` | No body | `200`, `{ success: true, message: "Chat retrieved", data: ChatResponse }` |
| `GET /api/v1/chats?page=0&size=20` | Zero-based `page >= 0`; `size` 1–50, default 20 | `200`, `{ success: true, message: "Chats retrieved", results: ChatResponse[], total, page, size, hasPrev, hasNext }` |

`ChatResponse` contains `id`, `type`, `peer`, `lastMessage`, `createdAt`, and `updatedAt`. `peer` contains `id`, `username`, `firstname`, and `lastname`; for Saved Messages it is the current user. `lastMessage` is a `MessageResponse` or `null` for an empty chat. Chat timestamps also use `LocalDateTime` without an offset.

Creating or retrieving a chat does not publish a chat-created event. If an event references an unknown `chatId`, fetch that chat's metadata through `GET /api/v1/chats/{chatId}`.

## 5. Message history and recovery requests

### Latest messages

```http
GET /api/v1/chats/11111111-1111-4111-8111-111111111111/messages?size=50
Authorization: Bearer <accessToken>
```

| Parameter | Required | Rules |
| --- | --- | --- |
| `beforeSeq` | No | Positive integer; returns messages with `seq < beforeSeq` (exclusive) |
| `size` | No | 1–100; default 50 |

Omit `beforeSeq` for the newest page. Results are sorted by **descending `seq`**, newest first.

#### Example success: `200 OK`

This example shows a one-message page with older messages available:

```json
{
  "success": true,
  "message": "Messages retrieved",
  "data": {
    "messages": [
      {
        "id": "22222222-2222-4222-8222-222222222222",
        "seq": 42,
        "senderId": "33333333-3333-4333-8333-333333333333",
        "text": "Hello!",
        "createdAt": "2026-09-03T14:30:00.123456",
        "mine": false
      }
    ],
    "nextBeforeSeq": 42,
    "hasMore": true
  }
}
```

### Older messages

```http
GET /api/v1/chats/11111111-1111-4111-8111-111111111111/messages?beforeSeq=42&size=50
Authorization: Bearer <accessToken>
```

Use the previous response's `nextBeforeSeq` as the next request's `beforeSeq`. When there are no older pages, `hasMore` is `false` and `nextBeforeSeq` is `null`. Empty history returns `messages: []`, `nextBeforeSeq: null`, and `hasMore: false`.

There is no `afterSeq` endpoint, WebSocket replay cursor, or durable offline event queue. Frontend recovery recommendation:

1. After connecting/reconnecting, subscribe and buffer or merge live events while loading REST state.
2. Refresh the chat list, including additional pages as needed, to discover chats/messages missed while disconnected.
3. For each chat being synchronized, fetch its newest messages without `beforeSeq`.
4. Page backward with `nextBeforeSeq` until reaching the sequence that was synchronized before disconnect, or until `hasMore` is false. Do not assume one page covers a long disconnect.
5. Merge REST and live messages by ID and render in ascending `seq` order. Do not let an older history response overwrite newer live state.
6. Repeat reconciliation when necessary; the backend exposes no application-level subscription-ready acknowledgment that makes a REST snapshot and subscription atomic.

## 6. Errors and unsupported operations

### STOMP errors

There is no custom WebSocket error DTO or `/user/queue/errors` destination. The interceptor throws authentication/access exceptions; handle the STOMP client's error callback and transport close/error callbacks. Do not parse these as REST `ApiErrorResponse` objects or depend on an exact JSON body/header layout.

These are the exact exception messages in the backend, useful for diagnostics; the framework may wrap them in a protocol error:

| Condition | Backend exception message |
| --- | --- |
| Missing token, wrong header/prefix, or empty bearer value | `A Bearer token is required for WebSocket connections` |
| JWT decoding/validation failure, including expiry | `Invalid or expired WebSocket access token` |
| JWT converter returns no authentication | `Invalid WebSocket access token` |
| Non-disconnect client command without authenticated principal | `WebSocket authentication is required` |
| Any STOMP `SEND` | `WebSocket message sending is disabled; use the REST API` |
| Subscription to any other destination | `Only the private message queue can be subscribed to` |

An HTTP transport/origin failure can occur before STOMP connects. Check the configured backend URL and `CORS_ALLOWED_ORIGINS`. Reconnect transient transport failures with a delay/backoff; stop retrying an invalid login until authentication is restored.

### REST message errors

Missing/blank text returns `400 Bad Request`:

```json
{
  "text": "Message text is required"
}
```

Overlength text returns `400 Bad Request`:

```json
{
  "text": "Message text must not exceed 4096 characters"
}
```

A missing/inaccessible chat, or unsupported chat type for sending, returns `404 Not Found`:

```json
{
  "message": "Chat not found",
  "httpStatus": "NOT_FOUND",
  "localDateTime": "2026-09-03 14:30:00",
  "code": 404
}
```

Missing/invalid REST authentication returns `401 Unauthorized` with the security error shape:

```json
{
  "message": "Authentication is required",
  "httpStatus": "UNAUTHORIZED",
  "localDateTime": "2026-09-03 14:30:00",
  "code": 401
}
```

Invalid history parameter constraints produce `400` with an `ApiErrorResponse` whose message starts with `Constraint violation:`. Malformed JSON, malformed UUIDs, or parameter type conversion failures may use Spring's standard error response; error handling should tolerate non-field-map and non-`ApiErrorResponse` bodies.

### Features not currently implemented

- Typing indicators, online/offline presence broadcasts, and last-seen events. Session listeners only log connections/disconnections.
- Read receipts, delivery receipts, unread-count events, or mark-as-read commands.
- Message editing/deletion events, reactions, attachments, and media messages.
- Chat-created/updated/deleted events or group/channel messaging commands.
- A WebSocket send API, application request IDs, application acknowledgments, event replay, or a resume token.

## 7. Frontend TypeScript payload types

```ts
export type UUID = string;
export type ChatType = 'SAVED' | 'DIRECT' | 'GROUP' | 'CHANNEL';

export interface MessageResponse {
  id: UUID;
  seq: number;
  senderId: UUID;
  text: string;
  createdAt: string; // Local date/time; no timezone offset supplied.
  mine: boolean;
}

export interface RealtimeMessageEvent {
  type: 'MESSAGE_CREATED';
  chatId: UUID;
  chatType: ChatType; // Current publisher flow: DIRECT or SAVED only.
  message: MessageResponse;
}

export interface SendMessageRequest {
  text: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface MessageHistoryResponse {
  messages: MessageResponse[];
  nextBeforeSeq: number | null;
  hasMore: boolean;
}
```

These declarations describe expected payloads; validate parsed JSON at runtime before updating state. Ignore or record unknown future event types without crashing the connection.

## 8. Frontend implementation checklist

1. Use a STOMP client with a SockJS transport pointed at the backend's `/ws` URL.
2. Supply the current access token in the STOMP `CONNECT` headers on every connection attempt.
3. Subscribe to `/user/queue/messages` inside the connected callback so reconnects restore the subscription. Avoid duplicate active subscriptions.
4. Route each `MESSAGE_CREATED` by `chatId`. Upsert by persisted message ID, sort by `seq`, and update chat previews only when the incoming sequence is newer than their existing message.
5. Send through REST and merge the REST result with the sender's echoed event. A temporary optimistic ID is frontend-only; it is not echoed by the backend. Do not identify messages solely by text.
6. Load initial history and recover missed events through REST. Treat realtime delivery as best effort: events publish after commit, publication failures are logged, and no retry/replay mechanism is configured.
7. Keep one connection per intended application session. Unsubscribe/disconnect on teardown, and deactivate reconnection and clear user-specific state on logout/account changes.
8. Treat the broker as instance-local: it is Spring's in-memory simple broker, with no cross-instance broker relay configured. Multi-instance deployment requires backend infrastructure work to guarantee fan-out across instances.

## 9. Backend sources and verification scope

The contract was checked against these files under `../src/main/java/uz/mirmaxsudov/chatclonebackend`:

- `config/security/WebSocketConfig.java`: endpoint, prefixes, heartbeats, origins, limits.
- `security/websocket/WebSocketJwtChannelInterceptor.java`: JWT headers and destination/send restrictions.
- `listener/chat/RealtimeMessagePublisher.java`: event type, private delivery, recipient-specific `mine`, and after-commit publishing.
- `listener/chat/WebSocketSessionListener.java`: connection logging only.
- `model/response/chat/RealtimeMessageEvent.java` and `MessageResponse.java`: event payload fields.
- `controller/chat/ChatController.java`, `model/request/chat/SendMessageRequest.java`, and `service/impl/chat/ChatServiceImpl.java`: REST requests, validation, persistence, and history.
- `repository/chat/MessageRepository.java` and `ChatMemberRepository.java`: history ordering and recipient selection.
- `exceptions/GlobalExceptionHandler.java` and `security/handler/`: REST error shapes.

Existing tests also describe authentication/destination restrictions, sender/peer payload mapping, Saved Messages delivery, and after-commit publication in `WebSocketJwtChannelInterceptorTest`, `RealtimeMessagePublisherTest`, and `RealtimeMessageAfterCommitIntegrationTest`.

This is a source-reviewed integration reference; the illustrative wire frames are not a captured live session. No backend behavior was changed to produce this document.
