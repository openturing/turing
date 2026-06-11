/**
 * T279 / §XIV.6.2 — platform-admin tenant console: list every tenant, suspend /
 * reactivate, and impersonate (audited cross-tenant access). Requires
 * ROLE_PLATFORM_ADMIN on the backend; non-admins get 403 on the API calls.
 */
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  useAllTenants,
  useImpersonateTenant,
  useSetTenantStatus,
} from "@/api/queries/tenant.queries";

export default function PlatformTenantAdminPage() {
  const { data: tenants, isLoading, isError } = useAllTenants();
  const setStatus = useSetTenantStatus();
  const impersonate = useImpersonateTenant();

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <Card>
        <CardHeader>
          <CardTitle>Tenant administration</CardTitle>
          <CardDescription>
            Platform-wide tenant management. Suspending blocks a tenant's
            members; impersonating opens an audited cross-tenant session.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {isError && (
            <p className="text-sm text-destructive">
              You need the platform-admin role to view this page.
            </p>
          )}
          {tenants && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Slug</TableHead>
                  <TableHead>Plan</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.map((tenant) => {
                  const suspended = tenant.status === "SUSPENDED";
                  return (
                    <TableRow key={tenant.id}>
                      <TableCell className="font-medium">{tenant.name}</TableCell>
                      <TableCell>{tenant.slug}</TableCell>
                      <TableCell>{tenant.plan}</TableCell>
                      <TableCell>{tenant.status}</TableCell>
                      <TableCell className="space-x-2 text-right">
                        <Button
                          variant="secondary"
                          disabled={setStatus.isPending}
                          onClick={() =>
                            setStatus.mutate({ id: tenant.id, suspend: !suspended })
                          }
                        >
                          {suspended ? "Reactivate" : "Suspend"}
                        </Button>
                        <Button
                          variant="outline"
                          disabled={impersonate.isPending}
                          onClick={() => impersonate.mutate(tenant.id)}
                        >
                          Impersonate
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
