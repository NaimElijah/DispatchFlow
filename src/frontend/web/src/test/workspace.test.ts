import lockfile from '../../../pnpm-lock.yaml?raw'
import { expect, test } from 'vitest'

// The rule of src/frontend/CLAUDE.md: a @dispatchflow/* package comes only from the workspace,
// because anyone can publish under that name on the public registry. The lockfile lists a package
// it resolved from a registry under `packages:` at two spaces, and a workspace package only under
// `importers:`, as a `link:`.
test('GivenTheWorkspaceLockfile_WhenItsDispatchflowPackagesAreRead_ThenEachIsAWorkspaceLink', () => {
  expect(lockfile).toMatch(
    /'@dispatchflow\/tsconfig':\r?\n\s+specifier: workspace:\*\r?\n\s+version: link:/,
  )
  expect(lockfile).not.toMatch(/^ {2}'@dispatchflow\//m)
})
