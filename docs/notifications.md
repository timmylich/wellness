# Notifications (Firebase Cloud Messaging)

Push notifications use Firebase Cloud Messaging (`@react-native-firebase/messaging`).
`google-services.json` is **not** in the repository (it is git-ignored): put your own file in the
project root before `npx expo prebuild`. Everything goes through `src/lib/notifications.ts`:

- `NotificationProvider` — the interface the app uses (permission, token, messages).
- `firebaseProvider` (`src/lib/firebaseMessaging.ts`) — the FCM implementation, installed in
  `app/_layout.tsx`. `noopProvider` remains available for forks that drop Firebase.
- `initNotifications()` — called from `app/_layout.tsx`; registers only if the user enabled
  notifications in settings (`settings.notifications.enabled`) and a real provider is set.
- `settings.notifications` — `enabled`, `waterReminders`, `weighInReminder` (persisted, included
  in backups). `meta.pushToken` stores the current device token.

## Setting up FCM for your own build

1. Create a Firebase project and an Android app with package `io.github.timmylich.wellness`.
   Download `google-services.json` to the project root. **Do not commit it** if the repository
   is public with your own project — add it to `.gitignore` and provide it in CI as a secret.
2. Install the libraries:
   ```bash
   npx expo install @react-native-firebase/app @react-native-firebase/messaging expo-build-properties
   ```
3. In `app.json`:
   ```json
   "android": { "googleServicesFile": "./google-services.json" },
   "plugins": ["@react-native-firebase/app", "@react-native-firebase/messaging"]
   ```
   and add `"android.permission.POST_NOTIFICATIONS"` to `android.permissions`.
4. In `src/lib/notifications.ts`, uncomment the `firebaseProvider` sketch.
5. In `src/app/_layout.tsx`, call `setNotificationProvider(firebaseProvider)` before
   `initNotifications()`.
6. For background/quit-state messages, register
   `messaging().setBackgroundMessageHandler(...)` at module scope (e.g. in
   `src/lib/backgroundSteps.ts`'s neighbour file imported from `_layout.tsx`).
7. `npx expo prebuild --clean` and rebuild.

## Privacy rules

- Push is strictly opt-in.
- Never put health data in push payloads or send it to the server. Reminders that depend on
  data (e.g. "you drank only 1 L today") should be computed on the device and shown as local
  notifications; the server may only send generic triggers.
- Document any server component in the README and About screen.
