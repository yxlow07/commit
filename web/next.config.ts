import type { NextConfig } from "next";

/**
 * Static single-page app: `next build` writes plain files to `out/` (hosted on Cloudflare Pages).
 * The browser talks to Supabase directly; row-level security protects the data, so there is no
 * server code. Cache Components / Partial Prefetching are server prerendering features and are off.
 */
const nextConfig: NextConfig = {
  output: "export",
  images: { unoptimized: true },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
