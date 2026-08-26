import { Stack } from 'expo-router';

export default function ReturnsLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Returns' }} />
    </Stack>
  );
}
