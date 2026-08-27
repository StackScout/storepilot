import { useQuery } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';
import { useState } from 'react';
import { Alert, ScrollView, StyleSheet, Switch, TextInput, TouchableOpacity, View } from 'react-native';

import { listCategories } from '@/api/categories';
import type { BookableServiceFormInput } from '@/api/bookable-services';
import type { BookableServiceResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ServiceFormValue = BookableServiceFormInput & { newImageUris: string[] };

export function ServiceForm({
  initial,
  existingImages,
  submitLabel,
  submitting,
  onSubmit,
}: {
  initial: BookableServiceFormInput;
  existingImages?: BookableServiceResponse['images'];
  submitLabel: string;
  submitting: boolean;
  onSubmit: (value: ServiceFormValue) => void;
}) {
  const theme = useTheme();
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories, staleTime: 5 * 60_000 });
  // A service's category is locked to its store's own approved category —
  // same rule as ProductForm — so this is a read-only label, not a picker.
  const categoryLabel = categoriesQuery.data?.find((c) => c.wireValue === initial.category)?.name ?? initial.category;
  const [name, setName] = useState(initial.name);
  const [description, setDescription] = useState(initial.description);
  const category = initial.category;
  const [price, setPrice] = useState(String(initial.price / 100));
  const [durationMinutes, setDurationMinutes] = useState(String(initial.durationMinutes));
  const [bufferMinutes, setBufferMinutes] = useState(String(initial.bufferMinutes));
  const [isActive, setIsActive] = useState(initial.status === 'active');
  const [newImageUris, setNewImageUris] = useState<string[]>([]);

  const pickImages = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow photo library access to add photos.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsMultipleSelection: true, selectionLimit: 5, quality: 0.8 });
    if (!result.canceled) setNewImageUris(result.assets.map((a) => a.uri));
  };

  const handleSubmit = () => {
    const priceCents = Math.round(parseFloat(price || '0') * 100);
    const duration = parseInt(durationMinutes || '0', 10);
    const buffer = parseInt(bufferMinutes || '0', 10) || 0;
    if (!name.trim() || !description.trim() || !priceCents || !duration) {
      Alert.alert('Missing details', 'Fill in name, description, price, and duration at least.');
      return;
    }
    onSubmit({
      name: name.trim(),
      description: description.trim(),
      category,
      price: priceCents,
      durationMinutes: duration,
      bufferMinutes: buffer,
      status: isActive ? 'active' : 'draft',
      newImageUris,
    });
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <ThemedText type="smallBold" themeColor="textSecondary">
        PHOTOS
      </ThemedText>
      <View style={styles.imagesRow}>
        {newImageUris.length > 0
          ? newImageUris.map((uri) => <Image key={uri} source={{ uri }} style={styles.thumb} contentFit="cover" />)
          : existingImages?.map((img) => <Image key={img.id} source={{ uri: img.url }} style={styles.thumb} contentFit="cover" />)}
        <TouchableOpacity style={[styles.addImage, { borderColor: theme.textSecondary }]} onPress={pickImages}>
          <ThemedText themeColor="textSecondary">+ Add</ThemedText>
        </TouchableOpacity>
      </View>
      {existingImages && existingImages.length > 0 ? (
        <ThemedText type="small" themeColor="textSecondary">
          Adding new photos replaces all existing ones.
        </ThemedText>
      ) : null}

      <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
        DETAILS
      </ThemedText>
      <TextInput
        style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        placeholder="Service name"
        placeholderTextColor={theme.textSecondary}
        value={name}
        onChangeText={setName}
      />
      <TextInput
        style={[styles.input, styles.multiline, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        placeholder="Description"
        placeholderTextColor={theme.textSecondary}
        multiline
        value={description}
        onChangeText={setDescription}
      />
      <View style={[styles.categoryLocked, { backgroundColor: theme.backgroundElement }]}>
        <ThemedText type="small" themeColor="textSecondary">
          Category
        </ThemedText>
        <ThemedText type="small">{categoryLabel}</ThemedText>
      </View>

      <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
        PRICE &amp; DURATION
      </ThemedText>
      <TextInput
        style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        placeholder="Price"
        placeholderTextColor={theme.textSecondary}
        keyboardType="decimal-pad"
        value={price}
        onChangeText={setPrice}
      />
      <TextInput
        style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        placeholder="Duration (minutes)"
        placeholderTextColor={theme.textSecondary}
        keyboardType="number-pad"
        value={durationMinutes}
        onChangeText={setDurationMinutes}
      />
      <TextInput
        style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        placeholder="Buffer between bookings (minutes, optional)"
        placeholderTextColor={theme.textSecondary}
        keyboardType="number-pad"
        value={bufferMinutes}
        onChangeText={setBufferMinutes}
      />
      <View style={styles.switchRow}>
        <ThemedText>Active (bookable by buyers)</ThemedText>
        <Switch value={isActive} onValueChange={setIsActive} />
      </View>

      <TouchableOpacity style={[styles.submit, submitting && styles.submitDisabled]} onPress={handleSubmit} disabled={submitting}>
        <ThemedText style={styles.submitText}>{submitLabel}</ThemedText>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  sectionLabel: { marginTop: Spacing.three },
  imagesRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  thumb: { width: 72, height: 72, borderRadius: 10 },
  addImage: { width: 72, height: 72, borderRadius: 10, borderWidth: 1, borderStyle: 'dashed', alignItems: 'center', justifyContent: 'center' },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  multiline: { height: 90, paddingTop: Spacing.two, textAlignVertical: 'top' },
  categoryLocked: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: Spacing.three, paddingVertical: Spacing.two, borderRadius: 10 },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: Spacing.one },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.three },
  submitDisabled: { opacity: 0.6 },
  submitText: { color: '#fff', fontWeight: '700' },
});
