/**
 * Minimal observable store — the vanilla replacement for React's `useState`.
 *
 * <p>Each controller in this package holds one {@link Store}. Consumers call
 * {@link Store#subscribe} to be notified on every state change and
 * {@link Store#getState} to read the current snapshot — the framework-agnostic
 * equivalent of a hook's return value re-rendering. The store keeps state
 * immutable (each {@link Store#setState} produces a new object), so a UI can
 * cheaply diff snapshots if it wants.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export type Listener<T> = (state: T) => void;

export interface Store<T> {
  /** Current immutable state snapshot. */
  getState(): T;
  /**
   * Merges {@code patch} into the state (or applies a functional updater) and
   * notifies subscribers. A no-op patch still notifies — callers that care
   * about reference stability should pass only changed keys.
   */
  setState(patch: Partial<T> | ((prev: T) => Partial<T>)): void;
  /**
   * Subscribes to state changes. Returns an unsubscribe function. The listener
   * is NOT called immediately on subscribe — read {@link getState} for the
   * initial value (mirrors how a hook renders once with the initial state).
   */
  subscribe(listener: Listener<T>): () => void;
}

export function createStore<T extends object>(initial: T): Store<T> {
  let state = initial;
  const listeners = new Set<Listener<T>>();

  return {
    getState: () => state,
    setState(patch) {
      const partial = typeof patch === "function" ? patch(state) : patch;
      state = { ...state, ...partial };
      // Snapshot the set so a re-entrant subscribe/unsubscribe during dispatch
      // doesn't mutate the iteration in flight.
      for (const listener of Array.from(listeners)) {
        try {
          listener(state);
        } catch {
          // A subscriber's error must not break notification of the rest.
        }
      }
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}
