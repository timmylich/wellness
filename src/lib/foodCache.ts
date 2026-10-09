/**
 * On-device cache for online food lookups (Open Food Facts, USDA).
 * - Query results are cached for 14 days, so repeated searches are instant and work offline.
 * - Every food ever returned is kept (up to MAX_FOODS, least recently seen dropped first)
 *   and is included in local search, so the offline database grows with use.
 * Stored separately from AppData: it is a cache, not user data, and is not part of backups.
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

import type { Food } from '../store/types';

const KEY = 'wellness/foodCache';
const MAX_FOODS = 3000;
const MAX_QUERIES = 300;
const QUERY_TTL_MS = 14 * 24 * 3600 * 1000;

interface CacheData {
  foods: Record<string, Food & { seenAt: number }>;
  queries: Record<string, { ids: string[]; at: number }>;
  barcodes: Record<string, string>;
}

let cache: CacheData | null = null;
let loading: Promise<CacheData> | null = null;
let saveTimer: ReturnType<typeof setTimeout> | null = null;

const normQuery = (q: string) => q.toLowerCase().replace(/ё/g, 'е').trim().replace(/\s+/g, ' ');

async function load(): Promise<CacheData> {
  if (cache) return cache;
  loading ??= AsyncStorage.getItem(KEY)
    .then((raw) => (raw ? (JSON.parse(raw) as CacheData) : null))
    .catch(() => null)
    .then((c) => (cache = c ?? { foods: {}, queries: {}, barcodes: {} }));
  return loading;
}

function save() {
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    if (cache) AsyncStorage.setItem(KEY, JSON.stringify(cache)).catch(() => {});
  }, 1000);
}

function prune(c: CacheData) {
  const foods = Object.values(c.foods);
  if (foods.length > MAX_FOODS) {
    foods.sort((a, b) => a.seenAt - b.seenAt);
    for (const f of foods.slice(0, foods.length - MAX_FOODS)) delete c.foods[f.id];
  }
  const qs = Object.entries(c.queries);
  if (qs.length > MAX_QUERIES) {
    qs.sort((a, b) => a[1].at - b[1].at);
    for (const [q] of qs.slice(0, qs.length - MAX_QUERIES)) delete c.queries[q];
  }
}

function strip(f: Food & { seenAt?: number }): Food {
  const { seenAt, ...food } = f;
  void seenAt;
  return food;
}

/** Cached results for a query, or null if missing/expired. */
export async function cachedQuery(query: string): Promise<Food[] | null> {
  const c = await load();
  const hit = c.queries[normQuery(query)];
  if (!hit || Date.now() - hit.at > QUERY_TTL_MS) return null;
  return hit.ids.map((id) => c.foods[id]).filter(Boolean).map(strip);
}

export async function storeQuery(query: string, foods: Food[]): Promise<void> {
  const c = await load();
  const now = Date.now();
  for (const f of foods) {
    c.foods[f.id] = { ...f, seenAt: now };
    if (f.barcode) c.barcodes[f.barcode] = f.id;
  }
  c.queries[normQuery(query)] = { ids: foods.map((f) => f.id), at: now };
  prune(c);
  save();
}

export async function cachedBarcode(code: string): Promise<Food | null> {
  const c = await load();
  const id = c.barcodes[code];
  return id && c.foods[id] ? strip(c.foods[id]) : null;
}

export async function storeFood(f: Food): Promise<void> {
  const c = await load();
  c.foods[f.id] = { ...f, seenAt: Date.now() };
  if (f.barcode) c.barcodes[f.barcode] = f.id;
  prune(c);
  save();
}

/** All cached foods, for offline local search. */
export async function cachedFoods(): Promise<Food[]> {
  const c = await load();
  return Object.values(c.foods).map(strip);
}

export async function clearFoodCache(): Promise<void> {
  cache = { foods: {}, queries: {}, barcodes: {} };
  await AsyncStorage.removeItem(KEY);
}

export async function foodCacheSize(): Promise<number> {
  return Object.keys((await load()).foods).length;
}
