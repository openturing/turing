import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  DialogueProject,
  DialogueProjectRequest,
  DialogueProjectSummary,
} from "@/models/persona/persona-dialogue.model";
import { TurPersonaDialogueProjectService } from "@/services/persona/persona-dialogue-project.service";
import { queryKeys } from "./keys";

const service = new TurPersonaDialogueProjectService();

export { service as dialogueProjectService };

export function useDialogueProjects() {
  return useQuery<DialogueProjectSummary[]>({
    queryKey: queryKeys.personaDialogue.list(),
    queryFn: () => service.list(),
  });
}

export function useDialogueProject(id: string | undefined) {
  return useQuery<DialogueProject>({
    queryKey: id ? queryKeys.personaDialogue.detail(id) : ["persona-dialogue", "pending"],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id) && id !== "new",
  });
}

function invalidateAll(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.personaDialogue.all() });
}

export function useCreateDialogueProject() {
  const queryClient = useQueryClient();
  return useMutation<DialogueProject, Error, DialogueProjectRequest>({
    mutationFn: (body) => service.create(body),
    onSuccess: () => invalidateAll(queryClient),
  });
}

export function useUpdateDialogueProject(id: string) {
  const queryClient = useQueryClient();
  return useMutation<DialogueProject, Error, DialogueProjectRequest>({
    mutationFn: (body) => service.update(id, body),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.personaDialogue.detail(id), saved);
      invalidateAll(queryClient);
    },
  });
}

export function useDeleteDialogueProject() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.remove(id),
    onSuccess: () => invalidateAll(queryClient),
  });
}

export function invalidateDialogueProject(
  queryClient: ReturnType<typeof useQueryClient>,
  id: string,
) {
  queryClient.invalidateQueries({ queryKey: queryKeys.personaDialogue.detail(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaDialogue.list() });
}
