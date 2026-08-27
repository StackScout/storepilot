import * as LocalAuthentication from 'expo-local-authentication';
import { useEffect, useState } from 'react';

/** True only when the device has biometric hardware AND at least one face/fingerprint is actually enrolled — a device with hardware but nothing enrolled can't authenticate, so the setting shouldn't be offered. */
export async function isBiometricAvailable(): Promise<boolean> {
  const [hasHardware, isEnrolled] = await Promise.all([LocalAuthentication.hasHardwareAsync(), LocalAuthentication.isEnrolledAsync()]);
  return hasHardware && isEnrolled;
}

export function useBiometricAvailability(): boolean {
  const [available, setAvailable] = useState(false);
  useEffect(() => {
    isBiometricAvailable().then(setAvailable);
  }, []);
  return available;
}

/** disableDeviceFallback: false lets the OS fall back to passcode/PIN if Face ID/fingerprint fails — without it, a temporarily unrecognized face has no recovery path short of the app's own "Sign out instead" escape hatch. */
export async function authenticateWithBiometrics(promptMessage: string): Promise<boolean> {
  const result = await LocalAuthentication.authenticateAsync({ promptMessage, disableDeviceFallback: false });
  return result.success;
}
