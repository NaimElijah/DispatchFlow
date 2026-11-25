import { createRoute } from '@tanstack/react-router'
import { shellRoute } from '../../routes/shellRoute.ts'
import { ImportPage } from './import/ImportPage.tsx'
import { OrdersPage } from './OrdersPage.tsx'

/** `/orders`: the Orders area's page. */
export const ordersRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: 'orders',
  component: OrdersPage,
})

/** `/orders/import`: an orders file's upload and preview. */
export const orderImportRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: 'orders/import',
  component: ImportPage,
})

/** The Orders area's routes, which `src/routeTree.ts` gathers. */
export const ordersRoutes = [ordersRoute, orderImportRoute] as const
