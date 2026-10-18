/**
 * Create or edit a food in the local database. In "quick" mode the values are per portion
 * and the entry is logged immediately without polluting the database. With `entry`, edits
 * the food snapshot of one log entry only (which also updates it in "Recent").
 * A new food can start from a "base" product whose values fill the form.
 */
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';

import { confirmDialog, showDialog } from '../components/Dialog';
import { defaultMeal } from '../components/AmountSheet';
import { Button, Card, Field, fmt, Hint, parseNum, Row, Screen, T, Tap } from '../components/ui';
import { useLang, useT } from '../i18n';
import { searchLocal } from '../lib/foodApi';
import { handOffFood } from '../lib/foodHandoff';
import { useTheme } from '../lib/theme';
import { NUTRIENT_KEYS } from '../lib/calc';
import { todayKey } from '../lib/date';
import { deleteFood, logFood, saveFood, updateFoodEntry } from '../store/actions';
import { uid, useStore } from '../store/store';
import type { Food, NutrientKey } from '../store/types';

export default function FoodEdit() {
  const c = useTheme();
  const L = useT();
  const lang = useLang();
  const params = useLocalSearchParams<{ id?: string; quick?: string; date?: string; entry?: string }>();
  const quick = params.quick === '1';
  const entry = useStore((s) => (params.entry ? s.foodLog.find((e) => e.id === params.entry) : undefined));
  const saved = useStore((s) => s.foods.find((f) => f.id === params.id));
  const userFoods = useStore((s) => s.foods);
  const existing = entry?.food ?? saved;
  const E = L.food.edit;
  const [name, setName] = useState(existing?.name ?? (quick ? E.defaultQuickName : ''));
  const [brand, setBrand] = useState(existing?.brand ?? '');
  const [serving, setServing] = useState(existing?.servingG ? String(existing.servingG) : '');
  const [values, setValues] = useState<Record<NutrientKey, string>>(() => valuesOf(existing));
  // Base product picker (new foods only).
  const [baseQuery, setBaseQuery] = useState('');
  const [baseName, setBaseName] = useState<string | null>(null);
  const baseResults = useMemo(() => searchLocal(baseQuery.trim(), userFoods, lang).slice(0, 6), [baseQuery, userFoods, lang]);
  const applyBase = (f: Food) => {
    setValues(valuesOf(f));
    if (!name.trim()) setName(f.name);
    if (!serving && f.servingG) setServing(String(f.servingG));
    setBaseName(f.name);
    setBaseQuery('');
  };
  const unit = (k: NutrientKey) => (k === 'kcal' ? L.units.kcal : k === 'sodium' || k === 'potassium' || k === 'cholesterol' ? L.units.mg : L.units.g);

  const save = () => {
    if (!name.trim()) return showDialog({ title: E.needName, icon: 'info' });
    const per100g = Object.fromEntries(NUTRIENT_KEYS.map((k) => [k, parseNum(values[k])]).filter(([, v]) => v != null));
    if (per100g.kcal == null) return showDialog({ title: E.needKcal, icon: 'info' });
    const food: Food = {
      id: existing?.id ?? `custom-${uid()}`,
      name: name.trim(),
      brand: brand.trim() || undefined,
      source: existing?.source ?? 'custom',
      barcode: existing?.barcode,
      servingG: parseNum(serving),
      per100g,
    };
    if (entry) {
      updateFoodEntry(entry.id, { food: { ...food, id: entry.food.id, source: entry.food.source } });
      router.back();
      return;
    }
    // Quick mode: values are per portion → log 100 "grams" of a food whose per-100g equals the portion.
    if (quick) logFood({ ...food, servingG: undefined }, 100, defaultMeal(), params.date || todayKey());
    else {
      saveFood(food);
      // New product: the picker opens its amount sheet right away.
      if (!existing) handOffFood(food);
    }
    router.back();
  };

  const remove = () =>
    confirmDialog({
      title: E.deleteTitle,
      message: E.deleteText,
      cancel: L.common.cancel,
      ok: L.common.delete,
      onOk: () => {
        deleteFood(existing!.id);
        router.back();
      },
    });

  return (
    <Screen>
      <Stack.Screen options={{ title: quick ? E.quickTitle : existing ? E.editTitle : E.newTitle }} />
      {entry ? <T dim size={13}>{L.food.entryEditNote}</T> : null}
      {!existing && !quick && (
        <Card>
          <T size={16} bold>{L.food.base}</T>
          <T dim size={13}>{baseName ? `✓ ${baseName}` : L.food.baseHint}</T>
          <Field value={baseQuery} onChange={setBaseQuery} numeric={false} placeholder={L.food.basePick} search />
          {baseResults.map((f) => (
            <Tap key={f.id} scaleTo={1} onPress={() => applyBase(f)} style={{ paddingVertical: 8, borderBottomWidth: 0.5, borderColor: c.border }}>
              <T>{f.name}</T>
              <T dim size={12}>{f.brand ? `${f.brand} · ` : ''}{fmt(f.per100g.kcal ?? 0)} {L.units.kcal}{L.food.per100}</T>
            </Tap>
          ))}
        </Card>
      )}
      <Card>
        <Field label={E.name} value={name} onChange={setName} numeric={false} />
        {!quick && (
          <Row>
            <Field label={E.brand} value={brand} onChange={setBrand} numeric={false} />
            <Field label={E.serving} value={serving} onChange={setServing} suffix={L.units.g} />
          </Row>
        )}
      </Card>
      <Card>
        <T size={18} bold>{quick ? E.perPortion : E.per100}</T>
        <Hint title={E.hintTitle}>{E.hint}</Hint>
        <Row>
          {NUTRIENT_KEYS.map((k) => (
            <Field key={k} label={L.nutrients[k].label} value={values[k]} onChange={(v) => setValues({ ...values, [k]: v })} suffix={unit(k)} />
          ))}
        </Row>
        {values.sodium === '' && <T dim size={12}>{E.saltNote}</T>}
      </Card>
      <Button title={quick ? E.addToLog : L.common.save} big onPress={save} />
      {saved && !entry && <Button title={E.deleteFood} kind="danger" onPress={remove} />}
    </Screen>
  );
}

function valuesOf(f: Food | undefined): Record<NutrientKey, string> {
  return Object.fromEntries(NUTRIENT_KEYS.map((k) => [k, f?.per100g[k] != null ? String(f.per100g[k]) : ''])) as Record<NutrientKey, string>;
}
