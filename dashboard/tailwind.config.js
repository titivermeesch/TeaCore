/** @type {import('tailwindcss').Config} */
export default {
    content: ['./index.html', './src/**/*.{ts,tsx}'],
    darkMode: 'class',
    theme: {
        extend: {
            colors: {
                // Loosely matches TeaCore's in-game theme so the dashboard feels
                // like the same product as the scoreboard/chat rendering.
                primary: '#5fd1ff',
                accent: '#ffb454',
                bg: {
                    DEFAULT: '#0b1117',
                    raised: '#141b23',
                    card: '#1a232d',
                },
                border: '#253140',
                muted: '#7a8a99',
                success: '#4ade80',
                error: '#f87171',
            },
            fontFamily: {
                sans: ['Inter', 'system-ui', 'sans-serif'],
                mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
            },
        },
    },
    plugins: [],
};
