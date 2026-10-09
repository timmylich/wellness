/** Settings sub-pages, opened from the "More" tab. One route renders the selected page. */
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, AppState, View } from 'react-native';

import { PaceSlider } from '../../components/PaceSlider';
import { Button, Card, Chip, Divider, Field, Hint, parseNum, Row, Screen, Segmented, T, Tap, Toggle } from '../../components/ui';
import type { SettingsPage } from '../../data/settingsPages';
import { useT, type Dict } from '../../i18n';
import { exportData, importData, importFoodPack } from '../../lib/backup';
import { bmr, computeTargets, NUTRIENT_KEYS } from '../../lib/calc';
import { pendingMilestone } from '../../lib/donation';
import { openAppSettings, openAutostartSettings } from '../../lib/battery';
import { backgroundTasksAvailable } from '../../lib/backgroundSteps';
import { deviceStepsSupported, requestStepPermission, syncDeviceSteps } from '../../lib/deviceSteps';
import { clearFoodCache, foodCacheSize } from '../../lib/foodCache';
import { notificationsAvailable } from '../../lib/notifications';
import { hcConnect, hcInstall, hcOpenSettings, hcStatus, hcSyncSteps, type HcStatus } from '../../lib/healthConnect';
import { ACCENTS, useTheme } from '../../lib/theme';
import { resetDonationPrompts, updateProfile, updateSettings, wipeSection } from '../../store/actions';
import { createDefaultData, replaceState, useStore } from '../../store/store';
import type { NutrientKey, Profile, SectionId } from '../../store/types';

/** Numeric input bound to a store value; commits only valid numbers. */
function NumSetting({ label, value, onCommit, suffix, hint }: { label: string; value: number | undefined; onCommit: (v: number | undefined) => void; suffix?: string; hint?: string }) {
  const [s, setS] = useState(value != null ? String(value) : '');
  return (
    <Field
      label={label}
      value={s}
      suffix={suffix}
      hint={hint}
      onChange={(v) => {
        setS(v);
        onCommit(parseNum(v));
      }}
    />
  );
}

function confirm(L: Dict, title: string, message: string, onOk: () => void) {
  Alert.alert(title, message, [
    { text: L.common.cancel, style: 'cancel' },
    { text: L.common.yes, style: 'destructive', onPress: onOk },
  ]);
}

function ProfilePage() {
  const L = useT();
  const profile = useStore((s) => s.profile);
  const p = (k: keyof Profile) => (v: number | undefined) => {
    if (v != null && v > 0) updateProfile({ [k]: v });
  };
  return (
    <Card>
      <Field label={L.settings.name} value={profile.name} onChange={(v) => updateProfile({ name: v })} numeric={false} />
      <Segmented value={profile.sex} onChange={(sex) => updateProfile({ sex })} options={[{ value: 'male', label: L.onboarding.male }, { value: 'female', label: L.onboarding.female }]} />
      <Row>
        <NumSetting label={L.onboarding.birthYear} value={profile.birthYear} onCommit={p('birthYear')} />
        <NumSetting label={L.onboarding.height} value={profile.heightCm} onCommit={p('heightCm')} suffix={L.units.cm} />
        <NumSetting label={L.onboarding.weight} value={profile.weightKg} onCommit={p('weightKg')} suffix={L.units.kg} />
      </Row>
    </Card>
  );
}

function GoalPage() {
  const L = useT();
  const profile = useStore((s) => s.profile);
  return (
    <>
      <Card>
        <NumSetting label={L.onboarding.targetWeight} value={profile.targetWeightKg} onCommit={(v) => v && v > 0 && updateProfile({ targetWeightKg: v })} suffix={L.units.kg} />
        <T bold>{L.pace.title}</T>
        <PaceSlider profile={profile} onChange={(v) => updateProfile({ weeklyChangeKg: v })} />
      </Card>
      <Card>
        <T bold>{L.settings.lifestyle}</T>
        <Segmented value={profile.lifestyleFactor} onChange={(v) => updateProfile({ lifestyleFactor: v })} options={L.lifestyles.map((l) => ({ value: l.value, label: l.label }))} />
        <T dim size={12}>{L.lifestyles.find((l) => l.value === profile.lifestyleFactor)?.hint}</T>
        <NumSetting
          label={L.settings.bmrManual}
          value={profile.bmrOverride}
          onCommit={(v) => updateProfile({ bmrOverride: v && v > 500 ? v : undefined })}
          suffix={L.units.kcal}
          hint={L.settings.bmrManualHint(bmr({ ...profile, bmrOverride: undefined }))}
        />
      </Card>
    </>
  );
}

