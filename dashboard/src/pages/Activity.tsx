import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { cn } from '../lib/cn';

interface Sample {
    at: number;
    players: number;
    peakPlayers: number;
    queued: number;
    arenas: number;
    live: number;
}

interface DayMatches {
    day: string;
    games: Record<string, number>;
}

interface Charts {
    range: string;
    bucketMs: number;
    samples: Sample[];
    matches: DayMatches[];
}

type Range = '24h' | '7d' | '30d';

// Categorical slots validated (dataviz validate_palette.js) against the card
// surface in dark mode; games keep their slot by name, not by rank.
const SERIES = ['#3987e5', '#d95926', '#199e70', '#c98500'];
const OTHER = '#7a8a99';

function useWidth<T extends HTMLElement>() {
    const ref = useRef<T>(null);
    const [width, setWidth] = useState(600);
    useEffect(() => {
        if (!ref.current) return;
        const ro = new ResizeObserver((entries) => setWidth(Math.max(280, entries[0].contentRect.width)));
        ro.observe(ref.current);
        return () => ro.disconnect();
    }, []);
    return { ref, width };
}

function niceMax(v: number) {
    if (v <= 4) return 4;
    const pow = 10 ** Math.floor(Math.log10(v));
    for (const m of [1, 2, 2.5, 5, 10]) if (m * pow >= v) return m * pow;
    return 10 * pow;
}

function timeLabel(at: number, range: Range) {
    const d = new Date(at);
    return range === '24h'
        ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        : d.toLocaleDateString([], { month: 'short', day: 'numeric' }) +
              (range === '7d' ? ` ${d.toLocaleTimeString([], { hour: '2-digit' })}` : '');
}

export default function Activity() {
    const [range, setRange] = useState<Range>('24h');
    const [data, setData] = useState<Charts | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let alive = true;
        const load = () =>
            api<Charts>(`/api/charts?range=${range}`)
                .then((d) => alive && setData(d))
                .catch((e) => alive && setError((e as Error).message));
        load();
        const t = window.setInterval(load, 60_000);
        return () => {
            alive = false;
            window.clearInterval(t);
        };
    }, [range]);

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-2xl font-semibold mr-auto">Activity</h1>
                <div className="flex gap-1 rounded-md border border-border p-1">
                    {(['24h', '7d', '30d'] as Range[]).map((r) => (
                        <button
                            key={r}
                            onClick={() => setRange(r)}
                            className={cn(
                                'rounded px-3 py-1 text-sm',
                                r === range ? 'bg-primary/10 text-primary' : 'text-slate-300 hover:bg-bg-card',
                            )}
                        >
                            {r}
                        </button>
                    ))}
                </div>
            </div>
            {error && <div className="text-error text-sm">{error}</div>}
            {data && data.samples.length === 0 && (
                <div className="text-sm text-muted">
                    No samples yet. The server running the dashboard records one every minute.
                </div>
            )}
            {data && (
                <div className="grid xl:grid-cols-2 gap-4">
                    <LineCard title="Players online" samples={data.samples} pick={(s) => s.players} range={range} unit="players" />
                    <LineCard title="Players queued" samples={data.samples} pick={(s) => s.queued} range={range} unit="queued" />
                    <LineCard title="Matches running" samples={data.samples} pick={(s) => s.live} range={range} unit="matches" />
                    <MatchesCard days={data.matches} />
                </div>
            )}
        </div>
    );
}

