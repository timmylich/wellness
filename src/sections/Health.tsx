/**
 * Health records: doctor visits, lab tests and other documents with photos. Photos are copied
 * into the app's private folder (lib/photos.ts) and included in backups. Nothing leaves the
 * phone.
 */
import * as ImagePicker from 'expo-image-picker';
import { useMemo, useState } from 'react';
import { Image, Modal, Pressable, View } from 'react-native';

import { confirmDialog } from '../components/Dialog';
import { Icon } from '../components/Icon';
import { Sheet } from '../components/Sheet';
import { Button, Card, Empty, Field, H, Row, Segmented, T, Tap } from '../components/ui';
import { useT } from '../i18n';
import { humanDate, todayKey } from '../lib/date';
import { deletePhotos, keepPhotos } from '../lib/photos';
import { useTheme } from '../lib/theme';
import { removeHealthRecord, saveHealthRecord } from '../store/actions';
import { uid, useStore } from '../store/store';
import type { HealthRecord, HealthRecordKind } from '../store/types';
import { DateStep } from '../components/DateStep';

const KIND_ICON = { visit: 'user', test: 'pulse', other: 'info' } as const;
const now = () => Date.now();

export function Health() {
  const c = useTheme();
  const L = useT();
  const records = useStore((s) => s.health);
  const [editing, setEditing] = useState<HealthRecord | 'new' | null>(null);
  const [query, setQuery] = useState('');
  const [viewer, setViewer] = useState<string | null>(null);
  const q = query.trim().toLowerCase();
  const list = useMemo(
    () =>
      [...records]
        .filter((r) => !q || `${r.title} ${r.place ?? ''} ${r.notes ?? ''}`.toLowerCase().includes(q))
        .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt - a.createdAt),
    [records, q],
  );

  return (
    <>
      <Button title={L.health.add} icon="plus" big onPress={() => setEditing('new')} />
      {records.length > 3 ? <Field value={query} onChange={setQuery} numeric={false} placeholder={L.health.search} search /> : null}
      {list.length ? (
        list.map((r) => (
          <Card key={r.id}>
            <Tap scaleTo={0.99} onPress={() => setEditing(r)} style={{ gap: 6 }}>
              <Row style={{ flexWrap: 'nowrap' }}>
                <View style={{ width: 36, height: 36, borderRadius: 11, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
                  <Icon name={KIND_ICON[r.kind]} size={19} color={c.accent} />
                </View>
                <View style={{ flex: 1 }}>
                  <T bold>{r.title}</T>
                  <T dim size={12}>
                    {L.health.kinds[r.kind]} · {humanDate(r.date, L)}
                    {r.place ? ` · ${r.place}` : ''}
                  </T>
                </View>
              </Row>
              {r.notes ? <T size={14} numberOfLines={4}>{r.notes}</T> : null}
            </Tap>
            {r.photos.length ? (
              <Row gap={6}>
                {r.photos.map((p) => (
                  <Tap key={p} scaleTo={0.96} onPress={() => setViewer(p)}>
                    <Image source={{ uri: p }} style={{ width: 72, height: 72, borderRadius: 10, backgroundColor: c.cardAlt }} />
                  </Tap>
                ))}
              </Row>
            ) : null}
          </Card>
        ))
      ) : (
        <Card>
          <H icon="health">{L.health.title}</H>
          <Empty text={L.health.empty} />
        </Card>
      )}
      <T dim size={11}>{L.health.privacy}</T>

      <Sheet visible={editing != null} onClose={() => setEditing(null)}>
        {editing != null && <HealthForm record={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />}
      </Sheet>

      <Modal visible={viewer != null} transparent animationType="fade" onRequestClose={() => setViewer(null)} statusBarTranslucent>
        <Pressable style={{ flex: 1, backgroundColor: '#000e', justifyContent: 'center' }} onPress={() => setViewer(null)}>
          {viewer ? <Image source={{ uri: viewer }} style={{ width: '100%', height: '85%' }} resizeMode="contain" /> : null}
        </Pressable>
      </Modal>
    </>
  );
}

function HealthForm({ record, onDone }: { record: HealthRecord | null; onDone: () => void }) {
  const c = useTheme();
  const L = useT();
  const [kind, setKind] = useState<HealthRecordKind>(record?.kind ?? 'visit');
  const [title, setTitle] = useState(record?.title ?? '');
  const [place, setPlace] = useState(record?.place ?? '');
  const [notes, setNotes] = useState(record?.notes ?? '');
  const [date, setDate] = useState(record?.date ?? todayKey());
  const [photos, setPhotos] = useState<string[]>(record?.photos ?? []);
  // Photos added in this session; deleted again if the form is discarded.
  const [added, setAdded] = useState<string[]>([]);

  const pick = async (camera: boolean) => {
    const opts: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], quality: 0.8, allowsMultipleSelection: !camera };
    if (camera) {
      const perm = await ImagePicker.requestCameraPermissionsAsync();
      if (!perm.granted) return;
    }
    const res = camera ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts);
    if (res.canceled) return;
    const kept = await keepPhotos(res.assets.map((a) => a.uri));
    setPhotos((p) => [...p, ...kept]);
    setAdded((p) => [...p, ...kept]);
  };

  const save = () => {
    if (!title.trim()) return;
    const removed = (record?.photos ?? []).filter((p) => !photos.includes(p));
    deletePhotos(removed);
    saveHealthRecord({
      id: record?.id ?? uid(),
      date,
      kind,
      title: title.trim(),
      place: place.trim() || undefined,
      notes: notes.trim() || undefined,
      photos,
      createdAt: record?.createdAt ?? now(),
    });
    onDone();
  };

  const cancel = () => {
    deletePhotos(added);
    onDone();
  };

  return (
    <>
      <T size={18} bold>{record ? L.health.edit : L.health.add}</T>
      <Segmented
        value={kind}
        onChange={setKind}
        options={(['visit', 'test', 'other'] as const).map((k) => ({ value: k, label: L.health.kinds[k] }))}
      />
      <Field label={L.health.titleLabel} value={title} onChange={setTitle} numeric={false} placeholder={L.health.titlePh} />
      <Field label={L.health.place} value={place} onChange={setPlace} numeric={false} />
      <DateStep label={L.health.date} value={date} onChange={setDate} />
      <Field label={L.health.notes} value={notes} onChange={setNotes} numeric={false} multiline />
      <T dim size={13}>{L.health.photos}</T>
      {photos.length ? (
        <Row gap={6}>
          {photos.map((p) => (
            <View key={p}>
              <Image source={{ uri: p }} style={{ width: 72, height: 72, borderRadius: 10, backgroundColor: c.cardAlt }} />
              <Tap
                onPress={() => setPhotos((list) => list.filter((x) => x !== p))}
                hitSlop={8}
                style={{ position: 'absolute', top: -6, right: -6, width: 24, height: 24, borderRadius: 12, backgroundColor: c.danger, alignItems: 'center', justifyContent: 'center' }}
              >
                <Icon name="close" size={14} color="#fff" />
              </Tap>
            </View>
          ))}
        </Row>
      ) : null}
      <Row style={{ flexWrap: 'nowrap' }}>
        <Button title={L.health.addPhoto} icon="image" kind="secondary" onPress={() => pick(false)} style={{ flex: 1 }} />
        <Button title={L.health.takePhoto} icon="camera" kind="secondary" onPress={() => pick(true)} style={{ flex: 1 }} />
      </Row>
      <Row style={{ flexWrap: 'nowrap' }}>
        {record ? (
          <Button
            title={L.health.remove}
            kind="danger"
            style={{ flex: 1 }}
            onPress={() =>
              confirmDialog({
                title: L.health.removeTitle,
                message: L.health.removeText,
                cancel: L.common.cancel,
                ok: L.common.delete,
                onOk: () => {
                  deletePhotos(record.photos);
                  removeHealthRecord(record.id);
                  onDone();
                },
              })
            }
          />
        ) : (
          <Button title={L.common.cancel} kind="ghost" onPress={cancel} style={{ flex: 1 }} />
        )}
        <Button title={L.health.save} onPress={save} disabled={!title.trim()} style={{ flex: 2 }} />
      </Row>
    </>
  );
}
