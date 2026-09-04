import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // Share the backend port from the project's .env; do not expose server secrets to the client.
  const projectRoot = fileURLToPath(new URL('../', import.meta.url))
  const env = loadEnv(mode, projectRoot, 'SERVER_')
  const backendTarget = `http://localhost:${env.SERVER_PORT || '18080'}`

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true
        },
        '/oauth2': {
          target: backendTarget,
          changeOrigin: true
        }
      }
    }
  }
})
