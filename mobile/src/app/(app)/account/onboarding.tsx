import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import * as WebBrowser from 'expo-web-browser';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Switch, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { Category } from '@storepilot/shared-api';

import { refreshSession } from '@/api/auth';
import { startBillingCheckout } from '@/api/billing';
import { listCategories } from '@/api/categories';
import { createStore } from '@/api/stores';
import {
  updateStoreSettings,
  uploadAbnDocument,
  uploadBusinessRegDocument,
  uploadDriverLicenceDocument,
  uploadNicDocument,
} from '@/api/store-settings';
import type { SellerType } from '@/api/types';
import { AbnVerificationBadge } from '@/components/abn-verification-badge';
import { SelectField } from '@/components/select-field';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { usePlatformConfig, useStates, formatCurrency } from '@/lib/platform-config';
import { useAuthStore } from '@/store/auth-store';

type OnboardingForm = {
  storeName: string;
  category: string;
  tagline: string;
  description: string;
  city: string;
  state: string;
  whatsappNumber: string;
  contactEmail: string;
  sellerType: SellerType;
  driverLicenceNumber: string;
  abn: string;
  nicNumber: string;
  businessRegistrationNumber: string;
  bankName: string;
  bankAccountName: string;
  bankAccountNumber: string;
};

const EMPTY_FORM: OnboardingForm = {
  storeName: '',
  category: '',
  tagline: '',
  description: '',
  city: '',
  state: '',
  whatsappNumber: '',
  contactEmail: '',
  sellerType: 'individual',
  driverLicenceNumber: '',
  abn: '',
  nicNumber: '',
  businessRegistrationNumber: '',
  bankName: '',
  bankAccountName: '',
  bankAccountNumber: '',
};

function Field({ label, value, onChangeText, placeholder, keyboardType, multiline }: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  placeholder?: string;
  keyboardType?: 'default' | 'email-address' | 'phone-pad';
  multiline?: boolean;
}) {
  const theme = useTheme();
  return (
    <View style={styles.field}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      <TextInput
        style={[
          styles.input,
          multiline && styles.inputMultiline,
          { color: theme.text, backgroundColor: theme.backgroundElement },
        ]}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={theme.textSecondary}
        keyboardType={keyboardType}
        multiline={multiline}
      />
    </View>
  );
}

function DocumentPicker({ label, uri, onPick }: { label: string; uri: string | null; onPick: (uri: string) => void }) {
  const theme = useTheme();
  const pick = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow photo library access to upload a document.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled || !result.assets[0]) return;
    onPick(result.assets[0].uri);
  };
  return (
    <TouchableOpacity style={[styles.docButton, { borderColor: theme.textSecondary }]} onPress={pick}>
      <ThemedText themeColor="textSecondary">
        {label} {uri ? '· selected' : '· not selected'}
      </ThemedText>
    </TouchableOpacity>
  );
}

