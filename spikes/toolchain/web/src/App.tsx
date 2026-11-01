import { useState } from "react";
import { describe, type Outcome } from "./outcome";

export function App() {
  const [outcome, setOutcome] = useState<Outcome>("STEP_UP");
  return (
    <main>
      <h1>Agentic Wallet</h1>
      <p data-testid="outcome">Synthetic decision: {describe(outcome)}</p>
      <button
        type="button"
        onClick={() => {
          setOutcome("APPROVE");
        }}
      >
        Approve (synthetic)
      </button>
    </main>
  );
}
