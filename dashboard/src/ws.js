import { jsx as _jsx } from "react/jsx-runtime";
import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { wsUrl } from './api';
const Ctx = createContext(null);
export function DashboardWsProvider({ children }) {
    const [status, setStatus] = useState('connecting');
    const [queueCounts, setQueueCounts] = useState({});
    const handlersRef = useRef(new Set());
    const wsRef = useRef(null);
    useEffect(() => {
        let cancelled = false;
        let retryHandle = null;
        function connect() {
            if (cancelled)
                return;
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
                const event = JSON.parse(msg.data);
                if (event.type === 'queue.counts') {
                    setQueueCounts(event.counts);
                }
                for (const handler of handlersRef.current)
                    handler(event);
            };
        }
        connect();
        return () => {
            cancelled = true;
            if (retryHandle)
                window.clearTimeout(retryHandle);
            wsRef.current?.close();
        };
    }, []);
    function subscribe(h) {
        handlersRef.current.add(h);
        return () => {
            handlersRef.current.delete(h);
        };
    }
    return _jsx(Ctx.Provider, { value: { status, queueCounts, subscribe }, children: children });
}
export function useDashboardWs() {
    const ctx = useContext(Ctx);
    if (!ctx)
        throw new Error('useDashboardWs must be used inside DashboardWsProvider');
    return ctx;
}
/** Subscribe to WebSocket events with a typed handler. */
export function useDashboardEvent(handler, deps = []) {
    const { subscribe } = useDashboardWs();
    useEffect(() => subscribe(handler), deps); // eslint-disable-line react-hooks/exhaustive-deps
}
