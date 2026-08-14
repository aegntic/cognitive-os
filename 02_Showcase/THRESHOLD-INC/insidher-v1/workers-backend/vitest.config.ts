import { defineWorkersConfig } from "@cloudflare/vitest-pool-workers/config";

export default defineWorkersConfig({
  test: {
    fileParallelism: false,
    poolOptions: {
      workers: {
        // Avoid D1 isolated-storage .sqlite-shm stack flakes under suite teardown
        isolatedStorage: false,
        singleWorker: true,
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          d1Databases: { DB: "insidher" },
          d1Persist: false,
        },
      },
    },
  },
});
