/** English health tips. Structure and ids must match tips.ru.ts. */
import type { TipCategory } from './tips';

export const TIPS_EN: TipCategory[] = [
  {
    id: 'air',
    title: 'Indoor air',
    icon: 'air',
    tips: [
      {
        id: 'ventilate',
        title: 'How to ventilate properly',
        body: [
          'Burst ventilation works best: open windows wide for 5–10 minutes (winter) or 15–20 minutes (summer) 3–4 times a day. A cross-draught through opposite windows replaces the air in 2–5 minutes.',
          'A window left slightly tilted all winter cools walls and window frames but exchanges air slowly — condensation and mould follow.',
          'Always air the bedroom before sleep and after waking, the kitchen during and after cooking, and the bathroom after a shower.',
          'If you live by a busy road, ventilate when traffic is lowest (late evening, early morning) or use filtered mechanical ventilation.',
        ],
      },
      {
        id: 'fresh-air',
        title: 'What counts as fresh air',
        body: [
          'The main marker of stuffiness is carbon dioxide (CO₂). Outdoors it is ~420 ppm. Up to 800 ppm is excellent, 800–1000 fine, 1000–1400 brings drowsiness and poorer focus, above 1400 — ventilate now.',
          'Humidity: 40–60 % is optimal. Lower dries mucous membranes and increases colds; higher raises the risk of mould and dust mites.',
          'Temperature: 17–20 °C for sleep, 20–23 °C for work.',
          'Fine particles (PM2.5) are the most harmful air pollutant. WHO 2021 guideline: annual mean no more than 5 µg/m³, daily 15 µg/m³.',
          'An inexpensive CO₂ monitor (with a real NDIR sensor, not “eCO₂”) pays off — it shows when to open a window.',
        ],
        source: 'WHO Global Air Quality Guidelines (2021)',
      },
      {
        id: 'indoor-pollution',
        title: 'Pollution sources at home',
        body: [
          'Gas stoves emit NO₂ and CO — always use the extractor hood and ventilate while cooking.',
          'High-heat frying produces many fine particles; a hood vented outdoors is much better than a recirculating one.',
          'Candles, incense and smoking indoors are strong PM2.5 sources.',
          'New particleboard furniture, laminate flooring and paints release formaldehyde and VOCs — ventilate more often for the first weeks.',
          'Mould in corners and around windows is not cosmetic: its spores trigger allergies and asthma. Fix the cause (humidity, cold bridges).',
        ],
      },
    ],
  },
  {
    id: 'water',
    title: 'Drinking water',
    icon: 'water',
    tips: [
      {
        id: 'what-water',
        title: 'What water to drink',
        body: [
          'Clean drinking water with low mineralisation (under 500 mg/L) is right for everyday use. Medicinal mineral waters are not meant to be drunk by the litre every day.',
          'Boiling kills microbes but does not remove organochlorines, heavy metals or hardness salts.',
          'Bottled water is not necessarily better than well-filtered tap water. Do not leave plastic bottles in the sun or a hot car.',
          'Tea, coffee, soup and milk provide water too. Moderate caffeine does not dehydrate.',
        ],
      },
      {
        id: 'filters',
        title: 'Choosing a filter',
        body: [
          'A jug with a carbon cartridge removes chlorine, odour and some organics. Cheap, but replace the cartridge on time (usually 200–300 L) — an old cartridge becomes a breeding ground for bacteria.',
          'An under-sink 3-stage filter is the sweet spot for most homes: cleaner water and less maintenance.',
          'Reverse osmosis removes almost everything, including useful minerals. Worth it for very poor or hard water; prefer a model with a remineraliser.',
          'A lab water test before buying helps you fight the real problem (iron, hardness, nitrates) rather than an imagined one.',
        ],
      },
      {
        id: 'hydration',
        title: 'How much to drink and when it is dangerous',
        body: [
          'A guide is 30–35 ml per kg of body weight a day, plus about 500 ml per hour of exercise or heat.',
          'Urine colour is a simple indicator: pale straw is normal, dark means drink more, completely clear all day may mean you drink more than needed.',
          'Kidneys excrete roughly 0.8–1 L per hour. Drinking far more in a short time drops blood sodium (hyponatremia): nausea, headache, confusion. It can be life-threatening.',
          'During long, sweaty workouts drink water with electrolytes, not plain water only.',
        ],
      },
    ],
  },
  {
    id: 'cancer',
    title: 'Cancer prevention',
    icon: 'cancer',
    tips: [
      {
        id: 'carcinogens',
        title: 'The main proven risk factors',
        body: [
          'Smoking (including hookah and, likely, vaping) causes ~20 % of cancer deaths. Quitting at any age lowers the risk.',
          'Alcohol is an IARC Group 1 carcinogen. There is no safe dose for cancer: risk rises with every drink (breast, liver, oesophagus, bowel).',
          'Ultraviolet radiation (sun and sunbeds) is the main cause of melanoma and other skin cancers.',
          'Overweight and obesity are linked to at least 13 types of cancer.',
          'Processed meat (sausages, frankfurters, bacon) is Group 1, red meat Group 2A. A sensible limit is 350–500 g of red meat a week and as little processed meat as possible.',
          'Viruses: HPV (cervical and other cancers), hepatitis B and C (liver cancer). Vaccines exist for HPV and hepatitis B.',
          'Radon in basements and ground floors, asbestos in old buildings, polluted air.',
        ],
        source: 'IARC, WHO, European Code Against Cancer',
      },
      {
        id: 'iarc-groups',
        title: 'What carcinogen groups mean',
        body: [
          'The International Agency for Research on Cancer (IARC) ranks agents by the strength of the EVIDENCE, not by how dangerous they are. Tobacco and sausages in the same group means both definitely cause cancer — not that they are equally potent.',
          'Group 1 — “carcinogenic to humans”: proven. Tobacco, alcohol, benzene, asbestos, UV and sunbeds, diesel exhaust, processed meat, outdoor air pollution, radon.',
          'Group 2A — “probably carcinogenic”: strong animal data, limited human data. Red meat, very hot drinks (above 65 °C), night shift work, acrylamide.',
          'Group 2B — “possibly carcinogenic”: limited evidence. For example aloe vera whole-leaf extract and some fuels. Many agents with very weak evidence end up here.',
          'Group 3 — “not classifiable”: not enough data to conclude. This does not mean “safe”.',
          'Dose and frequency matter: the risk from daily smoking is hundreds of times higher than from an occasional sausage.',
        ],
        source: 'IARC Monographs, Preamble (2019)',
      },
      {
        id: 'benzene',
        title: 'Benzene and exhaust fumes',
        body: [
          'Benzene is a volatile, colourless hydrocarbon with a sweetish smell. It is an IARC Group 1 carcinogen: it causes leukaemia (acute myeloid leukaemia) and damages bone marrow.',
          'Everyday sources: car exhaust, petrol vapour at filling stations, tobacco smoke (the main source for smokers), garages attached to the home, smoke from burning plastic and rubbish.',
          'Do not breathe exhaust: don’t stand near a running tailpipe, never warm up a car in a closed garage, keep windows closed in traffic jams and tunnels and switch the cabin air to recirculation.',
          'At the filling station avoid breathing the vapour, don’t linger at the pump and never fill jerrycans inside the boot or cabin.',
          'Exhaust also contains carbon monoxide (CO) — colourless, odourless and deadly in enclosed spaces. Never leave an engine running in a garage.',
          'Diesel exhaust itself is Group 1 (lung cancer). Run and walk in parks rather than along busy roads.',
          'At home benzene can come from solvents, paints and glues — use them with windows open and store them sealed outside living areas.',
        ],
        source: 'IARC Monographs vol. 120 (Benzene), WHO',
      },
      {
        id: 'screening',
        title: 'Screenings that save lives',
        body: [
          'Cervical cancer: Pap smear and HPV test from age 21–25 on your doctor’s schedule.',
          'Breast cancer: mammography usually from 40–50, every 1–2 years.',
          'Bowel cancer: faecal occult blood test or colonoscopy from 45–50.',
          'Skin: check your moles monthly with the ABCDE rule (asymmetry, border, colour, diameter > 6 mm, evolving).',
          'The exact schedule depends on your country and family history — ask your doctor.',
        ],
      },
    ],
  },
  {
    id: 'sun',
    title: 'Sun',
    icon: 'sun',
    tips: [
      {
        id: 'sun-safe',
        title: 'How much sun',
        body: [
          'In summer at mid latitudes, 10–20 minutes of midday sun on arms and face 2–3 times a week is enough for vitamin D (less for fair skin).',
          'From October to March the sun is too low in most northern regions for meaningful vitamin D production. Discuss a supplement with your doctor.',
          'When the UV index is 3 or higher, use SPF 30+, a hat and sunglasses. Apply sunscreen 15 minutes before going out and reapply every 2 hours and after swimming.',
          'Sunburn — especially in childhood — significantly increases melanoma risk. A tan is the skin’s response to damage.',
          'Sunbeds are an IARC Group 1 carcinogen.',
        ],
      },
    ],
  },
  {
    id: 'back',
    title: 'Back health',
    icon: 'back',
    tips: [
      {
        id: 'sitting',
        title: 'If you sit a lot',
        body: [
          'The best posture is the next one: change position and stand up every 30–45 minutes, even for 1–2 minutes.',
          'Monitor top at eye level, an arm’s length away. A laptop needs a stand and an external keyboard.',
          'Feet flat on the floor, knees roughly level with hips, lower back supported.',
          'Hold your phone higher instead of bending your head: a 60° tilt loads the neck like ~27 kg.',
        ],
      },
      {
        id: 'back-exercises',
        title: 'Exercises for your back',
        body: [
          'A strong core is the best prevention: plank, bird-dog, glute bridge, cat-cow. 10 minutes a day noticeably reduces the risk of pain.',
          'Walking 30+ minutes a day is one of the best remedies for chronic low back pain.',
          'With acute non-specific back pain, bed rest does harm: stay moderately active.',
          'See a doctor urgently if pain comes with numbness in the groin, bladder problems, leg weakness, fever, or follows an injury.',
        ],
        source: 'NICE NG59, low back pain guideline',
      },
      {
        id: 'lifting',
        title: 'How to lift heavy things',
        body: [
          'Keep the load close to your body, bend your knees and hips, keep your back neutral.',
          'Don’t twist while holding a load — turn with your feet first.',
          'Heavy and awkward — lift with two people.',
        ],
      },
    ],
  },
  {
    id: 'sleep',
    title: 'Sleep',
    icon: 'sleep',
    tips: [
      {
        id: 'sleep-basics',
        title: 'Healthy sleep basics',
        body: [
          'Adults need 7–9 hours. Chronic sleep loss increases appetite and the risk of obesity, diabetes and heart disease.',
          'Go to bed and get up at the same time, even at weekends.',
          'Morning daylight sets your body clock correctly; bright screens late at night do the opposite.',
          'No caffeine within 8 hours of bedtime. Alcohol helps you fall asleep but worsens sleep quality.',
          'A cool (17–20 °C), dark and quiet bedroom.',
        ],
      },
    ],
  },
  {
    id: 'heart',
    title: 'Heart & blood pressure',
    icon: 'heart',
    tips: [
      {
        id: 'measure-bp',
        title: 'How to measure blood pressure',
        body: [
          'Sit quietly for 5 minutes. No coffee, smoking or exercise for 30 minutes before.',
          'Seated, back supported, legs uncrossed, arm on a table, cuff at heart level on bare skin.',
          'Take 2 readings 1–2 minutes apart and record the average.',
          'Measure in the morning (before medication and food) and in the evening. A single high reading is not a diagnosis.',
          'Cuff size matters: a small cuff on a large arm overestimates pressure.',
        ],
      },
      {
        id: 'lower-bp',
        title: 'Lowering blood pressure without pills',
        body: [
          'Less salt: cutting to 5 g a day lowers pressure by about 4–5 mmHg on average.',
          'More potassium: vegetables, fruit, legumes.',
          'Weight loss: roughly −1 mmHg per kilogram lost.',
          '150 minutes of aerobic activity a week.',
          'Less alcohol, no smoking.',
          'If your doctor prescribed medication, don’t stop it on your own.',
        ],
      },
    ],
  },
  {
    id: 'nutrition',
    title: 'Nutrition',
    icon: 'nutrition',
    tips: [
      {
        id: 'plate',
        title: 'The plate rule',
        body: [
          'Half the plate vegetables and fruit, a quarter protein (meat, fish, eggs, legumes, cottage cheese), a quarter whole grains.',
          'Drink water, not sugary drinks: juice and soda are the most “invisible” calories.',
          'Add oil, nuts and seeds — but sensibly: they are the most calorie-dense foods.',
        ],
      },
      {
        id: 'labels',
        title: 'How to read labels',
        body: [
          'Ingredients are listed in descending order: if sugar is in the top three, the product is sweet.',
          'Sugar hides under names like glucose-fructose syrup, molasses, maltodextrin, dextrose, juice concentrate.',
          'Trans fats: “partially hydrogenated oils” — a reason to choose another product.',
          '1.5 g of salt per 100 g is a lot; 0.3 g or less is little.',
        ],
      },
      {
        id: 'weight-loss',
        title: 'Losing weight without relapses',
        body: [
          'A safe pace is 0.5–1 % of body weight per week. Faster means losing muscle, a slower metabolism and a higher risk of relapse.',
          'High protein and strength training help you lose fat rather than muscle.',
          'Weight fluctuates 1–2 kg day to day because of water and salt. Watch the 2–3 week trend.',
          'Plan some slack for holidays and weekends — adaptive targets in settings account for over- and under-eating.',
        ],
      },
    ],
  },
];
