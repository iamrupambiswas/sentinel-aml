import { LoginButton } from "./LoginButton";

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ callbackUrl?: string; error?: string }> }) {
  const { callbackUrl, error } = await searchParams;
  // Only same-site paths, never an absolute URL from the query string
  const target = callbackUrl?.startsWith("/") && !callbackUrl.startsWith("//") ? callbackUrl : "/";

  return (
    <div className="mx-auto mt-20 max-w-sm">
      <div className="card p-6">
        <div className="mb-1 flex items-center gap-2 text-lg font-semibold">
          <span aria-hidden className="inline-block h-5 w-1.5 rounded-sm bg-accent" />
          Sentinel AML
        </div>
        <p className="mb-5 text-sm text-muted">Analyst console. Sign in with your Keycloak account.</p>
        {error && (
          <p role="alert" className="mb-4 rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
            Sign-in failed ({error}). Try again.
          </p>
        )}
        <LoginButton callbackUrl={target} />
        <p className="mt-4 text-xs text-muted">Local test users: analyst1, senior1, admin1 (see infra/keycloak/sentinel-realm.json).</p>
      </div>
    </div>
  );
}
