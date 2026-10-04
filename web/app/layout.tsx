import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import type { ReactElement, ReactNode } from "react";
import ThemeProvider from "../components/theme";
import { ModifierProvider } from "../utils/kbd-modifier";
import { PwaProvider } from "../utils/pwa";
import { TodoProvider } from "../utils/store";
import "./globals.css";

const geist = Geist({ subsets: ["latin"], variable: "--font-geist" });

export const metadata: Metadata = {
  title: "Latr",
  description: "Do it latr.",
};

// A meta tag because GitHub Pages can't send headers. 'unsafe-inline' scripts: the static export emits inline ones.
const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' https://apis.google.com",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: https://*.googleusercontent.com",
  "connect-src 'self' https://*.googleapis.com https://auth.latr.hafa.cc",
  "frame-src https://auth.latr.hafa.cc",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

export const viewport: Viewport = {
  viewportFit: "cover",
  interactiveWidget: "resizes-content",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#fbfaf8" },
    { media: "(prefers-color-scheme: dark)", color: "#171615" },
  ],
};

export default function RootLayout({
  children,
}: {
  children: ReactNode;
}): ReactElement {
  return (
    <html lang="en" className={geist.variable}>
      {process.env.NODE_ENV === "production" && (
        <head>
          <meta
            httpEquiv="Content-Security-Policy"
            content={CONTENT_SECURITY_POLICY}
          />
        </head>
      )}
      <body>
        <ThemeProvider>
          <TodoProvider>
            <ModifierProvider>
              <PwaProvider>{children}</PwaProvider>
            </ModifierProvider>
          </TodoProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
