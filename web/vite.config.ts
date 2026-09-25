import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development the Spring Boot server runs on 8080; Vite forwards /api to it.
// In production the built files are served by the same Spring Boot server.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": "http://localhost:8080" },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
    // The plugin-timing notice goes to stderr, which Maven shows as [ERROR]; it is not an error.
    rolldownOptions: { checks: { pluginTimings: false } },
  },
});
