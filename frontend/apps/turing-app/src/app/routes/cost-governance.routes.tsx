import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const CostGovernancePage = lazy(() => import("../console/cost-governance/cost-governance.page"))
const CostGovernanceRootPage = lazy(() => import("../console/cost-governance/cost-governance.root.page"))

export const CostGovernanceRoutes = (
    <Route path={ROUTES.COST_GOVERNANCE} element={<CostGovernanceRootPage />}>
        <Route index element={<CostGovernancePage />} />
    </Route>
)
