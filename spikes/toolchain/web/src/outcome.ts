/** The three outcomes, as a closed union; an exhaustive switch keeps it honest. */
export type Outcome = "APPROVE" | "STEP_UP" | "DECLINE";

export function describe(outcome: Outcome): string {
  switch (outcome) {
    case "APPROVE":
      return "approved";
    case "STEP_UP":
      return "needs your answer";
    case "DECLINE":
      return "declined";
    default: {
      const never: never = outcome;
      throw new Error(`unknown outcome ${String(never)}`);
    }
  }
}
