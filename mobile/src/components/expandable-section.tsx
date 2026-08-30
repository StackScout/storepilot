import { StyleSheet, TouchableOpacity, View, type ViewProps } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function ExpandableSection({
  title,
  expanded,
  onToggle,
  children,
  ...viewProps
}: {
  title: string;
  expanded: boolean;
  onToggle: () => void;
  children: React.ReactNode;
} & ViewProps) {
  const theme = useTheme();

  return (
    <View style={[styles.container, { borderColor: theme.backgroundElement }]} {...viewProps}>
      <TouchableOpacity style={styles.header} onPress={onToggle}>
        <ThemedText type="smallBold" style={styles.title}>
          {title}
        </ThemedText>
        <ThemedText themeColor="textSecondary" style={[styles.chevron, expanded && styles.chevronExpanded]}>
          ⌄
        </ThemedText>
      </TouchableOpacity>
      {expanded ? <View style={styles.content}>{children}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { borderTopWidth: 1 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: Spacing.three },
  title: { fontSize: 16 },
  chevron: { fontSize: 18 },
  chevronExpanded: { transform: [{ rotate: '180deg' }] },
  content: { paddingBottom: Spacing.three },
});
