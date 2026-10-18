/** First-run wizard: collects the profile used for every recommendation in the app. */
import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { Logo } from '../components/Logo';
import { PaceSlider } from '../components/PaceSlider';
import { Button, Card, Field, fmt, Hint, parseNum, Row, Screen, Segmented, Stat, T, Toggle } from '../components/ui';
import { useT } from '../i18n';
import { baseExpenditure, bmi, bmiCategory, bmr, dailyCalorieTarget, healthyWeight } from '../lib/calc';
import { animateLayout } from '../lib/motion';
import { useTheme } from '../lib/theme';
import { completeOnboarding, updateProfile, updateSettings } from '../store/actions';
import { useStore } from '../store/store';
import type { Profile, SectionId } from '../store/types';

const STEPS = 5;

export default function Onboarding() {
  const c = useTheme();
  const L = useT();
  const saved = useStore((s) => s.profile);
  const sections = useStore((s) => s.settings.sections);
  const settings = useStore((s) => s.settings);
  const language = useStore((s) => s.settings.language);
  const [step, setStep] = useState(0);
  // Text fields are kept as strings so the user can type freely.
  const [f, setF] = useState({
    name: saved.name,
    birthYear: String(saved.birthYear),
    heightCm: String(saved.heightCm),
    weightKg: String(saved.weightKg),
    targetWeightKg: String(saved.targetWeightKg),
  });
  const [sex, setSex] = useState(saved.sex);
  const [pace, setPace] = useState(saved.weeklyChangeKg);
  const [lifestyle, setLifestyle] = useState(saved.lifestyleFactor);

  const profile: Profile = {
    ...saved,
    name: f.name.trim(),
    sex,
    birthYear: parseNum(f.birthYear) ?? saved.birthYear,
    heightCm: parseNum(f.heightCm) ?? saved.heightCm,
    weightKg: parseNum(f.weightKg) ?? saved.weightKg,
    targetWeightKg: parseNum(f.targetWeightKg) ?? parseNum(f.weightKg) ?? saved.targetWeightKg,
    weeklyChangeKg: pace,
    lifestyleFactor: lifestyle,
  };
  const set = (k: keyof typeof f) => (v: string) => setF({ ...f, [k]: v });
  const go = (n: number) => {
    animateLayout(260);
    setStep(n);
  };
  const next = () => {
    updateProfile(profile);
    if (step < STEPS - 1) go(step + 1);
    else {
      completeOnboarding();
      router.replace('/');
    }
  };

  const hw = healthyWeight(profile.heightCm, sex);
  const b = bmi(profile.weightKg, profile.heightCm);

  return (
    <Screen>
      <View style={{ alignItems: 'center', gap: 8, marginTop: 24 }}>
        <Logo size={64} color={c.accent} />
        <T size={13} dim>{L.onboarding.step(step + 1, STEPS)}</T>
        <View style={{ flexDirection: 'row', gap: 6 }}>
          {Array.from({ length: STEPS }, (_, i) => (
            <View key={i} style={{ width: i === step ? 40 : 24, height: 4, borderRadius: 2, backgroundColor: i <= step ? c.accent : c.border }} />
          ))}
        </View>
      </View>

      {step === 0 && (
        <Card>
          <T size={24} bold>{L.onboarding.welcomeTitle}</T>
          <T dim>{L.onboarding.welcomeText}</T>
          <T dim size={13}>{L.onboarding.language}</T>
          <Segmented value={language} onChange={(v) => updateSettings({ language: v })} options={(['system', 'ru', 'en'] as const).map((v) => ({ value: v, label: L.settings.languages[v] }))} />
          <Field label={L.onboarding.name} value={f.name} onChange={set('name')} numeric={false} />
          <T dim size={13}>{L.onboarding.sex}</T>
          <Segmented value={sex} onChange={setSex} options={[{ value: 'male', label: L.onboarding.male }, { value: 'female', label: L.onboarding.female }]} />
          <Field label={L.onboarding.birthYear} value={f.birthYear} onChange={set('birthYear')} />
        </Card>
      )}

      {step === 1 && (
        <Card>
          <T size={22} bold>{L.onboarding.bodyTitle}</T>
          <Row>
            <Field label={L.onboarding.height} value={f.heightCm} onChange={set('heightCm')} suffix={L.units.cm} />
            <Field label={L.onboarding.weight} value={f.weightKg} onChange={set('weightKg')} suffix={L.units.kg} />
          </Row>
          <Row>
            <Stat label={L.onboarding.bmi} value={fmt(b, 1)} />
            <Stat label={L.onboarding.category} value={L.body.bmiCat[bmiCategory(b).key]} />
          </Row>
          <T dim size={13}>{L.onboarding.healthyRange(hw.min, hw.max)}</T>
          <Hint title={L.onboarding.bmiHintTitle}>{L.onboarding.bmiHint}</Hint>
        </Card>
      )}

      {step === 2 && (
        <Card>
          <T size={22} bold>{L.onboarding.goalTitle}</T>
          <Field label={L.onboarding.targetWeight} value={f.targetWeightKg} onChange={set('targetWeightKg')} suffix={L.units.kg} />
          <T bold>{L.pace.title}</T>
          <PaceSlider profile={profile} onChange={setPace} />
        </Card>
      )}

      {step === 3 && (
        <Card>
          <T size={22} bold>{L.onboarding.activityTitle}</T>
          <T dim size={13}>{L.onboarding.activityText}</T>
          {L.lifestyles.map((l) => (
            <Button key={l.value} title={`${l.label} — ${l.hint}`} kind={lifestyle === l.value ? 'primary' : 'secondary'} onPress={() => setLifestyle(l.value)} />
          ))}
          <Row>
            <Stat label={L.onboarding.bmrStat} value={fmt(bmr(profile))} unit={L.units.kcal} />
            <Stat label={L.onboarding.baseStat} value={fmt(baseExpenditure(profile))} unit={L.units.kcal} />
          </Row>
          <Hint title={L.onboarding.bmrHintTitle}>{L.onboarding.bmrHint}</Hint>
        </Card>
      )}

      {step === 4 && (
        <Card>
          <T size={22} bold>{L.onboarding.finishTitle}</T>
          <T dim size={13}>{L.onboarding.finishText}</T>
          {(Object.keys(L.sectionsLong) as SectionId[]).map((id) => (
            <Toggle key={id} label={L.sectionsLong[id]} value={sections[id]} onChange={(v) => updateSettings({ sections: { ...sections, [id]: v } })} />
          ))}
          <T bold>{L.settings.theme}</T>
          <Segmented
            value={settings.theme}
            onChange={(theme) => updateSettings({ theme })}
            options={(['system', 'light', 'dark'] as const).map((v) => ({ value: v, label: L.settings.themes[v] }))}
          />
          <Toggle label={L.settings.amoled} hint={L.settings.amoledHint} value={settings.amoled} onChange={(amoled) => updateSettings({ amoled })} />
          <Toggle label={L.settings.adaptive} hint={L.settings.adaptiveHint} value={settings.adaptiveTargets} onChange={(adaptiveTargets) => updateSettings({ adaptiveTargets })} />
          <View style={{ backgroundColor: c.accentSoft, borderRadius: 12, padding: 12, gap: 4 }}>
            <T bold>{L.onboarding.yourNorm}</T>
            {(() => {
              const r = dailyCalorieTarget(profile, { activityKcal: 0, adaptiveKcal: 0 });
              return <T>{L.onboarding.normLine(r.target, r.min, r.max)}</T>;
            })()}
            <T dim size={12}>{L.onboarding.normNote}</T>
          </View>
        </Card>
      )}

      <Row style={{ flexWrap: 'nowrap' }}>
        {step > 0 && <Button title={L.common.back} kind="secondary" onPress={() => go(step - 1)} style={{ flex: 1 }} />}
        <Button title={step === STEPS - 1 ? L.common.start : L.common.next} onPress={next} style={{ flex: 2 }} />
      </Row>
      <T dim size={11} center>{L.common.disclaimer}</T>
    </Screen>
  );
}
