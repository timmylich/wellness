/**
 * Base products for the Pantry section with typical storage times, in days, for an unopened
 * product stored as usual (fridge for perishables). Source: USDA FSIS FoodKeeper
 * (https://www.foodsafety.gov/keep-food-safe/foodkeeper-app), rounded; the date on the
 * package always wins. `match` holds lowercase name fragments (ru + en) used to guess the base.
 */
export interface ShelfBase {
  id: string;
  ru: string;
  en: string;
  days: number;
  match: string[];
}

export const SHELF_BASES: ShelfBase[] = [
  { id: 'milk', ru: 'Молоко', en: 'Milk', days: 7, match: ['молок', 'milk'] },
  { id: 'kefir', ru: 'Кефир, ряженка', en: 'Kefir', days: 7, match: ['кефир', 'ряженк', 'простокваш', 'kefir'] },
  { id: 'yogurt', ru: 'Йогурт', en: 'Yogurt', days: 14, match: ['йогурт', 'yogurt', 'yoghurt'] },
  { id: 'cottage', ru: 'Творог', en: 'Cottage cheese', days: 5, match: ['творог', 'творож', 'cottage'] },
  { id: 'sourCream', ru: 'Сметана, сливки', en: 'Sour cream, cream', days: 10, match: ['сметан', 'сливк', 'cream'] },
  { id: 'cheeseSoft', ru: 'Сыр мягкий', en: 'Soft cheese', days: 7, match: ['моцарел', 'брынз', 'фета', 'рикотт', 'mozzarella', 'feta', 'ricotta'] },
  { id: 'cheeseHard', ru: 'Сыр твёрдый', en: 'Hard cheese', days: 30, match: ['сыр', 'cheese'] },
  { id: 'butter', ru: 'Сливочное масло', en: 'Butter', days: 30, match: ['масло слив', 'butter'] },
  { id: 'eggs', ru: 'Яйца', en: 'Eggs', days: 28, match: ['яйц', 'egg'] },
  { id: 'mince', ru: 'Фарш', en: 'Minced meat', days: 2, match: ['фарш', 'mince', 'ground'] },
  { id: 'poultry', ru: 'Курица, птица (сырая)', en: 'Raw poultry', days: 2, match: ['кур', 'индей', 'chicken', 'turkey'] },
  { id: 'smoked', ru: 'Колбаса копчёная', en: 'Dry sausage', days: 30, match: ['копч', 'салями', 'сервелат', 'salami'] },
  { id: 'sausage', ru: 'Колбаса варёная, сосиски', en: 'Cooked sausage', days: 7, match: ['колбас', 'сосиск', 'сардельк', 'ветчин', 'sausage', 'ham'] },
  { id: 'meat', ru: 'Мясо (сырое)', en: 'Raw meat', days: 4, match: ['говяд', 'свин', 'баран', 'мясо', 'стейк', 'beef', 'pork', 'steak'] },
  { id: 'fish', ru: 'Рыба (свежая)', en: 'Fresh fish', days: 2, match: ['рыб', 'лосос', 'сёмг', 'семг', 'треск', 'fish', 'salmon', 'cod'] },
  { id: 'cooked', ru: 'Готовая еда', en: 'Cooked leftovers', days: 3, match: ['суп', 'готов', 'soup', 'leftover'] },
  { id: 'bread', ru: 'Хлеб', en: 'Bread', days: 4, match: ['хлеб', 'батон', 'багет', 'лаваш', 'bread', 'baguette'] },
  { id: 'greens', ru: 'Зелень, салатные листья', en: 'Greens', days: 5, match: ['зелен', 'укроп', 'петруш', 'шпинат', 'spinach', 'lettuce', 'herbs'] },
  { id: 'berries', ru: 'Ягоды', en: 'Berries', days: 4, match: ['ягод', 'клубн', 'малин', 'черник', 'berr'] },
  { id: 'tomato', ru: 'Помидоры, огурцы', en: 'Tomatoes, cucumbers', days: 7, match: ['помид', 'томат', 'огур', 'tomato', 'cucumber'] },
  { id: 'banana', ru: 'Бананы', en: 'Bananas', days: 5, match: ['банан', 'banana'] },
  { id: 'fruit', ru: 'Яблоки, цитрусы', en: 'Apples, citrus', days: 30, match: ['яблок', 'апельс', 'мандар', 'лимон', 'груш', 'apple', 'orange', 'lemon', 'pear'] },
  { id: 'roots', ru: 'Картофель, морковь, лук', en: 'Potatoes, carrots, onions', days: 30, match: ['картоф', 'морков', 'свекл', 'лук', 'potato', 'carrot', 'onion', 'beet'] },
  { id: 'juice', ru: 'Сок', en: 'Juice', days: 180, match: ['сок', 'juice'] },
  { id: 'chocolate', ru: 'Шоколад, сладости', en: 'Chocolate, sweets', days: 180, match: ['шокол', 'конфет', 'печень', 'chocolate', 'candy', 'cookie'] },
  { id: 'grains', ru: 'Крупы, макароны', en: 'Grains, pasta', days: 365, match: ['гречк', 'рис', 'овсян', 'макарон', 'спагет', 'круп', 'rice', 'pasta', 'oat'] },
  { id: 'flour', ru: 'Мука', en: 'Flour', days: 180, match: ['мука', 'flour'] },
  { id: 'canned', ru: 'Консервы', en: 'Canned food', days: 730, match: ['консерв', 'тушён', 'canned', 'tuna'] },
  { id: 'frozen', ru: 'Заморозка', en: 'Frozen food', days: 120, match: ['заморож', 'пельмен', 'вареник', 'frozen', 'dumpling'] },
  { id: 'other', ru: 'Другое', en: 'Other', days: 7, match: [] },
];

export function shelfBase(id: string | undefined): ShelfBase | undefined {
  return SHELF_BASES.find((b) => b.id === id);
}

/** Best-guess base for a product name (first base with a matching fragment). */
export function guessBase(name: string): ShelfBase | undefined {
  const n = name.toLowerCase();
  if (!n.trim()) return undefined;
  return SHELF_BASES.find((b) => b.match.some((m) => n.includes(m)));
}
