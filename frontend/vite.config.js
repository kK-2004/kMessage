import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  base: "/admin/",
  plugins: [vue(), tailwindcss()],
  server: {
    proxy: {
      "/api": "http://localhost:8002",
    },
  },
  build: {
    outDir: "../src/main/resources/static/admin",
    emptyOutDir: true,
    cssCodeSplit: false,
  },
});
