import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import App from './App.tsx'

test('GivenTheWebApp_WhenRendered_ThenShowsTheProductName', () => {
  render(<App />)

  expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('DispatchFlow')
})
