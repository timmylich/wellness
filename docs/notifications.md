# Notifications

Wellness uses **local notifications only**: no push server, no Firebase, no Google Play
services. That keeps the app private and eligible for F-Droid.

- Delivery: the local Expo module `modules/wellness-native` (`Reminders.kt`) shows a
  notification directly or schedules it with an inexact `AlarmManager` alarm (no exact-alarm
  permission; Android may deliver it a few minutes late).
- JS API: `src/lib/notifications.ts` — `showNotification`, `scheduleAt`, `cancelScheduled`,
  `requestNotificationPermission`.
- Everything is opt-in in Settings → Notifications.

Current uses:

| Feature | When |
| --- | --- |
| Pantry expiry | 10:00 the day before a product expires (`src/sections/Pantry.tsx`) |
| App update | Once per new version found on GitHub Releases, checked at most daily (`src/lib/updates.ts`) |

The step counter's foreground service (`StepService.kt`) also shows a silent, minimum-priority
notification while it runs; Android requires it for any long-running background work.

## Why not Firebase or Supabase push?

Server push needs a server that knows each device and decides when to send. Nothing in the
app needs that: reminders depend on data that only exists on the phone, and update checks can
ask GitHub directly. Firebase Cloud Messaging also requires Google Play services, which F-Droid
does not accept.

If server push is ever needed (for example, announcements), the open option compatible with
F-Droid is [UnifiedPush](https://unifiedpush.org): the user picks a distributor app (ntfy,
NextPush…), and a small server (Supabase Edge Function, Cloudflare Worker…) posts to the
endpoint the app registers. Keep it optional and never send health data.
