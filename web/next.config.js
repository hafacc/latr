// Served by GitHub Pages for github.com/hafacc/latr at hafa.cc/latr; the deploy workflow sets the base path.
const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

export default {
  output: "export",
  reactStrictMode: true,
  images: { unoptimized: true },
  basePath,
  assetPrefix: basePath || undefined,
  trailingSlash: true,
};
