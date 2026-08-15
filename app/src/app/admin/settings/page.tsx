import { MfaSettingsCard } from "@/components/shared/mfa-settings-card";

export default function AdminSettingsPage() {
  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Your security</h1>
        <p className="text-muted-foreground text-sm">
          Settings for your own admin account — see the Admins page to manage the team.
        </p>
      </div>
      <MfaSettingsCard />
    </div>
  );
}
