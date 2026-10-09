/**
 * Create or edit a food in the local database. In "quick" mode the values are per portion
 * and the entry is logged immediately without polluting the database.
 */
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert } from 'react-native';

import { defaultMeal } from '../components/AmountSheet';
import { Button, Card, Field, Hint, parseNum, Row, Screen, T } from '../components/ui';
import { useT } from '../i18n';
import { NUTRIENT_KEYS } from '../lib/calc';
import { todayKey } from '../lib/date';
import { deleteFood, logFood, saveFood } from '../store/actions';
import { uid, useStore } from '../store/store';
import type { Food, NutrientKey } from '../store/types';

export default function FoodEdit() {
  const L = useT();
  const params = useLocalSearchParams<{ id?: string; quick?: string; date?: string }>();
  const quick = params.quick === '1';
  const existing = useStore((s) => s.foods.find((f) => f.id === params.id));
  const E = L.food.edit;
  const [name, setName] = useState(existing?.name ?? (quick ? E.defaultQuickName : ''));
  const [brand, setBrand] = useState(existing?.brand ?? '');
  const [serving, setServing] = useState(existing?.servingG ? String(existing.servingG) : '');
  const [values, setValues] = useState<Record<NutrientKey, string>>(
    () => Object.fromEntries(NUTRIENT_KEYS.map((k) => [k, existing?.per100g[k] != null ? String(existing.per100g[k]) : ''])) as Record<NutrientKey, string>,
  );
  const unit = (k: NutrientKey) => (k === 'kcal' ? L.units.kcal : k === 'sodium' || k === 'potassium' || k === 'cholesterol' ? L.units.mg : L.units.g);

  const save = () => {
    if (!name.trim()) return Alert.alert(E.needName);
    const per100g = Object.fromEntries(NUTRIENT_KEYS.map((k) => [k, parseNum(values[k])]).filter(([, v]) => v != null));
    if (per100g.kcal == null) return Alert.alert(E.needKcal);
    const food: Food = {
      id: existing?.id ?? `custom-${uid()}`,
      name: name.trim(),
      brand: brand.trim() || undefined,
      source: existing?.source ?? 'custom',
      barcode: existing?.barcode,
      servingG: parseNum(serving),
      per100g,
    };
    // Quick mode: values are per portion → log 100 "grams" of a food whose per-100g equals the portion.
    if (quick) logFood({ ...food, servingG: undefined }, 100, defaultMeal(), params.date || todayKey());
    else saveFood(food);
    router.back();
  };

  const remove = () =>
    Alert.alert(E.deleteTitle, E.deleteText, [
      { text: L.common.cancel, style: 'cancel' },
      { text: L.common.delete, style: 'destructive', onPress: () => { deleteFood(existing!.id); router.back(); } },
    ]);

  return (
    <Screen>
      <Stack.Screen options={{ title: quick ? E.quickTitle : existing ? E.editTitle : E.newTitle }} />
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
      {existing && <Button title={E.deleteFood} kind="danger" onPress={remove} />}
    </Screen>
  );
}
