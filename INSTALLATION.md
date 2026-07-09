# Passport Photo Print System

A complete production-ready system for passport photo printing.

## Components

1.  **Android App**: Kotlin + Compose + CameraX. Used for capturing/selecting photos and sending them to print.
2.  **Node.js Backend**: Express + Socket.io + Sharp. Processes images into 4/8 photo layouts (4x6 sheet) and triggers auto-print.
3.  **Merchant Portal**: HTML/JS/Socket.io. Real-time dashboard for the merchant to monitor prints.

## Installation

### 1. Backend & Portal (Deploy to Render/VPS)
1.  Go to the `server/` directory.
2.  Install dependencies: `npm install`
3.  Start the server: `npm start`
4.  Configure your environment variables (e.g., `PORT`).

### 2. Android App
1.  Open the project in Android Studio.
2.  Update `Constants.BACKEND_URL` in `app/src/main/java/com/example/Constants.kt` with your server URL.
3.  Build and run on an Android device.

## Features
-   **No Auth**: Designed for a single merchant usage.
-   **High Resolution**: CameraX captures full quality.
-   **Real-time**: Socket.io updates the portal instantly.
-   **Voice Notifications**: "New photo received" and "Printing started".
-   **Layouts**: Automatic 4 (Portrait) or 8 (Landscape) photo generation with black borders.
-   **Indian Passport Standard**: 3.5cm x 4.5cm sizing used for crops.