function AppearancePage() {
  const c = useTheme();
  const L = useT();
  const settings = useStore((s) => s.settings);
  return (
    <Card>
      <T bold>{L.settings.language}</T>
      <Segmented value={settings.language} onChange={(language) => updateSettings({ language })} options={(['system', 'ru', 'en'] as const).map((v) => ({ value: v, label: L.settings.languages[v] }))} />
      <Divider />
      <T bold>{L.settings.theme}</T>
      <Segmented value={settings.theme} onChange={(theme) => updateSettings({ theme })} options={(['system', 'light', 'dark'] as const).map((v) => ({ value: v, label: L.settings.themes[v] }))} />
      <T bold>{L.settings.accent}</T>
      <Row>
        {ACCENTS.map((a) => (
          <Tap key={a} onPress={() => updateSettings({ accent: a })} style={{ width: 38, height: 38, borderRadius: 19, backgroundColor: a, borderWidth: settings.accent === a ? 3 : 0, borderColor: c.text }}>
            <View />
          </Tap>
        ))}
      </Row>
      <Divider />
      <Toggle label={L.settings.animations} hint={L.settings.animationsHint} value={settings.animations} onChange={(v) => updateSettings({ animations: v })} />
      <Toggle label={L.settings.hints} hint={L.settings.hintsHint} value={settings.showHints} onChange={(v) => updateSettings({ showHints: v })} />
    </Card>
  );
}

function SectionsPage() {
  const L = useT();
  const sections = useStore((s) => s.settings.sections);
  return (
    <Card>
      <T dim size={13}>{L.settings.sectionsNote}</T>
      {(Object.keys(L.sectionsLong) as SectionId[]).map((id) => (
        <Toggle key={id} label={L.sectionsLong[id]} value={sections[id]} onChange={(v) => updateSettings({ sections: { ...sections, [id]: v } })} />
      ))}
    </Card>
  );
}

function NutritionPage() {
  const L = useT();
  const profile = useStore((s) => s.profile);
  const settings = useStore((s) => s.settings);
  const [key, setKey] = useState<NutrientKey>('sodium');
  const [cacheSize, setCacheSize] = useState(0);
  useEffect(() => {
    foodCacheSize().then(setCacheSize);
  }, []);
  const defaults = computeTargets(profile, { activityKcal: 0, adaptiveKcal: 0 });
  const override = settings.nutrientOverrides[key] ?? {};
  const unit = key === 'kcal' ? L.units.kcal : key === 'sodium' || key === 'potassium' || key === 'cholesterol' ? L.units.mg : L.units.g;
  const setOverride = (patch: { min?: number; max?: number }) => {
    const next = { ...override, ...patch };
    const all = { ...settings.nutrientOverrides, [key]: next };
    if (next.min == null && next.max == null) delete all[key];
    updateSettings({ nutrientOverrides: all });
  };
  return (
    <>
      <Card>
        <Toggle label={L.settings.adaptive} hint={L.settings.adaptiveHint} value={settings.adaptiveTargets} onChange={(v) => updateSettings({ adaptiveTargets: v })} />
        <Toggle label={L.settings.online} hint={L.settings.onlineHint} value={settings.onlineFoodSearch} onChange={(v) => updateSettings({ onlineFoodSearch: v })} />
        {settings.onlineFoodSearch && (
          <Field label={L.settings.usdaKey} hint={L.settings.usdaKeyHint} numeric={false} value={settings.usdaApiKey} onChange={(v) => updateSettings({ usdaApiKey: v.trim() })} />
        )}
        <T dim size={13}>{L.settings.foodCache(cacheSize)}</T>
        <Button title={L.settings.clearCache} kind="ghost" onPress={() => clearFoodCache().then(() => setCacheSize(0))} />
      </Card>
      <Card>
        <T bold>{L.settings.overrides}</T>
        <T dim size={13}>{L.settings.overridesNote}</T>
        <Row>
          {NUTRIENT_KEYS.map((k) => (
            <Chip key={k} label={`${L.nutrients[k].short}${settings.nutrientOverrides[k] ? ' •' : ''}`} active={key === k} onPress={() => setKey(k)} />
          ))}
        </Row>
        <View key={key}>
          <Row>
            <NumSetting label={L.settings.minOf(unit)} value={override.min} onCommit={(v) => setOverride({ min: v })} hint={L.settings.byDefault(defaults[key].min)} />
            <NumSetting label={L.settings.maxOf(unit)} value={override.max} onCommit={(v) => setOverride({ max: v })} hint={L.settings.byDefault(defaults[key].max)} />
          </Row>
        </View>
        <Button title={L.settings.resetOverrides} kind="ghost" onPress={() => updateSettings({ nutrientOverrides: {} })} />
      </Card>
    </>
  );
}

