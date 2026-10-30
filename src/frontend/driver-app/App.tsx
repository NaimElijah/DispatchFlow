import { StatusBar } from 'expo-status-bar'
import { StyleSheet, Text, View } from 'react-native'

/** The driver app's root component. */
export default function App() {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header">DispatchFlow</Text>
      <StatusBar style="auto" />
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
})
