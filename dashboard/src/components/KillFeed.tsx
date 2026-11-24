import { Link } from 'react-router-dom';
import type { KillEvent } from '../types';
import { ago } from '../lib/mc';

/** Newest first. */
export function KillFeed({ kills, showArena = false, empty = 'No kills yet.' }: {
    kills: KillEvent[];
    showArena?: boolean;
    empty?: string;
}) {
    if (kills.length === 0) return <div className="text-sm text-muted">{empty}</div>;
    return (
        <ul className="space-y-1 text-sm">
            {[...kills].reverse().map((k, i) => (
                <li key={`${k.at}-${i}`} className="flex items-baseline gap-2">
                    <span className="text-xs text-muted w-16 shrink-0 tabular-nums">{ago(k.at)}</span>
                    <span>
                        {k.killerName ? (
                            <>
                                <span className="text-slate-100 font-medium">{k.killerName}</span>
                                <span className="text-muted"> killed </span>
                            </>
                        ) : null}
                        <span className="text-slate-100 font-medium">{k.victimName}</span>
                        {!k.killerName && <span className="text-muted"> died</span>}
                        {k.assists.length > 0 && (
                            <span className="text-muted"> (assist {k.assists.join(', ')})</span>
                        )}
                        {k.cause && <span className="text-xs text-muted"> {k.cause.toLowerCase().replace(/_/g, ' ')}</span>}
                        {showArena && (
                            <Link to={`/arenas/${k.arenaId}`} className="ml-2 text-xs text-primary hover:underline">
                                {k.gameId} #{k.arenaId.slice(0, 8)}
                            </Link>
                        )}
                    </span>
                </li>
            ))}
        </ul>
    );
}
