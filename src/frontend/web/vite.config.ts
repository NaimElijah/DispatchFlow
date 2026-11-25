import react from '@vitejs/plugin-react'
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

export default defineConfig({
  plugins: [react()],
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
