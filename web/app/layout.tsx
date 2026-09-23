import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import type { ReactElement, ReactNode } from "react";
import ThemeProvider from "../components/theme";
import { ModifierProvider } from "../utils/kbd-modifier";
import { TodoProvider } from "../utils/store";
import "./globals.css";

const geist = Geist({ subsets: ["latin"], variable: "--font-geist" });

export const metadata: Metadata = {
  title: "latr",
  description: "Do it latr.",
};

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
      <body>
        <ThemeProvider>
          <TodoProvider>
            <ModifierProvider>{children}</ModifierProvider>
          </TodoProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
