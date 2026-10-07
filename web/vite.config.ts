import adapter from "@sveltejs/adapter-static";
import { sveltekit } from "@sveltejs/kit/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  // Firebase alone is past Vite's default 500 kB warning.
  build: { chunkSizeWarningLimit: 800 },
  plugins: [
    tailwindcss(),
    sveltekit({
      adapter: adapter({ pages: "out", fallback: "404.html" }),
      // Served by GitHub Pages at latr.hafa.cc, the domain root; set BASE_PATH to serve from a subpath.
      // Absolute asset paths, since the service worker serves the app page at paths it has no page for.
      paths: {
        base: (process.env.BASE_PATH ?? "") as "" | `/${string}`,
        relative: false,
      },
      // Prerendered pages get this as a meta tag (GitHub Pages can't send headers), with a hash for the inline start script.
      csp: {
        mode: "hash",
        directives: {
          "default-src": ["self"],
          "script-src": ["self", "https://apis.google.com"],
          "style-src": ["self", "unsafe-inline"],
          "img-src": ["self", "data:", "https://*.googleusercontent.com"],
          "connect-src": [
            "self",
            "https://*.googleapis.com",
            "https://auth.latr.hafa.cc",
          ],
          "frame-src": ["https://auth.latr.hafa.cc"],
          "object-src": ["none"],
          "base-uri": ["self"],
          "form-action": ["self"],
        },
      },
      version: { pollInterval: 0 },
    }),
  ],
});
