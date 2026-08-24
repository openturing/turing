import { Navigate, useLocation } from "react-router-dom";
import { ROUTES } from "../routes.const";
import { consoleSuffixToBento } from "./console-suffix-to-bento";


export default function ConsoleToBentoRedirect() {
  const { pathname, search, hash } = useLocation();
  const base = ROUTES.CONSOLE;
  const rawSuffix = pathname.startsWith(base) ? pathname.slice(base.length) : pathname;
  const suffix = rawSuffix.replace(/^\/+/, "").replace(/\/+$/, "");
  return <Navigate to={`${consoleSuffixToBento(suffix)}${search}${hash}`} replace />;
}
