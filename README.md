# Decentralized BLE Mesh Chat

A server-less, offline-first chat application for Android that uses a custom Bluetooth Low Energy (BLE) flooding mesh protocol.

## 🚀 Features
-   **Decentralized**: No central server or internet required.
-   **Mesh Networking**: Messages hop between devices to extend range (TTL-based).
-   **Offline Capable**: Works purely on Bluetooth.
-   **Persistent**: Chat history saved locally.
-   **Background Capable**: Runs as a Foreground Service.

## ⚠️ Known Limitations & caveats
-   **Throughput**: BLE advertising acts as a bottleneck. Messages are short text only.
-   **Reliability**: Broadcast-based (UDP style). Delivery is "Best Effort".
-   **Security**: Currently plaintext. No encryption implemented in this V1.
-   **Battery**: Continuous scanning consumes battery; handled via efficient scan settings but still significant.

## 🛠 Architecture
-   **MVVM**: `chatViewModel` -> `ChatRepository` -> `MeshService` -> `MeshRouter`.
-   **Database**: Room Persistence Library.
-   **Protocol**:
    -   Uses **Manufacturer Specific Data** in BLE Advertisements.
    -   Max Payload: ~22 bytes per packet (Legacy Mode).
    -   Deduplication: LRU Cache of (MessageID + SenderID).

## 🔧 Setup
1.  Open in Android Studio.
2.  Sync Gradle.
3.  Deploy to Physical Device (Emulator does not support BLE well).
