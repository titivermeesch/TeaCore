import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, History as HistoryIcon, Plus, RotateCcw, Save, Trash2, Undo2 } from 'lucide-react';
import { api, apiPost } from '../api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { cn } from '../lib/cn';

type FieldType = 'boolean' | 'int' | 'number' | 'text' | 'enum' | 'material' | 'text-list' | 'map' | 'list';

interface Field {
    path: string;
    type: FieldType;
    title?: string;
    help?: string;
    min?: number;
    max?: number;
    step?: number;
    options?: string[];
    apply?: string;
    secret?: boolean;
    fields?: Field[];
    'new-entry'?: Record<string, unknown>;
    'from-inventory'?: boolean;
}

interface Category {
    id: string;
    title?: string;
    fields: Field[];
}

interface SettingsFile {
    id: string;
    title: string;
    hosts: string[];
    divergent: string[];
    schema: { apply?: string; categories: Category[] };
    values: Record<string, unknown>;
}

type Obj = Record<string, unknown>;

const HIDDEN = '********';

export default function Settings() {
    const [files, setFiles] = useState<SettingsFile[] | null>(null);
    const [selected, setSelected] = useState<string | null>(null);
    const [category, setCategory] = useState<string | null>(null);
    const [draft, setDraft] = useState<Record<string, unknown>>({});
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [group, setGroup] = useState('');
    const [groups, setGroups] = useState<string[]>([]);
    const [view, setView] = useState<'settings' | 'history'>('settings');

    const refresh = useCallback(async () => {
        try {
            setFiles(await api<SettingsFile[]>(`/api/settings${group ? `?group=${encodeURIComponent(group)}` : ''}`));
        } catch (err) {
            setError((err as Error).message);
        }
    }, [group]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    useEffect(() => {
        api<string[]>('/api/settings-groups').then(setGroups).catch(() => {});
    }, []);

    function pickGroup(g: string) {
        if (Object.keys(draft).length && !window.confirm('Discard your unsaved changes?')) return;
        setDraft({});
        setGroup(g);
        setNotice(null);
    }

    const file = useMemo(
        () => files?.find((f) => f.id === selected) ?? files?.[0] ?? null,
        [files, selected],
    );
    const cat = file?.schema.categories.find((c) => c.id === category) ?? file?.schema.categories[0] ?? null;
    const dirty = Object.keys(draft).length;

    function pick(id: string) {
        if (dirty && !window.confirm('Discard your unsaved changes?')) return;
        setDraft({});
        setSelected(id);
        setCategory(null);
        setNotice(null);
        setError(null);
    }

    async function save() {
        if (!file || !dirty) return;
        setSaving(true);
        setError(null);
        try {
            const res = await apiPost<{ targets: string[] }>(`/api/settings/${file.id}`, {
                changes: Object.entries(draft).map(([path, value]) => ({ path, value })),
                group: group || undefined,
            });
            setDraft({});
            const n = res.targets.length;
            setNotice(`Saved on ${n} server${n === 1 ? '' : 's'}.`);
            // Other servers report their new values a moment later.
            window.setTimeout(refresh, 1200);
            refresh();
        } catch (err) {
            setError((err as Error).message);
        } finally {
            setSaving(false);
        }
    }

    if (files === null) {
        return error ? <div className="text-error text-sm">{error}</div> : null;
    }
    if (files.length === 0 || !file || !cat) {
        return (
            <div className="space-y-6">
                <h1 className="text-2xl font-semibold">Settings</h1>
                <Card>
                    <CardContent className="text-sm text-muted">No server reported settings yet.</CardContent>
                </Card>
            </div>
        );
    }

    const value = (path: string) => (path in draft ? draft[path] : file.values[path]);
    const set = (path: string, v: unknown) => setDraft((d) => ({ ...d, [path]: v }));

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-2xl font-semibold mr-auto">Settings</h1>
                <Button variant="ghost" onClick={() => setView(view === 'history' ? 'settings' : 'history')}>
                    <HistoryIcon className="h-4 w-4" /> {view === 'history' ? 'Back to settings' : 'History'}
                </Button>
                {notice && <span className="text-sm text-success">{notice}</span>}
                <Button variant="ghost" disabled={!dirty || saving} onClick={() => setDraft({})}>
                    <RotateCcw className="h-4 w-4" /> Discard
                </Button>
                <Button disabled={!dirty || saving} onClick={save}>
                    <Save className="h-4 w-4" /> Save{dirty ? ` (${dirty})` : ''}
                </Button>
            </div>
            {error && <div className="text-error text-sm">{error}</div>}

            {view === 'history' ? (
                <HistoryView files={files} onUndone={refresh} />
            ) : (
            <>
            <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="text-muted">Apply to</span>
                <select
                    className="rounded-md bg-bg-raised border border-border px-3 py-1.5 text-sm"
                    value={group}
                    onChange={(e) => pickGroup(e.target.value)}
                >
                    <option value="">Every server</option>
                    {groups.map((g) => (
                        <option key={g} value={g}>
                            {g} servers only
                        </option>
                    ))}
                </select>
                {group && (
                    <span className="text-xs text-muted">
                        A value saved for {group} servers wins over the value for every server.
                    </span>
                )}
            </div>

            <div className="flex flex-wrap gap-2">
                {files.map((f) => (
                    <button
                        key={f.id}
                        onClick={() => pick(f.id)}
                        className={cn(
                            'rounded-md border px-3 py-1.5 text-sm transition',
                            f.id === file.id
                                ? 'border-primary/50 bg-primary/10 text-primary'
                                : 'border-border text-slate-300 hover:bg-bg-card',
                        )}
                    >
                        {f.title}
                    </button>
                ))}
            </div>

            <div className="text-xs text-muted">
                On {file.hosts.length === 1 ? file.hosts[0] : `${file.hosts.length} servers: ${file.hosts.join(', ')}`}.
                Changes go to every one of them.
            </div>

            <div className="grid gap-4 md:grid-cols-[12rem_1fr]">
                <nav className="flex md:flex-col gap-1 overflow-x-auto">
                    {file.schema.categories.map((c) => (
                        <button
                            key={c.id}
                            onClick={() => setCategory(c.id)}
                            className={cn(
                                'whitespace-nowrap text-left rounded-md px-3 py-1.5 text-sm transition',
                                c.id === cat.id ? 'bg-primary/10 text-primary' : 'text-slate-300 hover:bg-bg-card',
                            )}
                        >
                            {c.title ?? c.id}
                        </button>
                    ))}
                </nav>
                <Card>
                    <CardHeader>
                        <CardTitle>{cat.title ?? cat.id}</CardTitle>
                    </CardHeader>
                    <CardContent className="divide-y divide-border">
                        {cat.fields.map((f) => (
                            <FieldRow
                                key={f.path}
                                field={f}
                                fileApply={file.schema.apply}
                                value={value(f.path)}
                                changed={f.path in draft}
                                divergent={file.divergent.includes(f.path)}
                                onChange={(v) => set(f.path, v)}
                            />
                        ))}
                    </CardContent>
                </Card>
            </div>
            </>
            )}
        </div>
    );
}

