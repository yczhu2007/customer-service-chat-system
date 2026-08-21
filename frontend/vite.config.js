import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  base: '/',
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/chat": {
        target: "http://localhost:8080",
        changeOrigin: true,
        ws: true
      },
      "/auth": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/account": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/users": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/roles": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/permissions": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/ws/chat": {
        target: "ws://localhost:8080",
        changeOrigin: true,
        ws: true
      }
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    clearMocks: true
  }
});
