import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getMyStore } from '@/api/stores';
import { updateStoreProfile, uploadStoreBanner, uploadStoreLogo } from '@/api/store-settings';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';

export default function StoreProfileScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const [uploading, setUploading] = useState<'logo' | 'banner' | null>(null);

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const store = storeQuery.data;

  const [facebookUrl, setFacebookUrl] = useState('');
  const [instagramUrl, setInstagramUrl] = useState('');
  const [tiktokUrl, setTiktokUrl] = useState('');
  useEffect(() => {
    if (store) {
      setFacebookUrl(store.facebookUrl ?? '');
      setInstagramUrl(store.instagramUrl ?? '');
      setTiktokUrl(store.tiktokUrl ?? '');
    }
  }, [store]);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['me', 'store'] });

  const saveMutation = useMutation({
    // Empty string clears the link server-side; undefined leaves it untouched — never send null.
    mutationFn: () => updateStoreProfile(store!.id, { facebookUrl, instagramUrl, tiktokUrl }),
    onSuccess: () => {
      invalidate();
      Alert.alert('Saved', 'Store profile updated.');
    },
    onError: (e) => Alert.alert('Could not save profile', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const pickAndUpload = async (kind: 'logo' | 'banner') => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow photo library access to upload an image.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled || !result.assets[0]) return;
    setUploading(kind);
    try {
      await (kind === 'logo' ? uploadStoreLogo : uploadStoreBanner)(store!.id, result.assets[0].uri);
      invalidate();
    } catch (e) {
      Alert.alert('Could not upload image', e instanceof ApiError ? e.message : 'Please try again.');
    } finally {
      setUploading(null);
    }
  };

  if (storeQuery.isLoading || !store) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ThemedText type="smallBold" themeColor="textSecondary">
          LOGO
        </ThemedText>
        <View style={styles.imageRow}>
          {store.logoUrl ? <Image source={{ uri: store.logoUrl }} style={styles.logoPreview} contentFit="cover" /> : null}
          <TouchableOpacity style={[styles.imageButton, { borderColor: theme.textSecondary }]} onPress={() => pickAndUpload('logo')} disabled={uploading === 'logo'}>
            {uploading === 'logo' ? <ActivityIndicator /> : <ThemedText themeColor="textSecondary">Change logo</ThemedText>}
          </TouchableOpacity>
        </View>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          BANNER
        </ThemedText>
        {store.bannerUrl ? <Image source={{ uri: store.bannerUrl }} style={styles.bannerPreview} contentFit="cover" /> : null}
        <TouchableOpacity style={[styles.imageButton, { borderColor: theme.textSecondary }]} onPress={() => pickAndUpload('banner')} disabled={uploading === 'banner'}>
          {uploading === 'banner' ? <ActivityIndicator /> : <ThemedText themeColor="textSecondary">Change banner</ThemedText>}
        </TouchableOpacity>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          TAGLINE &amp; DESCRIPTION
        </ThemedText>
        <ThemedText>{store.tagline}</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {store.description}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          Tagline and description can only be changed on the web dashboard.
        </ThemedText>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          SOCIAL LINKS
        </ThemedText>
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Facebook URL"
          placeholderTextColor={theme.textSecondary}
          value={facebookUrl}
          onChangeText={setFacebookUrl}
          autoCapitalize="none"
        />
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Instagram URL"
          placeholderTextColor={theme.textSecondary}
          value={instagramUrl}
          onChangeText={setInstagramUrl}
          autoCapitalize="none"
        />
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="TikTok URL"
          placeholderTextColor={theme.textSecondary}
          value={tiktokUrl}
          onChangeText={setTiktokUrl}
          autoCapitalize="none"
        />

        <TouchableOpacity style={[styles.submit, saveMutation.isPending && styles.submitDisabled]} onPress={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
          {saveMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.submitText}>Save profile</ThemedText>}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  sectionLabel: { marginTop: Spacing.three },
  imageRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  logoPreview: { width: 64, height: 64, borderRadius: 12 },
  bannerPreview: { width: '100%', height: 120, borderRadius: 12 },
  imageButton: { height: 44, paddingHorizontal: Spacing.three, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.three },
  submitDisabled: { opacity: 0.6 },
  submitText: { color: '#fff', fontWeight: '700' },
});
