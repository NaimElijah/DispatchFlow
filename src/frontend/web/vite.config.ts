import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { searchForWorkspaceRoot } from 'vite'
import { defineConfig } from 'vitest/config'

/**
 * ADR-069's Content Security Policy, which `vite preview` sends so that a production build can be
 * checked against it before Caddy serves it. `connect-src` names only our own origin until
 * GlitchTip's address is chosen (ADR-079). The dev server sends no policy: Vite injects its own
 * scripts and styles there.
 */
const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "img-src 'self' blob: data:",
  "connect-src 'self'",
  'worker-src blob:',
  "frame-ancestors 'none'",
  "base-uri 'none'",
  "form-action 'self'",
  "object-src 'none'",
].join('; ')

/** Font files, which must stay files: the policy's `font-src` falls back to `'self'`, not `data:`. */
const FONT_FILE = /\.(woff2?|ttf|otf)$/

/**
 * The core, which the dev server proxies `/api/` to, so the web app calls it on its own origin:
 * the core has no CORS configuration, and the stand-in session signs in only a request whose
 * `Host` is loopback and whose `Sec-Fetch-Site` is absent, `same-origin` or `none` (ADR-147,
 * ADR-148). The proxy forwards the browser's headers unchanged, `Sec-Fetch-Site` among them, and
 * keeps its `Host` because `changeOrigin` stays off. `just core-run`'s core listens on this default;
 * the Compose web container sets the `core` service's address instead (ADR-151). An empty value
 * counts as unset.
 */
const CORE_URL = process.env.DISPATCHFLOW_CORE_URL || 'http://127.0.0.1:8080'

/**
 * What Vite may read: the pnpm workspace, the default it finds by itself, and the client API's
 * contract, which `src/lib/problems.contract.test.ts` reads until #148 generates the problem types
 * from it. The contract is public, so the dev server may serve it too.
 */
const READABLE_ROOTS = [
  searchForWorkspaceRoot(process.cwd()),
  fileURLToPath(new URL('../../../contracts/client-api/v1/', import.meta.url)),
]

export default defineConfig({
  plugins: [react()],
  server: {
    // Vite's own default answers CORS for any localhost origin, so a page on another local port
    // could read the proxied core's answers. The app calls only its own origin, so it needs none.
    cors: false,
    fs: { allow: READABLE_ROOTS },
    proxy: {
      // An object, never the string shorthand `'/api/': CORE_URL`, which turns `changeOrigin` on.
      '/api/': { target: CORE_URL },
    },
  },
  build: {
    // Vite would inline a file under 4 KB as a data: URI, and the browser would refuse it as a
    // font. No Heebo subset is that small today (the smallest is 12 KB); this keeps it so.
    assetsInlineLimit: (filePath) => (FONT_FILE.test(filePath) ? false : undefined),
  },
  preview: {
    headers: {
      'Content-Security-Policy': CONTENT_SECURITY_POLICY,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
