import type { Metadata, Viewport } from "next";
import { Public_Sans } from "next/font/google";
import { Providers } from "./providers";
import { getPlatformConfig } from "@/lib/platform-config";
import "./globals.css";

// Every page fetches live data from the backend (platform config, products,
// session state) — nothing here can be statically prerendered at build
// time anyway, and the production Docker build runs before the backend
// container exists, which would otherwise fail the build outright trying
// to fetch during static generation.
export const dynamic = "force-dynamic";

const publicSans = Public_Sans({
  variable: "--font-public-sans",
  subsets: ["latin"],
});

export async function generateMetadata(): Promise<Metadata> {
  const config = await getPlatformConfig();
  return {
    title: {
      default: `${config.name} — ${config.tagline}`,
      template: `%s | ${config.name}`,
    },
    description: config.tagline,
  };
}

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#FF9900",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const config = await getPlatformConfig();
  return (
    <html lang="en" className={`${publicSans.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col">
        <Providers config={config}>{children}</Providers>
      </body>
    </html>
  );
}
