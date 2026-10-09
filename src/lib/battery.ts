/**
 * Helpers to let users exempt the app from aggressive OEM battery savers, which otherwise
 * delay or cancel background step updates. Opens the most specific screen available.
 */
import * as IntentLauncher from 'expo-intent-launcher';
import { Linking, Platform } from 'react-native';

const PACKAGE = 'io.github.timmylich.wellness';

/** Known OEM "autostart" screens; tried in order, first that opens wins. */
const OEM_AUTOSTART = [
  { packageName: 'com.miui.securitycenter', className: 'com.miui.permcenter.autostart.AutoStartManagementActivity' },
  { packageName: 'com.huawei.systemmanager', className: 'com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity' },
  { packageName: 'com.coloros.safecenter', className: 'com.coloros.safecenter.permission.startup.StartupAppListActivity' },
  { packageName: 'com.vivo.permissionmanager', className: 'com.vivo.permissionmanager.activity.BgStartUpManagerActivity' },
];

export async function openAutostartSettings(): Promise<void> {
  if (Platform.OS !== 'android') return;
  for (const target of OEM_AUTOSTART) {
    try {
      await IntentLauncher.startActivityAsync('android.intent.action.MAIN', target);
      return;
    } catch {
      // Not this vendor — try the next one.
    }
  }
  await openAppSettings();
}

/** The app's system "App info" page (battery, permissions, autostart on many ROMs). */
export async function openAppSettings(): Promise<void> {
  try {
    await IntentLauncher.startActivityAsync(IntentLauncher.ActivityAction.APPLICATION_DETAILS_SETTINGS, { data: `package:${PACKAGE}` });
  } catch {
    await Linking.openSettings();
  }
}
