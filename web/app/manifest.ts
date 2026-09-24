import type { MetadataRoute } from "next";

export const dynamic = "force-static";

const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "latr",
    short_name: "latr",
    description: "Do it latr.",
    id: `${basePath}/`,
    start_url: `${basePath}/`,
    scope: `${basePath}/`,
    display: "standalone",
    background_color: "#fbfaf8",
    theme_color: "#fbfaf8",
    icons: [192, 512].map((size) => ({
      src: `${basePath}/icon-${size}.png`,
      sizes: `${size}x${size}`,
      type: "image/png",
    })),
  };
}