function WaterActivityPage() {
  const L = useT();
  const settings = useStore((s) => s.settings);
  const [presets, setPresets] = useState(settings.waterPresets.join(', '));
  return (
    <Card>
      <Field
        label={L.settings.waterPresets}
        numeric={false}
        value={presets}
        onChange={(v) => {
          setPresets(v);
          const list = v.split(/[,;\s]+/).map(Number).filter((n) => n > 0 && n < 5000);
          if (list.length) updateSettings({ waterPresets: list.slice(0, 6) });
        }}
      />
      <NumSetting label={L.settings.waterDanger} value={settings.waterDangerMl} onCommit={(v) => v && v >= 2000 && updateSettings({ waterDangerMl: v })} suffix={L.units.ml} />
      <NumSetting label={L.settings.stepGoal} value={settings.stepGoal} onCommit={(v) => v && updateSettings({ stepGoal: v })} suffix={L.units.steps} />
    </Card>
  );
}

function StepsPage() {
  const c = useTheme();
  const L = useT();
  const source = useStore((s) => s.settings.stepsSource);
  const [status, setStatus] = useState<HcStatus | 'checking'>('checking');
  const [bgOk, setBgOk] = useState<boolean | null>(null);
  const supported = deviceStepsSupported();
  const refresh = useCallback(() => {
    hcStatus().then(setStatus);
    backgroundTasksAvailable().then(setBgOk);
  }, []);

  useEffect(() => {
    refresh();
    // Re-check after the user returns from the Play Store / Health Connect / system screens.
    const sub = AppState.addEventListener('change', (s) => s === 'active' && refresh());
    return () => sub.remove();
  }, [refresh]);

  const connect = async () => {
    if (await hcConnect()) {
      updateSettings({ stepsSource: 'healthConnect' });
      hcSyncSteps(30);
    }
    refresh();
  };

  return (
    <>
      <Card>
        <T bold>{L.settings.stepsSource}</T>
        <Segmented
          value={source}
          onChange={(v) => {
            updateSettings({ stepsSource: v });
            if (v === 'device') requestStepPermission();
            if (v === 'healthConnect' && status !== 'connected') connect();
          }}
          options={[{ value: 'device', label: L.settings.sourceDevice }, { value: 'healthConnect', label: L.settings.sourceHc }]}
        />
        {(source === 'device' ? L.activity.info.device : L.activity.info.hc).map((p, i) => (
          <T key={i} dim size={13}>{p}</T>
        ))}
      </Card>

      {source === 'device' && (
        <Card>
          <T bold color={supported ? c.ok : c.danger}>{supported ? L.settings.deviceNote : L.settings.deviceUnsupported}</T>
          {bgOk != null ? <T size={13} color={bgOk ? c.ok : c.high}>{bgOk ? L.settings.backgroundOk : L.settings.backgroundRestricted}</T> : null}
          <T dim size={13}>{L.settings.batteryHint}</T>
          <Button title={L.activity.info.battery} icon="battery" onPress={openAutostartSettings} />
          <Button title={L.settings.appSettings} kind="secondary" onPress={openAppSettings} />
          <Button title={L.activity.sync} icon="refresh" kind="ghost" onPress={() => syncDeviceSteps()} />
        </Card>
      )}

      <Card>
        <T bold color={status === 'connected' ? c.ok : status === 'checking' ? c.textDim : c.high}>Health Connect: {L.settings.hcStatus[status]}</T>
        {status === 'unavailable' || status === 'update' ? <Button title={L.settings.hcInstall} icon="plus" onPress={hcInstall} /> : null}
        {status === 'noPermission' ? <Button title={L.settings.hcConnect} onPress={connect} /> : null}
        {status === 'connected' ? <Button title={L.activity.sync} icon="refresh" kind="secondary" onPress={() => hcSyncSteps(30)} /> : null}
        {status !== 'unavailable' && status !== 'checking' ? <Button title={L.settings.hcOpen} kind="ghost" onPress={hcOpenSettings} /> : null}
        <Hint title="Health Connect">{L.settings.hcNeedsSource}</Hint>
      </Card>
    </>
  );
}

