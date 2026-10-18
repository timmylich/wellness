/**
 * Food picker. Designed for speed without AI: favorites and recent foods are one tap away,
 * and search shows local and online results in one list (local first, online appended).
 */
import { router, Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { AmountSheet, defaultMeal } from '../components/AmountSheet';
import { Icon } from '../components/Icon';
import { Button, Card, Empty, Field, fmt, H, Row, Screen, Segmented, T, Tap } from '../components/ui';
import { useLang, useT } from '../i18n';
import { todayKey } from '../lib/date';
import { cachedFoods, cachedQuery } from '../lib/foodCache';
import { takeHandedOffFood } from '../lib/foodHandoff';
import { filterOnline, lookupBarcode, mergeResults, searchLocal, searchOnline } from '../lib/foodApi';
import { animateLayout } from '../lib/motion';
import { alpha, useTheme } from '../lib/theme';
import { logFood, saveFood, toggleFavorite } from '../store/actions';
import { useStore } from '../store/store';
import type { Food } from '../store/types';

/** How many recent foods the picker lists. */
const RECENT_MAX = 15;

function FoodRow({ food, fav, onPress }: { food: Food; fav: boolean; onPress: () => void }) {
  const c = useTheme();
  const L = useT();
  const p = food.per100g;
  return (
    <Tap scaleTo={0.98} onPress={onPress} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 10, gap: 8, borderBottomWidth: 0.5, borderColor: c.border }}>
      <View style={{ flex: 1 }}>
        <T>{food.name}</T>
        <T dim size={12}>
          {food.brand ? `${food.brand} · ` : ''}
          {fmt(p.kcal ?? 0)} {L.units.kcal} · {L.nutrients.protein.short} {fmt(p.protein ?? 0, 1)} {L.nutrients.fat.short} {fmt(p.fat ?? 0, 1)}{' '}
          {L.nutrients.carbs.short} {fmt(p.carbs ?? 0, 1)} {L.food.per100}
        </T>
      </View>
      <View style={{ alignItems: 'flex-end', gap: 3 }}>
        <View style={{ backgroundColor: c.cardAlt, borderRadius: 8, paddingHorizontal: 6, paddingVertical: 2 }}>
          <T size={10} dim>{L.food.sources[food.source]}</T>
        </View>
        {/* Zero/missing salt is usually missing data, not a salt-free product. */}
        {!p.sodium ? (
          <View style={{ backgroundColor: alpha(c.high, 0.15), borderRadius: 8, paddingHorizontal: 6, paddingVertical: 2 }}>
            <T size={10} color={c.high}>{L.food.noSalt}</T>
          </View>
        ) : null}
      </View>
      <Tap onPress={() => toggleFavorite(food.id)} hitSlop={10}>
        <Icon name="star" size={20} color={fav ? '#F5B301' : c.textDim} />
      </Tap>
    </Tap>
  );
}

