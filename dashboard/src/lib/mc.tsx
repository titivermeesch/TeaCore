import { ReactNode } from 'react';

// Legacy Minecraft colour codes (as the server renders sidebars).
const COLORS: Record<string, string> = {
    '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
    '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
    '8': '#555555', '9': '#5555FF', a: '#55FF55', b: '#55FFFF',
    c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF',
};

/**
 * Renders a string with legacy colour codes (section sign + code, including
 * the hex form section x + 6 pairs) as coloured spans.
 */
export function McText({ text }: { text: string }) {
    const parts: ReactNode[] = [];
    let color: string | undefined;
    let bold = false;
    let italic = false;
    let underline = false;
    let strike = false;
    let buf = '';
    const flush = (key: number) => {
        if (!buf) return;
        parts.push(
            <span
                key={key}
                style={{
                    color,
                    fontWeight: bold ? 700 : undefined,
                    fontStyle: italic ? 'italic' : undefined,
                    textDecoration: [underline && 'underline', strike && 'line-through'].filter(Boolean).join(' ') || undefined,
                }}
            >
                {buf}
            </span>,
        );
        buf = '';
    };
    for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        if (ch === '§' && i + 1 < text.length) {
            const code = text[i + 1].toLowerCase();
            flush(i);
            if (code === 'x' && i + 13 < text.length) {
                // section x section r section r section g section g section b section b
                let hex = '#';
                for (let j = 0; j < 6; j++) hex += text[i + 3 + j * 2];
                color = hex;
                bold = italic = underline = strike = false;
                i += 13;
                continue;
            }
            if (COLORS[code]) {
                color = COLORS[code];
                bold = italic = underline = strike = false;
            } else if (code === 'l') bold = true;
            else if (code === 'o') italic = true;
            else if (code === 'n') underline = true;
            else if (code === 'm') strike = true;
            else if (code === 'r') {
                color = undefined;
                bold = italic = underline = strike = false;
            }
            i++;
            continue;
        }
        buf += ch;
    }
    flush(text.length);
    return <>{parts}</>;
}

export function duration(seconds: number): string {
    if (seconds < 0) return '-';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${String(s).padStart(2, '0')}`;
}

export function ago(iso: string): string {
    const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.floor(s / 60)}m ago`;
    if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
    return `${Math.floor(s / 86400)}d ago`;
}

export function dateTime(iso: string): string {
    return new Date(iso).toLocaleString();
}
