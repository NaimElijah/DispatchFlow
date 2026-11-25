import { screen, within } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { renderApp } from '../test/renderApp.tsx'

// The Fleet area's routes, replaced by two whose subject is missing: one finds out in its loader,
// the other while its page renders, with its route's own not-found page, as `router.ts` asks.
vi.mock('../features/fleet/routes.ts', async () => {
  const { createRoute, notFound } = await import('@tanstack/react-router')
  const { shellRoute } = await import('./shellRoute.ts')
  const { NotFoundPage } = await import('./NotFoundPage.tsx')
  const missingInLoader = createRoute({
    getParentRoute: () => shellRoute,
    path: 'fleet',
    loader: () => {
      throw notFound()
    },
  })
  const missingInPage = createRoute({
    getParentRoute: () => shellRoute,
    path: 'fleet/missing',
    component: () => {
      throw notFound()
    },
    notFoundComponent: NotFoundPage,
  })
  return { fleetRoutes: [missingInLoader, missingInPage] as const }
})

/** Expects the not-found page inside the shell, with the Fleet area still the current item. */
async function expectNotFoundInsideTheShell(): Promise<void> {
  expect(await screen.findByRole('heading', { level: 1, name: 'Page not found' })).toBeDefined()
  expect(screen.getByRole('banner')).toBeDefined()
  const navigation = screen.getByRole('navigation', { name: 'Main' })
  expect(within(navigation).getAllByRole('link', { current: 'page' })).toEqual([
    within(navigation).getByRole('link', { name: 'Fleet' }),
  ])
}

test('GivenALoaderThatFindsNothing_WhenItsPageIsOpened_ThenTheNotFoundPageShowsInsideTheShell', async () => {
  renderApp({ path: '/fleet' })

  await expectNotFoundInsideTheShell()
})

test('GivenAPageThatFindsNothingWhileRendering_WhenOpened_ThenItsRoutesNotFoundPageShowsInsideTheShell', async () => {
  renderApp({ path: '/fleet/missing' })

  await expectNotFoundInsideTheShell()
})
