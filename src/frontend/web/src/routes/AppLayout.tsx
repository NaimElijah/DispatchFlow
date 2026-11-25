import { Burger, NavLink, type NavLinkProps } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { createLink, Link, Outlet, useRouterState } from '@tanstack/react-router'
import { useId, type ComponentPropsWithRef } from 'react'
import { useTranslation } from 'react-i18next'
import classes from './AppLayout.module.css'

/**
 * Whether the address is one no route knows, which only the catch-all route matched. It belongs to
 * no area, so no navigation item is current, though the router marks one whose path the address
 * begins with: `/plans/2026-02-30` would mark Planning. Inside an area, a page that finds its
 * subject missing keeps its area current, as any of the area's pages does.
 */
function useShowsUnknownAddress(): boolean {
  return useRouterState({
    select: (state) => state.matches.some((match) => match.routeId === '/_shell/$'),
  })
}

/** Mantine's navigation item on a plain anchor, highlighted when the router marks it current. */
function NavigationAnchor({
  'aria-current': ariaCurrent,
  ...props
}: NavLinkProps & ComponentPropsWithRef<'a'>) {
  const unknownAddress = useShowsUnknownAddress()
  const current = ariaCurrent === 'page' && !unknownAddress
  return <NavLink {...props} active={current} aria-current={current ? 'page' : undefined} />
}

/** A navigation item typed as the router's `Link`, which sets `aria-current` on the current one. */
const NavigationLink = createLink(NavigationAnchor)

/**
 * The shell every page sits in: a header with the product name, the navigation, and a main area
 * that gives its page the full remaining height, for the planning map.
 *
 * The layout is this file's own CSS rather than Mantine's `AppShell`, which injects a `<style>`
 * element that ADR-069's Content Security Policy blocks (ADR-143). On a phone-sized screen the
 * navigation folds behind a menu button.
 */
export function AppLayout() {
  const { t } = useTranslation('shell')
  const [menuOpen, menu] = useDisclosure(false)
  const navigationId = useId()

  return (
    <div className={classes.layout}>
      <header className={classes.header}>
        <Burger
          hiddenFrom="sm"
          size="sm"
          opened={menuOpen}
          onClick={menu.toggle}
          aria-label={menuOpen ? t('navigation.close') : t('navigation.open')}
          aria-expanded={menuOpen}
          aria-controls={navigationId}
        />
        <Link to="/" className={classes.productName}>
          {t('productName')}
        </Link>
      </header>
      <nav
        id={navigationId}
        aria-label={t('navigation.label')}
        className={classes.navigation}
        data-open={menuOpen || undefined}
      >
        {/* `/plans` opens tomorrow's plan, and the item stays current on every plan's page. */}
        <NavigationLink to="/plans" label={t('navigation.planning')} onClick={menu.close} />
        <NavigationLink
          to="/orders"
          activeOptions={{ exact: true }}
          label={t('navigation.orders')}
          onClick={menu.close}
        />
        <NavigationLink to="/orders/import" label={t('navigation.import')} onClick={menu.close} />
        <NavigationLink to="/fleet" label={t('navigation.fleet')} onClick={menu.close} />
        <NavigationLink to="/settings" label={t('navigation.settings')} onClick={menu.close} />
      </nav>
      <main className={classes.main}>
        <Outlet />
      </main>
    </div>
  )
}
