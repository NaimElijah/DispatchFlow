// ADR-069's Content Security Policy refuses code compiled at run time. Zod's object parser would
// compile one, and the probe that asks whether it may is already reported as a violation, though
// Zod catches the refusal. An object schema reads this setting when it is built, not when it
// parses, so every schema of this package takes `z` from here, and the setting comes first.
import { z } from 'zod'

z.config({ jitless: true })

export { z }
