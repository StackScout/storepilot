import { AccountView } from "./account-view";

/** proxy.ts already redirects unauthenticated visitors before this ever renders; AccountView resolves its own identity client-side (GET /api/me — there's no server-decodable session anymore, only a JWT the backend validates). */
export default function AccountPage() {
  return <AccountView />;
}
