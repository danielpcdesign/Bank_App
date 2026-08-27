import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // The dev server runs on 5173, the API on 8080. Two ports means two origins, and a
    // browser will not let a page served from one call the other without CORS headers.
    //
    // This proxy sidesteps that in development: fetch("/api/v1/customers") goes to the
    // Vite server on the *same* origin, and Vite forwards it to Spring server-side, where
    // the same-origin policy does not apply. The browser never sees a cross-origin request.
    //
    // The point worth remembering: this is a development convenience, not the deployed
    // topology. In production the two are served separately and the request IS cross-origin,
    // so Spring must send Access-Control-Allow-Origin for real. Configured in Phase 9.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