export default function OnboardingScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const updateProfile = useAuthStore((s) => s.updateProfile);
  const platformConfig = usePlatformConfig();
  const statesQuery = useStates();
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories, staleTime: 5 * 60_000 });
  const isSriLanka = platformConfig.countryCode === 'LK';
  const needsBankDetails = platformConfig.defaultCodEnabled || platformConfig.defaultBankTransferEnabled;
  const currency = { code: platformConfig.currencyCode, symbol: platformConfig.currencySymbol, locale: platformConfig.currencyLocale };

  const [form, setForm] = useState<OnboardingForm>(EMPTY_FORM);
  const [sellerPlan, setSellerPlan] = useState<'free' | 'pro'>('free');
  const [agreeToTerms, setAgreeToTerms] = useState(false);
  const [licenceUri, setLicenceUri] = useState<string | null>(null);
  const [abnUri, setAbnUri] = useState<string | null>(null);
  const [nicUri, setNicUri] = useState<string | null>(null);
  const [businessRegUri, setBusinessRegUri] = useState<string | null>(null);

  const categoryOptions = (categoriesQuery.data ?? []).map((c: Category) => ({ label: c.name, value: c.wireValue }));
  const stateOptions = (statesQuery.data ?? []).map((s) => s.name);

  const canSubmit = useMemo(() => {
    if (form.storeName.trim().length < 3) return false;
    if (!form.category) return false;
    if (form.tagline.trim().length < 5) return false;
    if (form.description.trim().length < 20) return false;
    if (form.city.trim().length < 2) return false;
    if (!form.state) return false;
    if (form.whatsappNumber.trim().length < 9) return false;
    if (!/^\S+@\S+\.\S+$/.test(form.contactEmail.trim())) return false;
    if (isSriLanka) {
      if (form.nicNumber.trim().length < 5 || !nicUri) return false;
      if (form.sellerType === 'business' && (!form.businessRegistrationNumber.trim() || !businessRegUri)) return false;
    } else {
      if (form.driverLicenceNumber.trim().length < 6 || !licenceUri) return false;
      if (form.sellerType === 'business' && !form.abn.trim()) return false;
    }
    if (needsBankDetails) {
      if (form.bankName.trim().length < 2) return false;
      if (form.bankAccountName.trim().length < 2) return false;
      if (form.bankAccountNumber.trim().length < 4) return false;
    }
    return agreeToTerms;
  }, [form, isSriLanka, needsBankDetails, agreeToTerms, licenceUri, nicUri, businessRegUri]);

  const mutation = useMutation({
    mutationFn: async () => {
      const store = await createStore({
        name: form.storeName.trim(),
        category: form.category,
        tagline: form.tagline.trim(),
        description: form.description.trim(),
        city: form.city.trim(),
        state: form.state,
        whatsappNumber: form.whatsappNumber.trim(),
      });
      // createStore just granted the caller's account the "seller" Cognito
      // group, but the access token already in hand was issued before
      // that — see refreshSession's doc comment.
      const session = await refreshSession();
      await updateStoreSettings(store.id, {
        contactEmail: form.contactEmail.trim(),
        contactPhone: form.whatsappNumber.trim(),
        ...(needsBankDetails
          ? { bankName: form.bankName.trim(), bankAccountName: form.bankAccountName.trim(), bankAccountNumber: form.bankAccountNumber.trim() }
          : {}),
        sellerType: form.sellerType,
        ...(isSriLanka
          ? { nicNumber: form.nicNumber.trim(), businessRegistrationNumber: form.businessRegistrationNumber.trim() || undefined }
          : { driverLicenceNumber: form.driverLicenceNumber.trim(), abn: form.abn.trim() || undefined }),
      });
      if (isSriLanka) {
        await uploadNicDocument(store.id, nicUri!);
        if (businessRegUri) await uploadBusinessRegDocument(store.id, businessRegUri);
      } else {
        await uploadDriverLicenceDocument(store.id, licenceUri!);
        if (abnUri) await uploadAbnDocument(store.id, abnUri);
      }
      updateProfile({ role: session.role ?? 'seller', email: session.email ?? null, name: session.name ?? null });
      return store;
    },
    onSuccess: async () => {
      queryClient.clear();
      if (sellerPlan === 'pro') {
        try {
          const { checkoutUrl } = await startBillingCheckout();
          await WebBrowser.openBrowserAsync(checkoutUrl);
        } catch {
          Alert.alert('Store created', "Your store was created, but starting the Pro checkout failed — you can upgrade anytime from Settings.");
        }
      }
      // The root layout swaps the whole tree to the seller dashboard the
      // moment role becomes "seller" above — nothing else to navigate to.
    },
    onError: (e) => Alert.alert('Could not submit', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ThemedText type="title" style={styles.pageTitle}>
          Start selling on {platformConfig.name}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" style={styles.pageSubtitle}>
          We review every new store before it goes live to buyers — approval typically takes 1–3 business days.
        </ThemedText>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          STORE DETAILS
        </ThemedText>
        <Field label="Store name" value={form.storeName} onChangeText={(v) => setForm({ ...form, storeName: v })} placeholder="e.g. Blue Mountains Roasters" />
        <Field label="Short tagline" value={form.tagline} onChangeText={(v) => setForm({ ...form, tagline: v })} placeholder="One line describing what you sell" />
        <Field label="Store description" value={form.description} onChangeText={(v) => setForm({ ...form, description: v })} placeholder="Tell buyers more about your products" multiline />
        <ThemedText type="small" themeColor="textSecondary">
          Category
        </ThemedText>
        <SelectField placeholder="Select category" value={form.category} options={categoryOptions} onChange={(v) => setForm({ ...form, category: v })} />
        <ThemedText type="small" themeColor="textSecondary">
          State/Province
        </ThemedText>
        <SelectField placeholder="Select state/province" value={form.state} options={stateOptions} onChange={(v) => setForm({ ...form, state: v })} />
        <Field label="City / town" value={form.city} onChangeText={(v) => setForm({ ...form, city: v })} placeholder="e.g. Katoomba" />

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          SELLER VERIFICATION
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          We ask for this to confirm you&apos;re a real seller before your store goes live.
        </ThemedText>
        <View style={styles.segmentRow}>
          <TouchableOpacity
            style={[styles.segment, { borderColor: theme.textSecondary }, form.sellerType === 'individual' && styles.segmentActive]}
            onPress={() => setForm({ ...form, sellerType: 'individual' })}>
            <ThemedText style={form.sellerType === 'individual' ? styles.segmentActiveText : undefined}>Individual</ThemedText>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.segment, { borderColor: theme.textSecondary }, form.sellerType === 'business' && styles.segmentActive]}
            onPress={() => setForm({ ...form, sellerType: 'business' })}>
            <ThemedText style={form.sellerType === 'business' ? styles.segmentActiveText : undefined}>Registered business</ThemedText>
          </TouchableOpacity>
        </View>

        {isSriLanka ? (
          <Field label="NIC number" value={form.nicNumber} onChangeText={(v) => setForm({ ...form, nicNumber: v })} placeholder="e.g. 199512345678" />
        ) : (
          <Field label="Driver's licence number" value={form.driverLicenceNumber} onChangeText={(v) => setForm({ ...form, driverLicenceNumber: v })} placeholder="e.g. 12345678" />
        )}
        <DocumentPicker
          label={isSriLanka ? 'NIC copy' : "Driver's licence copy"}
          uri={isSriLanka ? nicUri : licenceUri}
          onPick={isSriLanka ? setNicUri : setLicenceUri}
        />

        {form.sellerType === 'business' ? (
          isSriLanka ? (
            <>
              <Field
                label="Business registration number"
                value={form.businessRegistrationNumber}
                onChangeText={(v) => setForm({ ...form, businessRegistrationNumber: v })}
                placeholder="e.g. PV 12345"
              />
              <DocumentPicker label="Business registration document" uri={businessRegUri} onPick={setBusinessRegUri} />
            </>
          ) : (
            <>
              <Field label="ABN" value={form.abn} onChangeText={(v) => setForm({ ...form, abn: v })} placeholder="e.g. 51 824 753 556" />
              <AbnVerificationBadge abn={form.abn} />
              <DocumentPicker label="ABN registration document" uri={abnUri} onPick={setAbnUri} />
            </>
          )
        ) : null}

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          {needsBankDetails ? 'CONTACT & PAYOUT DETAILS' : 'CONTACT'}
        </ThemedText>
        <Field label="WhatsApp number" value={form.whatsappNumber} onChangeText={(v) => setForm({ ...form, whatsappNumber: v })} placeholder="+61 4XX XXX XXX" keyboardType="phone-pad" />
        <Field label="Email" value={form.contactEmail} onChangeText={(v) => setForm({ ...form, contactEmail: v })} placeholder="you@example.com" keyboardType="email-address" />
        {needsBankDetails ? (
          <>
            <Field label="Bank name" value={form.bankName} onChangeText={(v) => setForm({ ...form, bankName: v })} placeholder="e.g. Commonwealth Bank of Australia" />
            <Field label="Account holder name" value={form.bankAccountName} onChangeText={(v) => setForm({ ...form, bankAccountName: v })} />
            <Field label="Account number" value={form.bankAccountNumber} onChangeText={(v) => setForm({ ...form, bankAccountNumber: v })} />
          </>
        ) : null}

        {platformConfig.proPlanEnabled ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              CHOOSE YOUR PLAN
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              You can switch plans anytime from your dashboard.
            </ThemedText>
            <TouchableOpacity
              style={[styles.planCard, { borderColor: theme.backgroundElement }, sellerPlan === 'free' && styles.planCardActive]}
              onPress={() => setSellerPlan('free')}>
              <ThemedText type="smallBold">Free</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Sell with online payment — a {platformConfig.platformFeePercent}% transaction fee applies only when you make a sale.
              </ThemedText>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.planCard, { borderColor: theme.backgroundElement }, sellerPlan === 'pro' && styles.planCardActive]}
              onPress={() => setSellerPlan('pro')}>
              <ThemedText type="smallBold">Pro · {formatCurrency(platformConfig.proMonthlyPriceCents, platformConfig)}/month</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Everything in Free, plus Cash on Delivery and Bank transfer as payment options.
              </ThemedText>
            </TouchableOpacity>
          </>
        ) : null}

        <View style={styles.agreeRow}>
          <Switch value={agreeToTerms} onValueChange={setAgreeToTerms} />
          <ThemedText type="small" themeColor="textSecondary" style={styles.agreeText}>
            I confirm the information above is accurate and agree to {platformConfig.name}&apos;s seller terms.
          </ThemedText>
        </View>

        <TouchableOpacity
          style={[styles.submit, (!canSubmit || mutation.isPending) && styles.submitDisabled]}
          onPress={() => mutation.mutate()}
          disabled={!canSubmit || mutation.isPending}>
          {mutation.isPending ? <ActivityIndicator color="#fff" /> : (
            <ThemedText style={styles.submitText}>{sellerPlan === 'pro' ? 'Submit application & continue to payment' : 'Submit application'}</ThemedText>
          )}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  pageTitle: { fontSize: 24, textAlign: 'center' },
  pageSubtitle: { textAlign: 'center', marginBottom: Spacing.two },
  sectionLabel: { marginTop: Spacing.three },
  field: { gap: 4 },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  inputMultiline: { height: 88, paddingTop: Spacing.two, textAlignVertical: 'top' },
  docButton: { height: 44, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  segmentRow: { flexDirection: 'row', gap: Spacing.two },
  segment: { flex: 1, height: 44, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  segmentActive: { backgroundColor: '#208AEF', borderColor: '#208AEF' },
  segmentActiveText: { color: '#fff', fontWeight: '600' },
  planCard: { borderRadius: 12, borderWidth: 1, padding: Spacing.three, gap: 4 },
  planCardActive: { borderColor: '#208AEF', borderWidth: 2 },
  agreeRow: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.two, marginTop: Spacing.three },
  agreeText: { flex: 1 },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.three },
  submitDisabled: { opacity: 0.5 },
  submitText: { color: '#fff', fontWeight: '700' },
});
