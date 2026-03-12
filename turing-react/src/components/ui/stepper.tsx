import { IconCheck, IconCircleCheck } from "@tabler/icons-react";
import {
  createContext,
  useContext,
  type ReactNode,
} from "react";
import { cn } from "@/lib/utils";

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

interface StepperContextValue {
  completedSteps: boolean[];
  allDone: boolean;
}

const StepperContext = createContext<StepperContextValue>({
  completedSteps: [],
  allDone: false,
});

// ---------------------------------------------------------------------------
// Stepper (root)
// ---------------------------------------------------------------------------

interface StepperProps {
  completedSteps: boolean[];
  children: ReactNode;
  className?: string;
}

function Stepper({ completedSteps, children, className }: Readonly<StepperProps>) {
  const allDone = completedSteps.length > 0 && completedSteps.every(Boolean);

  return (
    <StepperContext.Provider value={{ completedSteps, allDone }}>
      <div className={cn("relative", className)}>
        {/* Animated vertical line */}
        <div className="stepper-line absolute left-3.75 top-4 bottom-4 w-0.5 rounded-full" />
        {children}
      </div>
    </StepperContext.Provider>
  );
}

// ---------------------------------------------------------------------------
// Step
// ---------------------------------------------------------------------------

interface StepProps {
  index: number;
  children: ReactNode;
  className?: string;
}

function Step({ index, children, className }: Readonly<StepProps>) {
  const { completedSteps } = useContext(StepperContext);
  const completed = completedSteps[index] ?? false;
  const step = index + 1;

  return (
    <div className={cn("relative flex items-start gap-4 pb-8", className)}>
      <div className="flex flex-col items-center">
        <div
          className={cn(
            "relative z-10 flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold transition-all duration-500",
            completed
              ? "bg-emerald-500 text-white shadow-md shadow-emerald-500/30 scale-105"
              : "bg-primary text-primary-foreground",
          )}
        >
          {completed ? <IconCheck className="h-4 w-4" /> : step}
        </div>
      </div>
      <div className="flex-1 pt-0.5">{children}</div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Completion
// ---------------------------------------------------------------------------

interface CompletionProps {
  readyLabel?: string;
  pendingLabel?: string;
  className?: string;
}

function Completion({
  readyLabel = "Ready to submit",
  pendingLabel = "Complete all steps above",
  className,
}: Readonly<CompletionProps>) {
  const { allDone } = useContext(StepperContext);

  return (
    <div className={cn("relative flex items-center gap-4", className)}>
      <div className="flex flex-col items-center">
        <div
          className={cn(
            "relative z-10 flex h-8 w-8 items-center justify-center rounded-full transition-all duration-700",
            allDone
              ? "stepper-ready bg-emerald-500 text-white shadow-lg shadow-emerald-500/40 scale-110"
              : "bg-muted text-muted-foreground/40",
          )}
        >
          <IconCircleCheck className="h-5 w-5" />
        </div>
      </div>
      <span
        className={cn(
          "text-sm font-medium transition-all duration-500",
          allDone
            ? "text-emerald-600 dark:text-emerald-400"
            : "text-muted-foreground/40",
        )}
      >
        {allDone ? readyLabel : pendingLabel}
      </span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Composite export
// ---------------------------------------------------------------------------

Stepper.Step = Step;
Stepper.Completion = Completion;

export { Stepper };
