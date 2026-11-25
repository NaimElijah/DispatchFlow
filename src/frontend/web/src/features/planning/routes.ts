import { createRoute, redirect } from '@tanstack/react-router'
import { shellRoute } from '../../routes/shellRoute.ts'
import { isPlanDate, tomorrowInIsrael } from './planDate.ts'
import { PlanPage } from './PlanPage.tsx'

/**
 * `/plans`: the planning screen with no date chosen, which opens tomorrow's plan in Israel's time,
 * the date a dispatcher plans (ADR-088, ADR-143). It replaces itself in the history, so Back does
 * not return to a redirect.
 */
export const plansRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: 'plans',
  beforeLoad: ({ context }) => {
    throw redirect({
      to: '/plans/$date',
      params: { date: tomorrowInIsrael(context.now()) },
      replace: true,
    })
  },
})

/**
 * `/plans/$date`: one delivery date's plan (TS §10). A date that is not a real `YYYY-MM-DD` day
 * matches no route, so it shows the not-found page.
 */
export const planRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: 'plans/$date',
  params: {
    parse: ({ date }) => (isPlanDate(date) ? { date } : false),
    stringify: ({ date }) => ({ date }),
  },
  component: PlanPage,
})

/** The Planning area's routes, which `src/routeTree.ts` gathers. */
export const planningRoutes = [plansRoute, planRoute] as const
