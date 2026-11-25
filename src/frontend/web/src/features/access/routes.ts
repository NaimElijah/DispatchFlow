import { createRoute } from '@tanstack/react-router'
import { shellRoute } from '../../routes/shellRoute.ts'
import { SettingsPage } from './SettingsPage.tsx'

/** `/settings`: the Settings page. */
export const settingsRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: 'settings',
  component: SettingsPage,
})

/** The Access area's routes, which `src/routeTree.ts` gathers. */
export const accessRoutes = [settingsRoute] as const
