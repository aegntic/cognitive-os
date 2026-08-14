import { cloudflareTest } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    fileParallelism: false,
  },
  plugins: [
    // Avoid D1 isolated-storage .sqlite-shm stack flakes under suite teardown
    cloudflareTest({
      isolatedStorage: false,
      singleWorker: true,
      wrangler: { configPath: "./wrangler.toml" },
      miniflare: {
        d1Databases: { DB: "insidher" },
        d1Persist: false,
      },
    }),
  ],
});
