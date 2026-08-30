import { useState } from 'react';
import { FlatList, Modal, Pressable, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type SelectOption = { label: string; value: string };

/** A simple bottom-sheet list picker — no native dependency needed for a single-select dropdown like this. Used for state/province, category, etc. selection. */
export function SelectField({
  placeholder,
  value,
  options,
  onChange,
}: {
  placeholder: string;
  value: string;
  /** Plain strings are shorthand for `{ label: s, value: s }` (e.g. state/province, where the display name is the value). */
  options: string[] | SelectOption[];
  onChange: (value: string) => void;
}) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);

  const normalized: SelectOption[] = options.map((o) => (typeof o === 'string' ? { label: o, value: o } : o));
  const selectedLabel = normalized.find((o) => o.value === value)?.label;

  return (
    <>
      <TouchableOpacity style={[styles.field, { backgroundColor: theme.backgroundElement }]} onPress={() => setOpen(true)}>
        <ThemedText style={!value ? { color: theme.textSecondary } : undefined}>{selectedLabel || placeholder}</ThemedText>
      </TouchableOpacity>
      <Modal visible={open} transparent animationType="slide" onRequestClose={() => setOpen(false)}>
        <Pressable style={styles.backdrop} onPress={() => setOpen(false)}>
          <Pressable style={[styles.sheet, { backgroundColor: theme.background }]} onPress={(e) => e.stopPropagation()}>
            <SafeAreaView edges={['bottom']} style={styles.sheetInner}>
              <View style={styles.handle} />
              <View style={[styles.header, { borderColor: theme.backgroundElement }]}>
                <ThemedText type="smallBold">{placeholder}</ThemedText>
                <TouchableOpacity onPress={() => setOpen(false)}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Close
                  </ThemedText>
                </TouchableOpacity>
              </View>
              <FlatList
                style={styles.list}
                data={normalized}
                keyExtractor={(item) => item.value}
                renderItem={({ item }) => (
                  <TouchableOpacity
                    style={[styles.option, { borderColor: theme.backgroundElement }]}
                    onPress={() => {
                      onChange(item.value);
                      setOpen(false);
                    }}>
                    <ThemedText style={item.value === value ? { fontWeight: '700' } : undefined}>{item.label}</ThemedText>
                  </TouchableOpacity>
                )}
              />
            </SafeAreaView>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  field: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, justifyContent: 'center' },
  backdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.4)' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, maxHeight: '70%' },
  sheetInner: { flexShrink: 1 },
  handle: { alignSelf: 'center', width: 36, height: 4, borderRadius: 2, backgroundColor: '#8888', marginTop: Spacing.two },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: Spacing.three, borderBottomWidth: 1 },
  list: { flexGrow: 0, flexShrink: 1 },
  option: { padding: Spacing.three, borderBottomWidth: 1 },
});
