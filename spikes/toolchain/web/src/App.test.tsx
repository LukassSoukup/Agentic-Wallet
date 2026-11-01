import { render, screen, fireEvent } from "@testing-library/react";
import { App } from "./App";
import { describe as describeOutcome } from "./outcome";

describe("App", () => {
  it("renders the synthetic decision and reacts to the answer", () => {
    render(<App />);
    expect(screen.getByTestId("outcome")).toHaveTextContent("needs your answer");
    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByTestId("outcome")).toHaveTextContent("approved");
  });

  it("describes every outcome", () => {
    expect(describeOutcome("DECLINE")).toBe("declined");
  });
});
