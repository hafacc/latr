// Served by GitHub Pages at latr.hafa.cc, the domain root; set NEXT_PUBLIC_BASE_PATH to serve from a subpath.
const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

export default {
  output: "export",
  reactStrictMode: true,
  images: { unoptimized: true },
  basePath,
  assetPrefix: basePath || undefined,
  trailingSlash: true,
};
