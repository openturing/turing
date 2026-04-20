import { Navigate, Route, useParams } from "react-router-dom"
import IntentListPage from "../console/intent/intent.list.page"
import IntentPage from "../console/intent/intent.page"
import IntentRootPage from "../console/intent/intent.root.page"
import IntentSettingsPage from "../console/intent/settings/intent.settings.page"
import { ROUTES } from "../routes.const"

function RedirectToIntentSettings() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.INTENT_INSTANCE}/${id}/settings`} replace />;
}

export const IntentRoutes = (
    <Route path={ROUTES.INTENT_ROOT} element={<IntentRootPage />}>
        <Route index element={<Navigate to={ROUTES.INTENT_INSTANCE} replace />} />
        <Route path={ROUTES.INTENT_INSTANCE} element={<IntentListPage />} />
        <Route path={`${ROUTES.INTENT_INSTANCE}/:id`} element={<IntentPage />}>
            <Route index element={<RedirectToIntentSettings />} />
            <Route path="settings" element={<IntentSettingsPage />} />
        </Route>
    </Route>
)
