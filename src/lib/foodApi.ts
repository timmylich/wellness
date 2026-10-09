/**
 * Food search: local database first (user foods, bundled foods, cached online results), then
 * online sources — Open Food Facts (free, open, community-maintained, good coverage of CIS
 * products including chains such as Dodo Pizza) and USDA FoodData Central (public domain).
 *
 * No API keys, no accounts, no tracking. Online lookups can be disabled in settings.
 */
import { builtinFoods, ciqualFoods } from '../data/foods';
import type { Food, Nutrients } from '../store/types';
import { cachedBarcode, storeFood, storeQuery } from './foodCache';

const USER_AGENT = 'Wellness/1.0 (open-source health tracker)';
const SEARCH_URL = 'https://search.openfoodfacts.org/search';
const PRODUCT_URL = 'https://world.openfoodfacts.org/api/v2/product';
// Names in the languages of our main audience (CIS, Caucasus, Europe) for display and search.
const NAME_LANGS = ['ru', 'uk', 'be', 'kk', 'ka', 'hy', 'en', 'de', 'fr', 'pl'];
const FIELDS = `code,product_name,${NAME_LANGS.map((l) => `product_name_${l}`).join(',')},brands,nutriments,serving_quantity`;

/** Case/ё-insensitive normalization for local matching. */
function norm(s: string): string {
  return s.toLowerCase().replace(/ё/g, 'е').trim();
}

/** Every query word must appear in the name, brand or an alias (any language). */
export function searchLocal(query: string, userFoods: Food[], lang: 'ru' | 'en', cached: Food[] = []): Food[] {
  const words = norm(query).split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const userIds = new Set(userFoods.map((f) => f.id));
  const all = [...userFoods, ...builtinFoods(lang), ...cached.filter((f) => !userIds.has(f.id)), ...ciqualFoods()];
  return all.filter((f) => {
    const hay = norm(`${f.name} ${f.brand ?? ''} ${(f.aliases ?? []).join(' ')}`);
    return words.every((w) => hay.includes(w));
  });
}

/** Merge local and online results into one list: local first, online duplicates dropped. */
export function mergeResults(local: Food[], online: Food[]): Food[] {
  const seen = new Set(local.map((f) => f.id));
  const names = new Set(local.map((f) => norm(`${f.name}|${f.brand ?? ''}`)));
  return [...local, ...online.filter((f) => !seen.has(f.id) && !names.has(norm(`${f.name}|${f.brand ?? ''}`)))];
}

type OffNutriments = Record<string, number | string | undefined>;

interface OffProduct {
  code?: string;
  product_name?: string;
  product_name_ru?: string;
  [key: `product_name_${string}`]: string | undefined;
  brands?: string | string[];
  nutriments?: OffNutriments;
  serving_quantity?: number | string;
}

function num(v: unknown): number | undefined {
  const n = typeof v === 'string' ? parseFloat(v) : (v as number);
  return Number.isFinite(n) ? n : undefined;
}

/** Map an Open Food Facts product into our `Food` model. OFF stores sodium etc. in grams. */
export function fromOff(p: OffProduct): Food | null {
  const n = p.nutriments ?? {};
  const kcal = num(n['energy-kcal_100g']) ?? (num(n['energy_100g']) ?? NaN) / 4.184;
  // Prefer Russian, then other regional names, then the generic name.
  const name = (NAME_LANGS.map((l) => p[`product_name_${l}`]).find((n) => n && n.trim()) || p.product_name || '').trim();
  if (!name || !Number.isFinite(kcal)) return null;

  const g = (k: string) => num(n[`${k}_100g`]);
  const mg = (k: string) => {
    const v = g(k);
    return v == null ? undefined : Math.round(v * 1000);
  };
  const per100g: Partial<Nutrients> = {
    kcal: Math.round(kcal),
    protein: g('proteins'),
    fat: g('fat'),
    carbs: g('carbohydrates'),
    sugar: g('sugars'),
    addedSugar: g('added-sugars'),
    // Prefer explicit sodium; otherwise derive from salt (salt = sodium × 2.5).
    sodium: mg('sodium') ?? (g('salt') != null ? Math.round((g('salt')! * 1000) / 2.5) : undefined),
    fiber: g('fiber'),
    satFat: g('saturated-fat'),
    transFat: g('trans-fat'),
    cholesterol: mg('cholesterol'),
    potassium: mg('potassium'),
  };
  const brand = Array.isArray(p.brands) ? p.brands.join(', ') : p.brands;
  return {
    id: `off-${p.code ?? name}`,
    name,
    brand: brand || undefined,
    source: 'off',
    barcode: p.code,
    servingG: num(p.serving_quantity),
    per100g,
  };
}

