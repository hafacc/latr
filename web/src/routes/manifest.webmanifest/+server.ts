import { asset, resolve } from "$app/paths";

export const prerender = true;

export function GET(): Response {
  return Response.json({
    name: "Latr",
    short_name: "Latr",
    description: "Do it latr.",
    id: resolve("/"),
    start_url: resolve("/"),
    scope: resolve("/"),
    display: "standalone",
    background_color: "#fbfaf8",
    theme_color: "#fbfaf8",
    icons: ([192, 512] as const).map((size) => ({
      src: asset(`icon-${size}.png`),
      sizes: `${size}x${size}`,
      type: "image/png",
    })),
  });
}
