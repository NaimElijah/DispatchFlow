import { accessRoutes } from './features/access/routes.ts'
import { fleetRoutes } from './features/fleet/routes.ts'
import { ordersRoutes } from './features/orders/routes.ts'
import { planningRoutes } from './features/planning/routes.ts'
import { shellRoute } from './routes/shellRoute.ts'
import { indexRoute } from './routes/indexRoute.ts'
import { notFoundRoute } from './routes/notFoundRoute.ts'
import { rootRoute } from './routes/rootRoute.ts'

/**
 * Every route of the web app. Each area declares its own in `src/features/<context>/routes.ts`, as
 * children of the shell's `shellRoute`, and this tree gathers them (ADR-143). It sits here, above
 * `routes/` and `features/`, because it is the one module that imports both: a route left out of it
 * is unreachable, and a link to it fails tsc.
 */
export const routeTree = rootRoute.addChildren([
  shellRoute.addChildren([
    indexRoute,
    ...planningRoutes,
    ...ordersRoutes,
    ...fleetRoutes,
    ...accessRoutes,
    notFoundRoute,
  ]),
])