async function searchOff(query: string, signal?: AbortSignal): Promise<Food[]> {
  const url = `${SEARCH_URL}?q=${encodeURIComponent(query)}&page_size=40&fields=${FIELDS}`;
  const res = await fetch(url, { signal, headers: { 'User-Agent': USER_AGENT } });
  if (!res.ok) throw new Error(`Open Food Facts: HTTP ${res.status}`);
  const json = (await res.json()) as { hits?: OffProduct[] };
  return (json.hits ?? []).map(fromOff).filter((f): f is Food => f !== null);
}

// ---- USDA FoodData Central --------------------------------------------------------
// Public-domain US database (generic foods + many branded products). English names only, so
// it is queried only for Latin-script searches. DEMO_KEY is rate-limited per IP; users can
// enter their own free key in settings.

const USDA_URL = 'https://api.nal.usda.gov/fdc/v1/foods/search';

/** USDA nutrient ids → our fields (values are per 100 g in search results). */
const USDA_IDS: Record<number, keyof Nutrients> = {
  1008: 'kcal', 1003: 'protein', 1004: 'fat', 1005: 'carbs', 2000: 'sugar', 1235: 'addedSugar',
  1093: 'sodium', 1079: 'fiber', 1258: 'satFat', 1257: 'transFat', 1253: 'cholesterol', 1092: 'potassium',
};

interface UsdaFood {
  fdcId: number;
  description: string;
  brandOwner?: string;
  brandName?: string;
  gtinUpc?: string;
  servingSize?: number;
  servingSizeUnit?: string;
  foodNutrients?: { nutrientId?: number; value?: number }[];
}

/** "CHICKEN, BREAST, ROASTED" → "Chicken, breast, roasted". */
function tidyName(s: string): string {
  const lower = s === s.toUpperCase() ? s.toLowerCase() : s;
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

export function fromUsda(f: UsdaFood): Food | null {
  const per100g: Partial<Nutrients> = {};
  for (const n of f.foodNutrients ?? []) {
    const key = n.nutrientId != null ? USDA_IDS[n.nutrientId] : undefined;
    if (key && n.value != null) per100g[key] = Math.round(n.value * 10) / 10;
  }
  if (per100g.kcal == null || !f.description) return null;
  return {
    id: `usda-${f.fdcId}`,
    name: tidyName(f.description),
    brand: f.brandName || f.brandOwner || undefined,
    source: 'usda',
    barcode: f.gtinUpc || undefined,
    servingG: f.servingSizeUnit?.toLowerCase() === 'g' ? f.servingSize : undefined,
    per100g,
  };
}

async function searchUsda(query: string, apiKey: string, signal?: AbortSignal): Promise<Food[]> {
  const url = `${USDA_URL}?api_key=${encodeURIComponent(apiKey || 'DEMO_KEY')}&query=${encodeURIComponent(query)}&pageSize=25&dataType=Foundation,SR%20Legacy,Branded`;
  const res = await fetch(url, { signal });
  if (!res.ok) throw new Error(`USDA: HTTP ${res.status}`);
  const json = (await res.json()) as { foods?: UsdaFood[] };
  return (json.foods ?? []).map(fromUsda).filter((f): f is Food => f !== null);
}

const isLatin = (s: string) => /[a-z]/i.test(s) && !/[а-яё]/i.test(s);

/**
 * Search every online source in parallel and merge. Fails only if all sources fail.
 * Results are written to the on-device cache.
 */
export async function searchOnline(query: string, signal?: AbortSignal, usdaKey = ''): Promise<Food[]> {
  const jobs = [searchOff(query, signal)];
  if (isLatin(query)) jobs.push(searchUsda(query, usdaKey, signal));
  const settled = await Promise.allSettled(jobs);
  const ok = settled.filter((r): r is PromiseFulfilledResult<Food[]> => r.status === 'fulfilled');
  if (!ok.length) throw (settled[0] as PromiseRejectedResult).reason;
  // Interleave sources so one database does not bury the other.
  const lists = ok.map((r) => r.value);
  const merged: Food[] = [];
  for (let i = 0; lists.some((l) => i < l.length); i++) for (const l of lists) if (l[i]) merged.push(l[i]);
  storeQuery(query, merged);
  return merged;
}

export async function lookupBarcode(code: string): Promise<Food | null> {
  const cached = await cachedBarcode(code);
  if (cached) return cached;
  const res = await fetch(`${PRODUCT_URL}/${encodeURIComponent(code)}?fields=${FIELDS}`, {
    headers: { 'User-Agent': USER_AGENT },
  });
  if (!res.ok) return null;
  const json = (await res.json()) as { status?: number; product?: OffProduct };
  const food = json.status === 1 && json.product ? fromOff({ code, ...json.product }) : null;
  if (food) storeFood(food);
  return food;
}
