import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// Built assets are consumed by the TeaCore shadow jar; they live on the
// classpath under /dashboard/ and Javalin serves them from there. The empty
// base keeps all generated references relative so the bundle works whether
// it's mounted at / or a subpath.
export default defineConfig({
    plugins: [react()],
    base: '',
    resolve: {
        alias: { '@': path.resolve(__dirname, './src') },
    },
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        sourcemap: true,
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                ws: true,
            },
        },
    },
});
