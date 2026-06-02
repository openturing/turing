import { lazy } from "react";
import { Navigate, Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const WebhookListPage = lazy(() => import("../console/webhook/webhook.list.page"));
const WebhookPage = lazy(() => import("../console/webhook/webhook.page"));
const WebhookRootPage = lazy(() => import("../console/webhook/webhook.root.page"));

/**
 * T62 admin UI — list/create/edit outbound webhooks that POST chat
 * transcripts/slots to a CRM. Deployment-wide (not agent-scoped).
 *
 * @since 2026.3.1
 */
export const ChatWebhookRoutes = (
  <Route path={ROUTES.CHAT_WEBHOOK_ROOT} element={<WebhookRootPage />}>
    <Route index element={<Navigate to={ROUTES.CHAT_WEBHOOK_INSTANCE} replace />} />
    <Route path={ROUTES.CHAT_WEBHOOK_INSTANCE} element={<WebhookListPage />} />
    <Route path={`${ROUTES.CHAT_WEBHOOK_INSTANCE}/:id`} element={<WebhookPage />} />
  </Route>
);
