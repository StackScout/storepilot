import { useState } from 'react';
import { FlatList, Modal, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** A simple modal list picker — no native dependency needed for a single-select dropdown like this. Used for state/province selection. */
export function SelectField({
  placeholder,
  value,
  options,
  onChange,
}: {
  placeholder: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);

  return (
    <>
      <TouchableOpacity style={[styles.field, { backgroundColor: theme.backgroundElement }]} onPress={() => setOpen(true)}>
        <ThemedText style={!value ? { color: theme.textSecondary } : undefined}>{value || placeholder}</ThemedText>
      </TouchableOpacity>
      <Modal visible={open} animationType="slide" onRequestClose={() => setOpen(false)}>
        <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
          <View style={[styles.header, { borderColor: theme.backgroundElement }]}>
            <ThemedText type="smallBold">{placeholder}</ThemedText>
            <TouchableOpacity onPress={() => setOpen(false)}>
              <ThemedText type="small" themeColor="textSecondary">
                Close
              </ThemedText>
            </TouchableOpacity>
          </View>
          <FlatList
            data={options}
            keyExtractor={(item) => item}
            renderItem={({ item }) => (
              <TouchableOpacity
                style={[styles.option, { borderColor: theme.backgroundElement }]}
                onPress={() => {
                  onChange(item);
                  setOpen(false);
                }}>
                <ThemedText style={item === value ? { fontWeight: '700' } : undefined}>{item}</ThemedText>
              </TouchableOpacity>
            )}
          />
        </SafeAreaView>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  field: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, justifyContent: 'center' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: Spacing.three, borderBottomWidth: 1 },
  option: { padding: Spacing.three, borderBottomWidth: 1 },
});
