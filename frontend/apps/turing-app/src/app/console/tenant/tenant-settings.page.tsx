/**
 * T278 / §XIV.6.1 — tenant context UI: the org switcher (my tenants), a
 * tenant-settings summary, and a self-service signup/onboarding form.
 *
 * Switching a tenant clears the React Query cache so every view refetches under
 * the new tenant; signup persists the new tenant and routes the user into it.
 */
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { getCurrentTenant } from "@/lib/axios";
import {
  useMyTenants,
  useSwitchTenant,
  useTenantSignup,
} from "@/api/queries/tenant.queries";

export default function TenantSettingsPage() {
  const { data: tenants, isLoading } = useMyTenants();
  const switchTenant = useSwitchTenant();
  const signup = useTenantSignup();
  const current = getCurrentTenant();

  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <Card>
        <CardHeader>
          <CardTitle>Your organizations</CardTitle>
          <CardDescription>
            Switch the active tenant. The current one is sent on every request.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {tenants?.length === 0 && (
            <p className="text-sm text-muted-foreground">
              You don't belong to any organization yet — create one below.
            </p>
          )}
          {tenants?.map((tenant) => (
            <div
              key={tenant.id}
              className="flex items-center justify-between rounded-md border p-3"
            >
              <div>
                <div className="font-medium">{tenant.name}</div>
                <div className="text-xs text-muted-foreground">
                  {tenant.slug} · {tenant.plan} · {tenant.status}
                </div>
              </div>
              <Button
                variant={tenant.slug === current ? "secondary" : "default"}
                disabled={tenant.slug === current || switchTenant.isPending}
                onClick={() => switchTenant.mutate(tenant.slug)}
              >
                {tenant.slug === current ? "Active" : "Switch"}
              </Button>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Create a new organization</CardTitle>
          <CardDescription>
            Pick a unique slug (your subdomain). You become its owner.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Input
            placeholder="slug (e.g. acme)"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
          />
          <Input
            placeholder="Display name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <Button
            disabled={!slug || signup.isPending}
            onClick={() => signup.mutate({ slug, name })}
          >
            {signup.isPending ? "Creating…" : "Create organization"}
          </Button>
          {signup.isError && (
            <p className="text-sm text-destructive">
              Could not create the organization. The slug may be taken or invalid.
            </p>
          )}
          {signup.isSuccess && (
            <p className="text-sm text-emerald-600">
              Created — switched to “{signup.data?.name}”.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
