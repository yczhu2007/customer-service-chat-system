import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const backendTarget = process.env.VITE_BACKEND_URL || 'http://localhost:8080'
const backendWebSocketTarget = process.env.VITE_BACKEND_WS_URL || backendTarget.replace(/^http/, 'ws')

export default defineConfig(({ command, mode }) => ({
  plugins: [
    vue(),
    ...(mode === 'test' ? [] : [Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    })]),
  ],
  // Spring Boot serves production assets below /frontend; Vite serves development at the root.
  base: command === 'serve' || process.env.VERCEL === '1' || process.env.VERCEL_ENV ? '/' : '/frontend/',
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('element-plus')) {
            const component = id.match(/element-plus[/\\]es[/\\]components[/\\]([^/\\]+)/)
            return component ? `el-${component[1]}` : 'element-plus-core'
          }
          if (id.includes('/vue/') || id.includes('vue-router') || id.includes('pinia')) return 'vue-vendor'
          if (id.includes('@stomp/stompjs') || id.includes('sockjs-client')) return 'realtime-vendor'
          return 'vendor'
        },
      },
    },
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/chat": {
        target: backendTarget,
        changeOrigin: true,
        ws: true
      },
      "/auth": {
        target: backendTarget,
        changeOrigin: true
      },
      "/account": {
        target: backendTarget,
        changeOrigin: true
      },
      "/users": {
        target: backendTarget,
        changeOrigin: true
      },
      "/roles": {
        target: backendTarget,
        changeOrigin: true
      },
      "/permissions": {
        target: backendTarget,
        changeOrigin: true
      },
      "/ws/chat": {
        target: backendWebSocketTarget,
        changeOrigin: true,
        ws: true
      }
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    clearMocks: true,
    setupFiles: './src/__tests__/setup.js'
  }
}));
