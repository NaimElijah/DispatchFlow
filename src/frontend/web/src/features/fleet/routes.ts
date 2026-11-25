import { createRoute } from '@tanstack/react-router'
import { shellRoute } from '../../routes/shellRoute.ts'
import { FleetPage } from './FleetPage.tsx'

/** `/fleet`: the Fleet area's page. */
export const fleetRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: 'fleet',
  component: FleetPage,
})

/** The Fleet area's routes, which `src/routeTree.ts` gathers. */
export const fleetRoutes = [fleetRoute] as const
