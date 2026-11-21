import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

// Same time zone as the Kotlin unit tests, so the ported rules are checked against the same cases.
process.env.TZ = "Europe/London";

export default defineConfig({
  test: {
    include: ["lib/**/*.test.ts"],
    environment: "node",
  },
  resolve: {
    alias: { "@": fileURLToPath(new URL(".", import.meta.url)) },
  },
});
