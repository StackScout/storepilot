import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';
import { useState } from 'react';
import { Alert, ScrollView, StyleSheet, Switch, TextInput, TouchableOpacity, View } from 'react-native';

import type { ProductFormInput } from '@/api/products';
import type { ProductResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// Matches StoreCategory's wire values exactly (backend/.../store/StoreCategory.kt) — a product's category must be one its store's own category, enforced server-side by requireCategoryMatchesStore.
const CATEGORIES = ['fashion', 'food-beverage', 'beauty', 'handicrafts', 'electronics', 'home-living', 'jewelry', 'grocery'] as const;

export type ProductFormValue = ProductFormInput & { newImageUris: string[] };

export function ProductForm({
  initial,
  existingImages,
  submitLabel,
  submitting,
  onSubmit,
}: {
  initial: ProductFormInput;
  existingImages?: ProductResponse['images'];
  submitLabel: string;
  submitting: boolean;
  onSubmit: (value: ProductFormValue) => void;
}) {
  const theme = useTheme();
  const [name, setName] = useState(initial.name);
  const [description, setDescription] = useState(initial.description);
  const [category, setCategory] = useState(initial.category);
  const [price, setPrice] = useState(String(initial.price / 100));
  const [stockQuantity, setStockQuantity] = useState(String(initial.stockQuantity));
  const [trackStock, setTrackStock] = useState(initial.trackStock);
  const [isActive, setIsActive] = useState(initial.status !== 'draft');
  const [newImageUris, setNewImageUris] = useState<string[]>([]);

  const pickImages = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow photo library access to add product images.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsMultipleSelection: true,
      selectionLimit: 5,
      quality: 0.8,
    });
    if (!result.canceled) {
      setNewImageUris(result.assets.map((a) => a.uri));
    }
  };

  const handleSubmit = () => {
    const priceCents = Math.round(parseFloat(price || '0') * 100);
    const stock = parseInt(stockQuantity || '0', 10);
    if (!name.trim() || !description.trim() || !priceCents || Number.isNaN(stock)) {
      Alert.alert('Missing details', 'Fill in name, description, and price at least.');
      return;
    }
    onSubmit({
      name: name.trim(),
      description: description.trim(),
      category,
      price: priceCents,
      compareAtPrice: undefined,
      stockQuantity: stock,
      trackStock,
      sku: initial.sku,
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
        placeholder="Product name"
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

      <View style={styles.categoryRow}>
        {CATEGORIES.map((c) => (
          <TouchableOpacity
            key={c}
            style={[styles.chip, { backgroundColor: c === category ? '#208AEF' : theme.backgroundElement }]}
            onPress={() => setCategory(c)}>
            <ThemedText style={c === category ? styles.chipTextActive : undefined} themeColor={c === category ? undefined : 'textSecondary'} type="small">
              {c}
            </ThemedText>
          </TouchableOpacity>
        ))}
      </View>

      <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
        PRICE &amp; STOCK
      </ThemedText>
      <TextInput
        style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        placeholder="Price"
        placeholderTextColor={theme.textSecondary}
        keyboardType="decimal-pad"
        value={price}
        onChangeText={setPrice}
      />
      <View style={styles.switchRow}>
        <ThemedText>Track stock</ThemedText>
        <Switch value={trackStock} onValueChange={setTrackStock} />
      </View>
      {trackStock ? (
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Stock quantity"
          placeholderTextColor={theme.textSecondary}
          keyboardType="number-pad"
          value={stockQuantity}
          onChangeText={setStockQuantity}
        />
      ) : null}
      <View style={styles.switchRow}>
        <ThemedText>Active (visible to buyers)</ThemedText>
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
  categoryRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.one },
  chip: { paddingHorizontal: Spacing.two, paddingVertical: Spacing.one, borderRadius: 999 },
  chipTextActive: { color: '#fff' },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: Spacing.one },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.three },
  submitDisabled: { opacity: 0.6 },
  submitText: { color: '#fff', fontWeight: '700' },
});
