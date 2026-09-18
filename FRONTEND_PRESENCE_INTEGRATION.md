# Frontend Presence Integration

This document describes how the frontend should display and synchronize users' online/offline state.

## Presence semantics

- A user is `ONLINE` while at least one authenticated WebSocket session is active.
- Multiple tabs and devices count as separate sessions. Closing one session does not make the user offline while another remains connected.
- A user changes to `OFFLINE` after their final session disconnects or is detected as dead by WebSocket heartbeats.
- Presence is shared only with direct-chat peers.
- Saved-message chats do not have peer presence and return `peerPresence: null`.
- Timestamps are UTC ISO-8601 instants. Format them in the viewer's local time zone.

## Data contracts

Direct-chat responses from `GET /api/v1/chats`, `GET /api/v1/chats/{chatId}`, and `POST /api/v1/chats/dm` include an initial presence snapshot. `Chat` is found in `results[]` for the paginated list endpoint and in `data` for the single-chat endpoints:

```ts
type PresenceStatus = "ONLINE" | "OFFLINE";

type UserPresence = {
  userId: string;
  status: PresenceStatus;
  lastSeenAt: string | null;
  changedAt: string | null;
};

type Chat = {
  id: string;
  type: "DIRECT" | "SAVED";
  peer: {
    id: string;
    username: string | null;
    firstname: string | null;
    lastname: string | null;
  };
  peerPresence: UserPresence | null;
  lastMessage: unknown | null;
  createdAt: string;
  updatedAt: string;
};
```

Example direct-chat snapshot:

```json
{
  "id": "3925eb34-35b6-49ab-a52d-fba33ca3bc96",
  "type": "DIRECT",
  "peer": {
    "id": "83d66f12-f895-4db4-b260-9c0b505c7512",
    "username": "alex",
    "firstname": "Alex",
    "lastname": "Smith"
  },
  "peerPresence": {
    "userId": "83d66f12-f895-4db4-b260-9c0b505c7512",
    "status": "OFFLINE",
    "lastSeenAt": "2026-09-18T10:15:30Z",
    "changedAt": "2026-09-18T10:15:30Z"
  },
  "lastMessage": null,
  "createdAt": "2026-09-18T09:00:00",
  "updatedAt": "2026-09-18T09:00:00"
}
```

`lastSeenAt` and `changedAt` can both be `null` when the user has never had a recorded disconnect. For an online snapshot, `changedAt` is when the current online period began and `lastSeenAt` may contain the previous offline time.

## WebSocket connection

The server exposes a SockJS/STOMP endpoint at `/ws`. Authenticate the STOMP `CONNECT` frame with the same access token used by REST:

```ts
import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";

type PresenceChangedEvent = {
  type: "PRESENCE_CHANGED";
  userId: string;
  status: PresenceStatus;
  lastSeenAt: string | null;
  changedAt: string;
};

export function createRealtimeClient(
  backendOrigin: string,
  accessToken: string,
  onPresence: (event: PresenceChangedEvent) => void,
  onConnected: () => void,
) {
  const client = new Client({
    // backendOrigin is the server origin, for example http://localhost:8080.
    // The WebSocket endpoint does not use the REST /api/v1 prefix.
    webSocketFactory: () => new SockJS(`${backendOrigin}/ws`),
    connectHeaders: {
      Authorization: `Bearer ${accessToken}`,
    },
    reconnectDelay: 5_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    onConnect: () => {
      client.subscribe("/user/queue/presence", (message: IMessage) => {
        onPresence(JSON.parse(message.body) as PresenceChangedEvent);
      });

      client.subscribe("/user/queue/messages", (message: IMessage) => {
        // Keep the existing message-event handler here.
      });

      onConnected();
    },
  });

  return client;
}
```

Clients may subscribe only to `/user/queue/messages` and `/user/queue/presence`. STOMP `SEND` frames are rejected; continue creating messages through the REST API.

When an access token is refreshed, deactivate the existing client and reconnect it with the new token.

## Startup and reconnect order

Use this order to avoid missing a presence transition between the REST request and the WebSocket subscription:

1. Activate the STOMP client.
2. In `onConnect`, subscribe to `/user/queue/presence` and `/user/queue/messages`.
3. Fetch or refetch the visible chats after both subscriptions are registered.
4. Store each non-null `peerPresence` snapshot by `userId`.
5. Apply subsequent `PRESENCE_CHANGED` events.

Spring's simple WebSocket broker does not durably retain events while the browser is disconnected. Therefore, refetch the chat list or the currently open chat after every successful reconnect.

## Presence store and ordering

Keep one normalized entry per user so every avatar and chat header updates together:

```ts
type PresenceState = Record<string, UserPresence>;

function isNewerOrEqual(
  current: UserPresence | undefined,
  incoming: UserPresence,
): boolean {
  if (!current?.changedAt) return true;
  if (!incoming.changedAt) return false;
  return Date.parse(incoming.changedAt) >= Date.parse(current.changedAt);
}

function applyPresence(
  state: PresenceState,
  incoming: UserPresence,
): PresenceState {
  if (!isNewerOrEqual(state[incoming.userId], incoming)) return state;

  return {
    ...state,
    [incoming.userId]: incoming,
  };
}
```

Apply REST snapshots and WebSocket events through the same reducer. Comparing `changedAt` prevents an older REST response or delayed event from overwriting a newer state.

## UI behavior

Recommended rendering rules:

- `ONLINE`: show a green avatar dot and `online` in the open chat header.
- `OFFLINE` with `lastSeenAt`: show `last seen ...`, formatted relatively or as a local date.
- `OFFLINE` without `lastSeenAt`: show `offline`.
- `peerPresence === null`: render no presence indicator. This is expected for saved-message chats.
- Do not infer that a peer is offline merely because the current browser's WebSocket disconnected. Retain the last server-provided peer state, display a connection/reconnecting indicator for the application, then reconcile after reconnect.

Example helper:

```ts
function presenceLabel(presence: UserPresence | null): string | null {
  if (!presence) return null;
  if (presence.status === "ONLINE") return "online";
  if (!presence.lastSeenAt) return "offline";
  return `last seen ${formatRelativeTime(presence.lastSeenAt)}`;
}
```

## Operational note

Presence is currently maintained in memory and is suitable for one backend application instance. A backend restart temporarily makes every user offline until their clients reconnect. If the backend is later horizontally scaled, presence storage and cross-instance events must move to Redis or another shared system; the frontend contract can remain unchanged.