function NotificationsPage() {
  const L = useT();
  const n = useStore((s) => s.settings.notifications);
  const set = (patch: Partial<typeof n>) => updateSettings({ notifications: { ...n, ...patch } });
  return (
    <Card>
      <T dim size={13}>{notificationsAvailable() ? L.settings.notifNote : `${L.settings.notifNote} ${L.settings.notifUnavailable}`}</T>
      <Toggle label={L.settings.notifEnabled} value={n.enabled} onChange={(v) => set({ enabled: v })} />
      {n.enabled && (
        <>
          <Toggle label={L.settings.waterReminders} value={n.waterReminders} onChange={(v) => set({ waterReminders: v })} />
          <Toggle label={L.settings.weighIn} value={n.weighInReminder} onChange={(v) => set({ weighInReminder: v })} />
        </>
      )}
    </Card>
  );
}

function DataPage() {
  const L = useT();
  const [busy, setBusy] = useState(false);
  const run = async (fn: () => Promise<unknown>, ok?: (r: unknown) => string | null) => {
    setBusy(true);
    try {
      const msg = ok?.(await fn());
      if (msg) Alert.alert(L.common.done, msg);
    } catch (e) {
      const key = e instanceof Error ? e.message : String(e);
      const text = (L.settings as Record<string, unknown>)[key];
      Alert.alert(L.common.error, typeof text === 'string' ? text : key);
    } finally {
      setBusy(false);
    }
  };
  const W = L.settings.wipeLabels;
  return (
    <>
      <Card>
        <T dim size={13}>{L.settings.dataNote}</T>
        <Button title={L.settings.export} icon="database" onPress={() => run(exportData)} disabled={busy} />
        <Button
          title={L.settings.import}
          kind="secondary"
          disabled={busy}
          onPress={() => confirm(L, L.settings.importTitle, L.settings.importText, () => run(importData, (ok) => (ok ? L.settings.restored : null)))}
        />
        <Button title={L.settings.importPack} kind="secondary" disabled={busy} onPress={() => run(importFoodPack, (n) => (n == null ? null : L.settings.packAdded(n as number)))} />
        <Hint title={L.settings.packHintTitle}>{L.settings.packHint}</Hint>
      </Card>
      <Card>
        <T bold>{L.settings.wipe}</T>
        <Row>
          {(Object.keys(W) as (keyof typeof W)[]).map((k) => (
            <Chip key={k} label={W[k]} onPress={() => confirm(L, L.settings.wipeTitle, L.settings.wipeText(W[k]), () => wipeSection(k))} />
          ))}
        </Row>
        <Button title={L.settings.redoOnboarding} kind="ghost" onPress={() => router.push('/onboarding')} />
        <Button
          title={L.settings.wipeAll}
          kind="danger"
          onPress={() =>
            confirm(L, L.settings.wipeAllTitle, L.settings.wipeAllText, () => {
              replaceState(createDefaultData());
              router.replace('/onboarding');
            })
          }
        />
      </Card>
    </>
  );
}

function SupportPage() {
  const L = useT();
  const enabled = useStore((s) => s.settings.donationPrompts);
  const meta = useStore((s) => s.meta);
  return (
    <Card>
      <Toggle label={L.settings.donations} hint={L.settings.donationsHint} value={enabled} onChange={(v) => updateSettings({ donationPrompts: v })} />
      <T dim size={13}>{L.settings.usage(meta.usageDays.length, meta.donationShown.length, pendingMilestone(meta.usageDays.length, meta.donationShown) != null)}</T>
      <Button title={L.settings.resetDonations} kind="ghost" onPress={resetDonationPrompts} />
    </Card>
  );
}

const PAGES: Record<SettingsPage, () => React.JSX.Element> = {
  profile: ProfilePage,
  goal: GoalPage,
  appearance: AppearancePage,
  sections: SectionsPage,
  nutrition: NutritionPage,
  waterActivity: WaterActivityPage,
  steps: StepsPage,
  notifications: NotificationsPage,
  data: DataPage,
  support: SupportPage,
};

export default function SettingsSection() {
  const L = useT();
  const { section } = useLocalSearchParams<{ section: SettingsPage }>();
  const Page = PAGES[section] ?? ProfilePage;
  return (
    <Screen>
      <Stack.Screen options={{ title: L.more[section] ?? '' }} />
      <Page />
    </Screen>
  );
}
