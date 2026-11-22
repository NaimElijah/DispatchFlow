import { render, screen } from '@testing-library/react-native'
import App from './App'

test('GivenThePhoneApp_WhenRendered_ThenShowsTheProductName', async () => {
  await render(<App />)

  expect(screen.getByRole('header', { name: 'DispatchFlow' })).toBeOnTheScreen()
})
