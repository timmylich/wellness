/** Health tips library. Category tags scroll to their section; the search filters tips. */
import { useRef, useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon } from '../../components/Icon';
import { Collapse } from '../../components/Collapse';
import { Card, Chevron, Chip, Field, Row, T, Tap } from '../../components/ui';
import { tipsFor } from '../../data/tips';
import { useLang, useT } from '../../i18n';
import { useTheme } from '../../lib/theme';

export default function Tips() {
  const c = useTheme();
  const L = useT();
  const lang = useLang();
  const insets = useSafeAreaInsets();
  const [open, setOpen] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const scrollRef = useRef<ScrollView>(null);
  // Y offset of each category card inside the scroll content.
  const offsets = useRef<Record<string, number>>({});
  const headerH = useRef(0);
  // Card offsets are measured inside the list container; this is that container's own y.
  const listY = useRef(0);
  const q = query.toLowerCase().trim();
  const all = tipsFor(lang);

  const cats = all
    .map((cat) => ({
      ...cat,
      tips: cat.tips.filter((tip) => !q || tip.title.toLowerCase().includes(q) || tip.body.some((p) => p.toLowerCase().includes(q))),
    }))
    .filter((cat) => cat.tips.length);

  const jump = (id: string) => {
    const y = offsets.current[id];
    // Leave room for the sticky tag row.
    if (y != null) scrollRef.current?.scrollTo({ y: Math.max(0, y - headerH.current - 4), animated: true });
  };

  return (
    <ScrollView
      ref={scrollRef}
      style={{ flex: 1, backgroundColor: c.bg }}
      contentContainerStyle={{ paddingBottom: 32 + insets.bottom }}
      // The tag row sticks to the top; it has no background so content scrolls under the pills.
      stickyHeaderIndices={[1]}
      keyboardShouldPersistTaps="handled"
    >
      <View style={{ paddingHorizontal: 16, paddingTop: 12 }}>
        <Field value={query} onChange={setQuery} numeric={false} placeholder={L.tips.search} />
      </View>
      <View onLayout={(e) => (headerH.current = e.nativeEvent.layout.height)}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingHorizontal: 16, paddingVertical: 10 }}>
          {all.map((cat) => (
            <Chip key={cat.id} icon={cat.icon} label={cat.title} onPress={() => jump(cat.id)} />
          ))}
        </ScrollView>
      </View>
      <View style={{ paddingHorizontal: 16, gap: 12 }} onLayout={(e) => (listY.current = e.nativeEvent.layout.y)}>
        {cats.map((cat) => (
          <View key={cat.id} onLayout={(e) => (offsets.current[cat.id] = e.nativeEvent.layout.y + listY.current)}>
            <Card>
              <Row style={{ flexWrap: 'nowrap' }}>
                <Icon name={cat.icon} size={22} color={c.accent} glow />
                <T size={18} bold>{cat.title}</T>
              </Row>
              {cat.tips.map((tip) => {
                const isOpen = open === tip.id || !!q;
                return (
                  <Tap key={tip.id} scaleTo={1} onPress={() => setOpen(isOpen ? null : tip.id)} style={{ gap: 6, paddingVertical: 6 }}>
                    <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
                      <T bold color={c.accent} style={{ flex: 1 }}>{tip.title}</T>
                      <Chevron open={isOpen} color={c.accent} />
                    </Row>
                    <Collapse open={isOpen}>
                      <View style={{ gap: 6 }}>
                        {tip.body.map((p, i) => <T key={i} size={14}>• {p}</T>)}
                        {tip.source ? <T dim size={12}>{L.tips.source}: {tip.source}</T> : null}
                      </View>
                    </Collapse>
                  </Tap>
                );
              })}
            </Card>
          </View>
        ))}
        <T dim size={12} center>{L.tips.footer}</T>
      </View>
    </ScrollView>
  );
}
