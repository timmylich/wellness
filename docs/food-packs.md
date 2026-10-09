# Food packs

A food pack is a JSON file with a list of foods that users import via
**Настройки → Данные → Импорт пакета продуктов**. Packs are the way to share large curated
datasets — for example a restaurant chain's menu — without bundling them into the app.

## Format

```json
{
  "name": "dodo-pizza-ru",
  "foods": [
    {
      "id": "pepperoni-25",
      "name": "Пепперони, 25 см",
      "brand": "Додо Пицца",
      "servingG": 410,
      "per100g": {
        "kcal": 0,
        "protein": 0,
        "fat": 0,
        "carbs": 0,
        "sugar": 0,
        "sodium": 0
      }
    }
  ]
}
```

| Field      | Required | Notes                                                                 |
|------------|----------|-----------------------------------------------------------------------|
| `name`     | yes      | Pack id. Re-importing a pack with the same name updates its foods.    |
| `foods[].id` | no     | Stable id inside the pack; defaults to the array index.               |
| `foods[].name` | yes  |                                                                       |
| `foods[].brand` | no  | Shown under the name and used in search.                              |
| `foods[].servingG` | no | Default amount in grams.                                            |
| `foods[].barcode` | no | EAN/UPC for barcode lookup.                                          |
| `foods[].per100g` | yes | Any of the keys below; missing keys count as 0.                     |

`per100g` keys and units: `kcal`, `protein` (g), `fat` (g), `carbs` (g), `sugar` (g),
`addedSugar` (g), `sodium` (mg), `fiber` (g), `satFat` (g), `transFat` (g), `cholesterol` (mg),
`potassium` (mg).

The values above are placeholders — the format example is not real data.

## Data sources and licensing

Only publish packs built from data you may share: official nutrition tables published by the
restaurant, product labels you photographed, or open databases. Always state the source and the
date of collection in the pack's repository or PR.