export default function FoodSearch() {
  const c = useTheme();
  const L = useT();
  const lang = useLang();
  const params = useLocalSearchParams<{ date?: string }>();
  const date = params.date ?? todayKey();
  const userFoods = useStore((s) => s.foods);
  const favorites = useStore((s) => s.favorites);
  const foodLog = useStore((s) => s.foodLog);
  const online = useStore((s) => s.settings.onlineFoodSearch);
  const [query, setQuery] = useState('');
  const [tab, setTab] = useState<'recent' | 'fav' | 'mine'>('recent');
  // Online results are tagged with the query they belong to, so stale ones are ignored.
  const [remote, setRemote] = useState<{ q: string; foods: Food[]; error?: boolean } | null>(null);
  const [barcode, setBarcode] = useState('');
  const [barcodeBusy, setBarcodeBusy] = useState(false);
  const [barcodeError, setBarcodeError] = useState(false);
  const [selected, setSelected] = useState<Food | null>(null);
  // Back from "Create product": open the amount sheet for the new food.
  useFocusEffect(
    useCallback(() => {
      const f = takeHandedOffFood();
      if (f) setSelected(f);
    }, []),
  );

  // Typing stays responsive: the result list renders from a deferred copy of the query.
  const q = useDeferredValue(query.trim());
  // Heavy lists render after the screen's open animation has started.
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setReady(true), 250);
    return () => clearTimeout(t);
  }, []);
  const usdaKey = useStore((s) => s.settings.usdaApiKey);
  // Previously seen online foods: searchable offline.
  const [cached, setCached] = useState<Food[]>([]);
  useEffect(() => {
    cachedFoods().then(setCached);
  }, []);
  const local = useMemo(() => searchLocal(q, userFoods, lang, cached), [q, userFoods, lang, cached]);
  const canSearchOnline = online && q.length >= 3;
  useEffect(() => {
    if (!canSearchOnline) return;
    const ctrl = new AbortController();
    // Show cached results for this exact query instantly, then refresh from the network.
    cachedQuery(q).then((hit) => {
      if (hit && !ctrl.signal.aborted) setRemote((r) => (r?.q === q ? r : { q, foods: hit }));
    });
    const timer = setTimeout(async () => {
      try {
        const foods = await searchOnline(q, ctrl.signal, usdaKey);
        animateLayout();
        setRemote({ q, foods });
      } catch {
        if (!ctrl.signal.aborted) setRemote((r) => (r?.q === q && r.foods.length ? r : { q, foods: [], error: true }));
      }
    }, 700);
    return () => {
      clearTimeout(timer);
      ctrl.abort();
    };
  }, [q, canSearchOnline, usdaKey]);
  const current = remote?.q === q ? remote : null;
  const loading = canSearchOnline && !current;
  const results = mergeResults(local, filterOnline(current?.foods ?? [], q));

  // Most recent first, unique by food id.
  const recent = useMemo(() => {
    const seen = new Set<string>();
    const out: Food[] = [];
    for (let i = foodLog.length - 1; i >= 0 && out.length < RECENT_MAX; i--) {
      const f = foodLog[i].food;
      if (!seen.has(f.id)) {
        seen.add(f.id);
        out.push(f);
      }
    }
    return out;
  }, [foodLog]);
  const known = useMemo(() => new Map([...recent, ...userFoods].map((f) => [f.id, f])), [recent, userFoods]);
  const favFoods = favorites.map((id) => known.get(id)).filter((f): f is Food => !!f);

  const findBarcode = async () => {
    const code = barcode.trim();
    if (!code) return;
    const hit = userFoods.find((f) => f.barcode === code);
    if (hit) return setSelected(hit);
    setBarcodeBusy(true);
    setBarcodeError(false);
    const f = await lookupBarcode(code).catch(() => null);
    setBarcodeBusy(false);
    if (f) setSelected(f);
    else setBarcodeError(true);
  };

  const list = (foods: Food[]) => foods.map((f) => <FoodRow key={f.id} food={f} fav={favorites.includes(f.id)} onPress={() => setSelected(f)} />);

  return (
    <Screen>
      <Stack.Screen options={{ title: L.nutrition.addFood }} />
      <Field value={query} onChange={setQuery} numeric={false} placeholder={L.food.searchPlaceholder} search />
      <Row style={{ flexWrap: 'nowrap' }}>
        <Button title={L.food.create} icon="plus" kind="secondary" onPress={() => router.push('/food-edit')} style={{ flex: 1 }} />
        <Button title={L.food.quick} icon="flame" kind="secondary" onPress={() => router.push({ pathname: '/food-edit', params: { quick: '1', date } })} style={{ flex: 1 }} />
      </Row>
      {online && (
        <Card>
          <H icon="barcode">{L.food.barcode}</H>
          <Row style={{ flexWrap: 'nowrap' }}>
            <Field value={barcode} onChange={setBarcode} placeholder="4601234567890" />
            <Button title={L.food.find} onPress={findBarcode} disabled={barcodeBusy} />
          </Row>
          {barcodeBusy ? <ActivityIndicator color={c.accent} /> : null}
          {barcodeError ? <T color={c.danger} size={13}>{L.food.barcodeMissing}</T> : null}
        </Card>
      )}

      {!ready ? (
        <ActivityIndicator color={c.accent} style={{ marginTop: 24 }} />
      ) : q ? (
        <Card>
          {list(results)}
          {loading ? (
            <Row style={{ justifyContent: 'center', paddingVertical: 8 }}>
              <ActivityIndicator color={c.accent} />
              <T dim size={13}>{L.food.searchingOnline}</T>
            </Row>
          ) : null}
          {current?.error ? <T color={c.danger} size={13}>{L.food.onlineError}</T> : null}
          {online && q.length < 3 ? <T dim size={12}>{L.food.minChars}</T> : null}
          {!loading && !results.length ? <Empty text={L.food.nothing} /> : null}
        </Card>
      ) : (
        <Card>
          <Segmented
            value={tab}
            onChange={(v) => {
              animateLayout();
              setTab(v);
            }}
            options={[
              { value: 'recent', label: L.food.recent },
              { value: 'fav', label: L.food.favorites },
              { value: 'mine', label: L.food.mine },
            ]}
          />
          {tab === 'recent' && (recent.length ? list(recent) : <Empty text={L.food.recentEmpty} />)}
          {tab === 'fav' && (favFoods.length ? list(favFoods) : <Empty text={L.food.favEmpty} />)}
          {tab === 'mine' &&
            (userFoods.length ? (
              userFoods.map((f) => (
                <View key={f.id}>
                  <FoodRow food={f} fav={favorites.includes(f.id)} onPress={() => setSelected(f)} />
                  <Tap onPress={() => router.push({ pathname: '/food-edit', params: { id: f.id } })}>
                    <T size={12} color={c.accent}>{L.common.edit}</T>
                  </Tap>
                </View>
              ))
            ) : (
              <Empty text={L.food.mineEmpty} />
            ))}
        </Card>
      )}


      <AmountSheet
        food={selected}
        onClose={() => setSelected(null)}
        onSubmit={(grams, pieces) => {
          const food = selected!;
          setSelected(null);
          router.back();
          // Write after the back transition has started, so re-rendering the Food section
          // does not stall the animation.
          setTimeout(() => {
            // Keep online foods locally so they work offline and show up in "My foods".
            if (food.source === 'off' || food.source === 'usda') saveFood(food);
            logFood(food, grams, defaultMeal(), date, pieces);
          }, 350);
        }}
      />
    </Screen>
  );
}
