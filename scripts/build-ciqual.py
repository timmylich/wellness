"""
Regenerate src/data/ciqual.json from the official ANSES-CIQUAL table (English edition).

    curl -o ciqual.xls "https://ciqual.anses.fr/cms/sites/default/files/inline-files/Table%20Ciqual%202020_ENG_2020%2007%2007.xls"
    python scripts/build-ciqual.py ciqual.xls

Values like "< 0.5" become half the limit, "traces" becomes 0, "-" is omitted.
Licence of the data: Etalab Open Licence 2.0 — keep the attribution in the app and README.
"""
import json
import math
import sys

import pandas as pd

COLS = {'kcal': 10, 'protein': 14, 'fat': 17, 'carbs': 16, 'sugar': 18, 'fiber': 26, 'satFat': 31,
        'cholesterol': 48, 'sodium': 60, 'potassium': 58}


def num(v):
    if v is None or (isinstance(v, float) and math.isnan(v)):
        return None
    s = str(v).strip().replace(',', '.')
    if s in ('-', ''):
        return None
    if s.lower() == 'traces':
        return 0
    if s.startswith('<'):
        try:
            return round(float(s[1:].strip()) / 2, 2)
        except ValueError:
            return None
    try:
        return round(float(s), 2)
    except ValueError:
        return None


def main(path):
    df = pd.read_excel(path)
    rows = []
    for _, r in df.iterrows():
        v = r.values
        kcal = num(v[10]) or num(v[12])
        name = str(v[7]).strip()
        if kcal is None or not name or name == 'nan':
            continue
        rows.append([int(v[6]), name[0].upper() + name[1:]] +
                    [round(kcal) if k == 'kcal' else num(v[i]) for k, i in COLS.items()])
    out = {'license': 'ANSES-CIQUAL 2020, Etalab Open Licence 2.0', 'fields': ['code', 'name'] + list(COLS), 'rows': rows}
    with open('src/data/ciqual.json', 'w', encoding='utf8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    print(f'{len(rows)} foods written')


if __name__ == '__main__':
    main(sys.argv[1])
