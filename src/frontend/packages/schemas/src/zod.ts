/**
 * Zod, set never to compile code at run time: the one `z` this package's schemas and the apps use,
 * which ESLint holds them to. ADR-069's Content Security Policy refuses compiled code, and the
 * browser reports even the probe Zod makes to see whether it may, though Zod catches the refusal.
 * An object schema reads the setting when it is built, not when it parses, so it is set here,
 * before any schema is built (ADR-154).
 *
 * @module
 */
import { z } from 'zod'

z.config({ jitless: true })

export { z }