function LineCard({
    title,
    samples,
    pick,
    range,
    unit,
}: {
    title: string;
    samples: Sample[];
    pick: (s: Sample) => number;
    range: Range;
    unit: string;
}) {
    const { ref, width } = useWidth<HTMLDivElement>();
    const [hover, setHover] = useState<number | null>(null);
    const height = 200;
    const pad = { l: 36, r: 12, t: 12, b: 24 };
    const values = samples.map(pick);
    const max = niceMax(Math.max(0, ...values));
    const x = (i: number) => pad.l + (samples.length <= 1 ? 0 : (i / (samples.length - 1)) * (width - pad.l - pad.r));
    const y = (v: number) => pad.t + (1 - v / max) * (height - pad.t - pad.b);
    const line = values.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
    const area = samples.length > 1 ? `${line} L${x(samples.length - 1)},${y(0)} L${x(0)},${y(0)} Z` : '';
    const now = values.length ? values[values.length - 1] : 0;
    const ticks = [0, max / 2, max];
    const labelIdx = samples.length > 1 ? [0, Math.floor((samples.length - 1) / 2), samples.length - 1] : [0];

    function onMove(e: React.MouseEvent<SVGSVGElement>) {
        if (samples.length === 0) return;
        const rect = e.currentTarget.getBoundingClientRect();
        const px = ((e.clientX - rect.left) / rect.width) * width;
        const i = Math.round(((px - pad.l) / (width - pad.l - pad.r)) * (samples.length - 1));
        setHover(Math.max(0, Math.min(samples.length - 1, i)));
    }

    return (
        <Card>
            <CardHeader>
                <div className="flex items-baseline gap-2">
                    <CardTitle>{title}</CardTitle>
                    <span className="ml-auto text-sm text-muted">
                        now <span className="text-slate-200 font-semibold tabular-nums">{Math.round(now)}</span>
                    </span>
                </div>
            </CardHeader>
            <CardContent>
                <div ref={ref} className="relative">
                    {samples.length === 0 ? (
                        <div className="h-[200px] grid place-items-center text-sm text-muted">No data yet</div>
                    ) : (
                        <svg
                            viewBox={`0 0 ${width} ${height}`}
                            width="100%"
                            height={height}
                            onMouseMove={onMove}
                            onMouseLeave={() => setHover(null)}
                            role="img"
                            aria-label={`${title} over time`}
                        >
                            {ticks.map((t) => (
                                <g key={t}>
                                    <line x1={pad.l} x2={width - pad.r} y1={y(t)} y2={y(t)} stroke="#253140" strokeWidth={1} />
                                    <text x={pad.l - 6} y={y(t) + 4} textAnchor="end" fontSize={11} fill="#7a8a99">
                                        {Math.round(t)}
                                    </text>
                                </g>
                            ))}
                            {labelIdx.map((i) => (
                                <text
                                    key={i}
                                    x={x(i)}
                                    y={height - 6}
                                    fontSize={11}
                                    fill="#7a8a99"
                                    textAnchor={i === 0 ? 'start' : i === samples.length - 1 ? 'end' : 'middle'}
                                >
                                    {timeLabel(samples[i].at, range)}
                                </text>
                            ))}
                            <path d={area} fill={SERIES[0]} opacity={0.12} />
                            <path d={line} fill="none" stroke={SERIES[0]} strokeWidth={2} strokeLinejoin="round" />
                            {hover != null && (
                                <g>
                                    <line x1={x(hover)} x2={x(hover)} y1={pad.t} y2={height - pad.b} stroke="#7a8a99" strokeDasharray="3 3" />
                                    <circle cx={x(hover)} cy={y(values[hover])} r={4} fill={SERIES[0]} stroke="#1a232d" strokeWidth={2} />
                                </g>
                            )}
                        </svg>
                    )}
                    {hover != null && samples[hover] && (
                        <div
                            className="pointer-events-none absolute top-0 rounded-md border border-border bg-bg-raised px-2 py-1 text-xs shadow"
                            style={{ left: Math.min(Math.max(0, (x(hover) / width) * 100), 75) + '%' }}
                        >
                            <div className="text-muted">{new Date(samples[hover].at).toLocaleString()}</div>
                            <div className="tabular-nums">
                                {values[hover]} {unit}
                                {unit === 'players' && samples[hover].peakPlayers > values[hover] && (
                                    <span className="text-muted"> (peak {samples[hover].peakPlayers})</span>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}

function MatchesCard({ days }: { days: DayMatches[] }) {
    const { ref, width } = useWidth<HTMLDivElement>();
    const [hover, setHover] = useState<number | null>(null);
    const height = 200;
    const pad = { l: 36, r: 12, t: 12, b: 24 };

    // Busiest games keep the four colours; any further games fold into "Other".
    const games = useMemo(() => {
        const totals: Record<string, number> = {};
        for (const d of days) for (const [g, n] of Object.entries(d.games)) totals[g] = (totals[g] ?? 0) + n;
        const sorted = Object.keys(totals).sort((a, b) => totals[b] - totals[a] || a.localeCompare(b));
        const top = sorted.slice(0, SERIES.length).sort();
        return { top, other: sorted.length > SERIES.length };
    }, [days]);
    const color = (g: string) => SERIES[games.top.indexOf(g)] ?? OTHER;
    const stack = (d: DayMatches) => {
        const parts: { game: string; n: number }[] = games.top.map((g) => ({ game: g, n: d.games[g] ?? 0 }));
        const other = Object.entries(d.games)
            .filter(([g]) => !games.top.includes(g))
            .reduce((a, [, n]) => a + n, 0);
        if (other) parts.push({ game: 'Other', n: other });
        return parts;
    };
    const totals = days.map((d) => Object.values(d.games).reduce((a, n) => a + n, 0));
    const max = niceMax(Math.max(0, ...totals));
    const slot = (width - pad.l - pad.r) / Math.max(1, days.length);
    const barW = Math.max(4, Math.min(28, slot - 6));
    const y = (v: number) => pad.t + (1 - v / max) * (height - pad.t - pad.b);
    const total = totals.reduce((a, n) => a + n, 0);

    return (
        <Card>
            <CardHeader>
                <div className="flex items-baseline gap-2">
                    <CardTitle>Matches per day</CardTitle>
                    <span className="ml-auto text-sm text-muted">
                        <span className="text-slate-200 font-semibold tabular-nums">{total}</span> in this period
                    </span>
                </div>
                {(games.top.length > 1 || games.other) && (
                    <div className="flex flex-wrap gap-3 text-xs text-muted">
                        {games.top.map((g) => (
                            <span key={g} className="inline-flex items-center gap-1.5">
                                <span className="h-2.5 w-2.5 rounded-sm" style={{ background: color(g) }} />
                                {g}
                            </span>
                        ))}
                        {games.other && (
                            <span className="inline-flex items-center gap-1.5">
                                <span className="h-2.5 w-2.5 rounded-sm" style={{ background: OTHER }} />
                                Other
                            </span>
                        )}
                    </div>
                )}
            </CardHeader>
            <CardContent>
                <div ref={ref} className="relative">
                    <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} onMouseLeave={() => setHover(null)}
                        role="img" aria-label="Matches per day by game">
                        {[0, max / 2, max].map((t) => (
                            <g key={t}>
                                <line x1={pad.l} x2={width - pad.r} y1={y(t)} y2={y(t)} stroke="#253140" strokeWidth={1} />
                                <text x={pad.l - 6} y={y(t) + 4} textAnchor="end" fontSize={11} fill="#7a8a99">
                                    {Math.round(t)}
                                </text>
                            </g>
                        ))}
                        {days.map((d, i) => {
                            const cx = pad.l + slot * i + slot / 2;
                            let base = 0;
                            const parts = stack(d).filter((p) => p.n > 0);
                            return (
                                <g key={d.day} onMouseEnter={() => setHover(i)}>
                                    <rect x={cx - slot / 2} y={pad.t} width={slot} height={height - pad.t - pad.b} fill="transparent" />
                                    {parts.map((p, j) => {
                                        const top = y(base + p.n);
                                        const h = Math.max(0, y(base) - top - (j > 0 ? 2 : 0));
                                        base += p.n;
                                        const last = j === parts.length - 1;
                                        return (
                                            <rect
                                                key={p.game}
                                                x={cx - barW / 2}
                                                y={top}
                                                width={barW}
                                                height={h}
                                                rx={last ? 3 : 0}
                                                fill={p.game === 'Other' ? OTHER : color(p.game)}
                                                opacity={hover == null || hover === i ? 1 : 0.55}
                                            />
                                        );
                                    })}
                                    {(i === 0 || i === days.length - 1 || days.length <= 10) && (
                                        <text x={cx} y={height - 6} fontSize={11} fill="#7a8a99" textAnchor="middle">
                                            {d.day.slice(5)}
                                        </text>
                                    )}
                                </g>
                            );
                        })}
                    </svg>
                    {hover != null && days[hover] && (
                        <div
                            className="pointer-events-none absolute top-0 rounded-md border border-border bg-bg-raised px-2 py-1 text-xs shadow"
                            style={{ left: Math.min(Math.max(0, ((pad.l + slot * hover) / width) * 100), 70) + '%' }}
                        >
                            <div className="text-muted">{days[hover].day}</div>
                            {stack(days[hover]).filter((p) => p.n > 0).map((p) => (
                                <div key={p.game} className="tabular-nums">
                                    <span className="inline-block h-2 w-2 rounded-sm mr-1.5" style={{ background: p.game === 'Other' ? OTHER : color(p.game) }} />
                                    {p.game}: {p.n}
                                </div>
                            ))}
                            {totals[hover] === 0 && <div>No matches</div>}
                        </div>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
