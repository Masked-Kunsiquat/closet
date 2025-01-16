import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'; // Import the PWA plugin


// https://vite.dev/config/
export default defineConfig({
  plugins: [
      VitePWA({
          registerType: 'autoUpdate',
          workbox: {
              globPatterns: ['**/*.{html,js,css,webmanifest,png}'],
          },
          manifest: {
              name: 'Closet PWA',
              short_name: 'Closet',
              start_url: '/',
              display: 'standalone',
              background_color: '#ffffff',
              theme_color: '#000000',
              icons: [
                  {
                      src: 'icon-192x192.png',
                      sizes: '192x192',
                      type: 'image/png',
                  },
                  {
                      src: 'icon-512x512.png',
                      sizes: '512x512',
                      type: 'image/png',
                  },
              ],
          },
      }),
  ],
});