interface HistoryEntry {
    id: string;
    file: string;
    group: string;
    path: string;
    oldValue: unknown;
    newValue: unknown;
    at: number;
    actor: string;
    revertedBy: string | null;
}

function short(v: unknown) {
    if (v === null || v === undefined) return 'not set';
    const s = typeof v === 'string' ? v : JSON.stringify(v);
    return s.length > 60 ? s.slice(0, 57) + '...' : s;
}

function HistoryView({ files, onUndone }: { files: SettingsFile[]; onUndone: () => void }) {
    const [entries, setEntries] = useState<HistoryEntry[] | null>(null);
    const [error, setError] = useState<string | null>(null);
    const titles = Object.fromEntries(files.map((f) => [f.id, f.title]));

    const load = useCallback(async () => {
        try {
            setEntries(await api<HistoryEntry[]>('/api/settings-history?limit=200'));
        } catch (err) {
            setError((err as Error).message);
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    async function undo(e: HistoryEntry) {
        if (!window.confirm(`Put ${e.path} back to ${short(e.oldValue)}?`)) return;
        try {
            await apiPost(`/api/settings-history/${e.id}/revert`);
            load();
            window.setTimeout(onUndone, 1200);
        } catch (err) {
            setError((err as Error).message);
        }
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>Recent changes</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
                {error && <div className="text-error text-sm">{error}</div>}
                {entries?.length === 0 && <div className="text-sm text-muted">No changes yet.</div>}
                <div className="divide-y divide-border">
                    {(entries ?? []).map((e) => (
                        <div key={e.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2 text-sm">
                            <span className="text-muted tabular-nums w-36">{new Date(e.at).toLocaleString()}</span>
                            <span className="w-40 truncate">{e.actor}</span>
                            <span className="font-medium">{titles[e.file] ?? e.file}</span>
                            {e.group && <Badge variant="muted">{e.group} only</Badge>}
                            <code className="text-xs text-muted">{e.path}</code>
                            <span className="text-xs">
                                <span className="text-muted line-through">{short(e.oldValue)}</span>
                                {' -> '}
                                <span>{short(e.newValue)}</span>
                            </span>
                            <span className="ml-auto">
                                {e.revertedBy ? (
                                    <Badge variant="muted">undone</Badge>
                                ) : (
                                    <Button variant="ghost" onClick={() => undo(e)} title="Undo this change">
                                        <Undo2 className="h-4 w-4" /> Undo
                                    </Button>
                                )}
                            </span>
                        </div>
                    ))}
                </div>
            </CardContent>
        </Card>
    );
}

function applyLabel(apply?: string) {
    switch ((apply ?? 'live').toLowerCase()) {
        case 'next-match':
            return 'next match';
        case 'restart':
            return 'needs restart';
        default:
            return null;
    }
}

function FieldRow({
    field,
    fileApply,
    value,
    changed,
    divergent,
    onChange,
}: {
    field: Field;
    fileApply?: string;
    value: unknown;
    changed?: boolean;
    divergent?: boolean;
    onChange: (v: unknown) => void;
}) {
    const apply = applyLabel(field.apply ?? fileApply);
    const wide = field.type === 'map' || field.type === 'list' || field.type === 'text-list';
    return (
        <div className={cn('py-3 gap-3', wide ? 'space-y-3' : 'flex flex-wrap items-start')}>
            <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2 text-sm font-medium">
                    {field.title ?? field.path}
                    {changed && <Badge>edited</Badge>}
                    {apply && <Badge variant="muted">{apply}</Badge>}
                    {divergent && <Badge variant="warning">differs between servers</Badge>}
                </div>
                {field.help && <div className="text-xs text-muted mt-0.5">{field.help}</div>}
            </div>
            <div className={wide ? '' : 'w-full sm:w-64'}>
                <Editor field={field} value={value} onChange={onChange} />
            </div>
        </div>
    );
}

function Editor({ field, value, onChange }: { field: Field; value: unknown; onChange: (v: unknown) => void }) {
    switch (field.type) {
        case 'boolean':
            return (
                <button
                    role="switch"
                    aria-checked={value === true}
                    onClick={() => onChange(value !== true)}
                    className={cn(
                        'relative h-6 w-11 rounded-full border transition',
                        value === true ? 'bg-primary/40 border-primary/60' : 'bg-bg-raised border-border',
                    )}
                >
                    <span
                        className={cn(
                            'absolute top-0.5 h-[18px] w-[18px] rounded-full bg-slate-200 transition-all',
                            value === true ? 'left-[22px]' : 'left-0.5',
                        )}
                    />
                </button>
            );
        case 'int':
        case 'number':
            return (
                <Input
                    type="number"
                    value={value === undefined || value === null ? '' : String(value)}
                    min={field.min}
                    max={field.max}
                    step={field.step ?? (field.type === 'int' ? 1 : 0.1)}
                    onChange={(e) => {
                        const raw = e.target.value;
                        onChange(raw === '' ? raw : Number(raw));
                    }}
                />
            );
        case 'enum':
            return (
                <select
                    className="w-full rounded-md bg-bg-raised border border-border px-3 py-2 text-sm"
                    value={String(value ?? '')}
                    onChange={(e) => onChange(e.target.value)}
                >
                    {(field.options ?? []).map((o) => (
                        <option key={o} value={o}>
                            {o}
                        </option>
                    ))}
                </select>
            );
        case 'material':
            return (
                <Input
                    value={String(value ?? '')}
                    placeholder="e.g. IRON_SWORD"
                    onChange={(e) => onChange(e.target.value.toUpperCase().replace(/\s+/g, '_'))}
                />
            );
        case 'text-list': {
            const lines = Array.isArray(value) ? value.map(String) : [];
            return (
                <textarea
                    className="w-full min-h-20 rounded-md bg-bg-raised border border-border px-3 py-2 text-sm font-mono"
                    value={lines.join('\n')}
                    placeholder="One per line"
                    onChange={(e) => onChange(e.target.value === '' ? [] : e.target.value.split('\n'))}
                />
            );
        }
        case 'map':
            return <MapEditor field={field} value={value} onChange={onChange} />;
        case 'list':
            return <ListEditor field={field} value={value} onChange={onChange} />;
        default:
            if (field.secret) {
                return (
                    <Input
                        type="password"
                        value={value === HIDDEN ? '' : String(value ?? '')}
                        placeholder={value === HIDDEN ? 'Set. Type to replace' : 'Not set'}
                        onChange={(e) => onChange(e.target.value)}
                    />
                );
            }
            return <Input value={String(value ?? '')} onChange={(e) => onChange(e.target.value)} />;
    }
}

function getIn(obj: unknown, path: string): unknown {
    let cur = obj;
    for (const seg of path.split('.')) {
        if (cur === null || typeof cur !== 'object') return undefined;
        cur = (cur as Obj)[seg];
    }
    return cur;
}

function setIn(obj: Obj, path: string, value: unknown): Obj {
    const segs = path.split('.');
    const out: Obj = { ...obj };
    let cur = out;
    for (let i = 0; i < segs.length - 1; i++) {
        const next = cur[segs[i]];
        cur[segs[i]] = next && typeof next === 'object' && !Array.isArray(next) ? { ...(next as Obj) } : {};
        cur = cur[segs[i]] as Obj;
    }
    cur[segs[segs.length - 1]] = value;
    return out;
}

function asEntry(item: unknown): Obj {
    if (typeof item === 'string') return { material: item };
    return item && typeof item === 'object' ? (item as Obj) : {};
}

function EntryFields({ field, entry, onChange }: { field: Field; entry: Obj; onChange: (e: Obj) => void }) {
    return (
        <div className="divide-y divide-border">
            {(field.fields ?? []).map((child) => (
                <FieldRow
                    key={child.path}
                    field={child}
                    value={getIn(entry, child.path)}
                    onChange={(v) => onChange(setIn(entry, child.path, v))}
                />
            ))}
        </div>
    );
}

function MapEditor({ field, value, onChange }: { field: Field; value: unknown; onChange: (v: unknown) => void }) {
    const entries = value && typeof value === 'object' && !Array.isArray(value) ? (value as Obj) : {};
    const [open, setOpen] = useState<string | null>(null);
    const [newId, setNewId] = useState('');
    const idOk = /^[A-Za-z0-9_-]+$/.test(newId) && !(newId in entries);

    return (
        <div className="space-y-2">
            {Object.entries(entries).map(([id, entry]) => (
                <div key={id} className="rounded-md border border-border">
                    <div className="flex items-center gap-2 px-3 py-2">
                        <button className="flex-1 text-left text-sm" onClick={() => setOpen(open === id ? null : id)}>
                            <span className="font-medium">
                                {getIn(entry, 'name') ? String(getIn(entry, 'name')).replace(/<[^>]+>/g, '') : id}
                            </span>
                            {getIn(entry, 'name') !== undefined && <span className="text-muted ml-2 text-xs">{id}</span>}
                        </button>
                        <Button
                            variant="danger"
                            onClick={() => {
                                if (!window.confirm(`Remove ${id}?`)) return;
                                const next = { ...entries };
                                delete next[id];
                                onChange(next);
                            }}
                        >
                            <Trash2 className="h-4 w-4" />
                        </Button>
                    </div>
                    {open === id && (
                        <div className="border-t border-border px-3">
                            <EntryFields
                                field={field}
                                entry={asEntry(entry)}
                                onChange={(e) => onChange({ ...entries, [id]: e })}
                            />
                        </div>
                    )}
                </div>
            ))}
            <div className="flex gap-2">
                <Input
                    value={newId}
                    placeholder="New id (letters, digits, - and _)"
                    onChange={(e) => setNewId(e.target.value.trim())}
                    className="sm:w-64"
                />
                <Button
                    disabled={!idOk}
                    onClick={() => {
                        onChange({ ...entries, [newId]: { ...(field['new-entry'] ?? {}) } });
                        setOpen(newId);
                        setNewId('');
                    }}
                >
                    <Plus className="h-4 w-4" /> Add
                </Button>
            </div>
        </div>
    );
}

function itemLabel(item: Obj, i: number) {
    const name = item.name ? String(item.name).replace(/<[^>]+>/g, '') : null;
    const material = item.material ? String(item.material).toLowerCase().replace(/_/g, ' ') : null;
    const amount = typeof item.amount === 'number' && item.amount > 1 ? ` x${item.amount}` : '';
    if (name && material) return `${name} (${material})${amount}`;
    return (material ?? name ?? `Item ${i + 1}`) + amount;
}

function ListEditor({ field, value, onChange }: { field: Field; value: unknown; onChange: (v: unknown) => void }) {
    const items = Array.isArray(value) ? value.map(asEntry) : [];
    const [open, setOpen] = useState<number | null>(null);

    function move(i: number, d: number) {
        const next = [...items];
        [next[i], next[i + d]] = [next[i + d], next[i]];
        onChange(next);
        setOpen(null);
    }

    return (
        <div className="space-y-2">
            {items.map((item, i) => (
                <div key={i} className="rounded-md border border-border">
                    <div className="flex items-center gap-1 px-3 py-2">
                        <button className="flex-1 text-left text-sm" onClick={() => setOpen(open === i ? null : i)}>
                            {itemLabel(item, i)}
                            {typeof item.slot === 'number' && (
                                <span className="text-muted ml-2 text-xs">slot {String(item.slot)}</span>
                            )}
                        </button>
                        <Button variant="ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">
                            <ArrowUp className="h-4 w-4" />
                        </Button>
                        <Button
                            variant="ghost"
                            disabled={i === items.length - 1}
                            onClick={() => move(i, 1)}
                            aria-label="Move down"
                        >
                            <ArrowDown className="h-4 w-4" />
                        </Button>
                        <Button
                            variant="danger"
                            onClick={() => {
                                onChange(items.filter((_, j) => j !== i));
                                setOpen(null);
                            }}
                            aria-label="Remove"
                        >
                            <Trash2 className="h-4 w-4" />
                        </Button>
                    </div>
                    {open === i && (
                        <div className="border-t border-border px-3">
                            <EntryFields
                                field={field}
                                entry={item}
                                onChange={(e) => onChange(items.map((x, j) => (j === i ? e : x)))}
                            />
                        </div>
                    )}
                </div>
            ))}
            <Button
                onClick={() => {
                    const fresh = { ...(field['new-entry'] ?? {}) };
                    if (Object.keys(fresh).length === 0 && field.fields?.some((f) => f.path === 'material')) {
                        fresh.material = 'STONE';
                    }
                    onChange([...items, fresh]);
                    setOpen(items.length);
                }}
            >
                <Plus className="h-4 w-4" /> Add item
            </Button>
            {field['from-inventory'] && (
                <div className="text-xs text-muted">
                    In game, /tea admin can fill this list from the items you carry.
                </div>
            )}
        </div>
    );
}
