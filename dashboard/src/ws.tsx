import { createContext, ReactNode, useContext, useEffect, useRef, useState } from 'react';
import { wsUrl } from './api';
import type { DashboardEvent } from './types';

/**
 * Shared dashboard WebSocket. One connection lives for the lifetime of the
 * authenticated session; pages subscribe to events via `useDashboardEvent`.
 */
type Handler = (e: DashboardEvent) => void;
type Status = 'connecting' | 'open' | 'closed';

const Ctx = createContext<{
    status: Status;
    queueCounts: Record<string, number>;
    subscribe: (h: Handler) => () => void;
} | null>(null);

export function DashboardWsProvider({ children }: { children: ReactNode }) {
    const [status, setStatus] = useState<Status>('connecting');
    const [queueCounts, setQueueCounts] = useState<Record<string, number>>({});
    const handlersRef = useRef<Set<Handler>>(new Set());
    const wsRef = useRef<WebSocket | null>(null);

    useEffect(() => {
        let cancelled = false;
        let retryHandle: number | null = null;

        function connect() {
            if (cancelled) return;
            const ws = new WebSocket(wsUrl('/api/events'));
            wsRef.current = ws;
            setStatus('connecting');
            ws.onopen = () => setStatus('open');
            ws.onclose = () => {
                setStatus('closed');
                if (!cancelled) {
                    retryHandle = window.setTimeout(connect, 2000);
                }
            };
            ws.onerror = () => ws.close();
            ws.onmessage = (msg) => {
                const event = JSON.parse(msg.data) as DashboardEvent;
                if (event.type === 'queue.counts') {
                    setQueueCounts(event.counts);
                }
                for (const handler of handlersRef.current) handler(event);
            };
        }

        connect();

        return () => {
            cancelled = true;
            if (retryHandle) window.clearTimeout(retryHandle);
            wsRef.current?.close();
        };
    }, []);

    function subscribe(h: Handler) {
        handlersRef.current.add(h);
        return () => {
            handlersRef.current.delete(h);
        };
    }

    return <Ctx.Provider value={{ status, queueCounts, subscribe }}>{children}</Ctx.Provider>;
}

export function useDashboardWs() {
    const ctx = useContext(Ctx);
    if (!ctx) throw new Error('useDashboardWs must be used inside DashboardWsProvider');
    return ctx;
}

/** Subscribe to WebSocket events with a typed handler. */
export function useDashboardEvent(handler: Handler, deps: unknown[] = []) {
    const { subscribe } = useDashboardWs();
    useEffect(() => subscribe(handler), deps); // eslint-disable-line react-hooks/exhaustive-deps
}
