import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// RTL teardown between tests — react-ui components portal into document.body
// (fullscreen), so an explicit unmount keeps the DOM clean across cases.
afterEach(() => {
  cleanup();
});
