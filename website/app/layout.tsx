import type { Metadata } from "next";
import { Inter } from "next/font/google";
import localFont from "next/font/local";
import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
});

const ndot = localFont({
  src: "../public/fonts/Ndot57-Regular.otf",
  variable: "--font-ndot",
  weight: "400",
});

export const metadata: Metadata = {
  title: "Glyph Odyssey",
  description:
    "Turn any gallery video into a Glyph Matrix animation — on-device, offline, in seconds. For Nothing Phone (3) and (4a) Pro.",
  openGraph: {
    title: "Glyph Odyssey",
    description:
      "Turn any gallery video into a Glyph Matrix animation — on-device, offline, in seconds.",
    type: "website",
    // TODO: add og:image once LED matrix footage is captured
    // images: [{ url: "/og/og-image.png", width: 1200, height: 630 }],
  },
  twitter: {
    card: "summary_large_image",
    title: "Glyph Odyssey",
    description:
      "Turn any gallery video into a Glyph Matrix animation — on-device, offline, in seconds.",
    // TODO: images: ["/og/og-image.png"],
  },
};

import AnimatedBackground from "./components/AnimatedBackground";
import { Providers } from "./providers";
import { Analytics } from "@vercel/analytics/react";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} ${ndot.variable}`}>
      <body>
        <Providers>
          <AnimatedBackground />
          <div style={{ position: "relative", zIndex: 1 }}>{children}</div>
        </Providers>
        <Analytics />
      </body>
    </html>
  );
}
