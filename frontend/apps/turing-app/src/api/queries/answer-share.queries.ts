import { useQuery } from "@tanstack/react-query";
import { fetchAnswerShare, type TurAnswerShareView } from "@viglet/turing-react-sdk";
import { queryKeys } from "./keys";

/**
 * T1018 — the answer behind a share link.
 *
 * `fetchAnswerShare` swallows its own errors and resolves `null`, so a missing
 * or expired id lands in the not-found branch rather than an unhandled
 * rejection; the query keeps that contract and never rejects either.
 */
export function useAnswerShare(site: string, shareId: string | undefined) {
  return useQuery<TurAnswerShareView | null>({
    queryKey: shareId
      ? queryKeys.answerShares.detail(site, shareId)
      : ["answer-shares", "pending"],
    queryFn: () => fetchAnswerShare(site, shareId as string),
    enabled: Boolean(site) && Boolean(shareId),
  });
}
