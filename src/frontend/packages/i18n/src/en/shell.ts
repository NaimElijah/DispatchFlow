/**
 * The English strings of an app's shell: its header, its navigation and the pages no area
 * owns. The source of the namespace's shape, which `he/shell.ts` must match key for key.
 */
export const shell = {
  productName: 'DispatchFlow',
  pageTitle: '{{page}} · DispatchFlow',
  navigation: {
    label: 'Main',
    open: 'Open the menu',
    close: 'Close the menu',
    planning: 'Planning',
    orders: 'Orders',
    import: 'Import',
    fleet: 'Fleet',
    settings: 'Settings',
  },
  notBuilt: 'This screen is not built yet.',
  notFound: {
    title: 'Page not found',
    body: 'No page has this address.',
    home: 'Go to the planning screen',
  },
  error: {
    title: 'Something went wrong',
    body: 'This page could not be shown. Try again, and reload the page if it keeps happening.',
    retry: 'Try again',
  },
} as const
