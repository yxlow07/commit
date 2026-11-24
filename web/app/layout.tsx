import type { Metadata } from "next";
import { Suspense } from "react";
import { Hanken_Grotesk, Nunito } from "next/font/google";
import { AppShell, Splash } from "@/components/AppShell";
import "./globals.css";

/** The app's one family (App Design Spec, "Type"); Nunito only for themes that ask for "rounded". */
const hanken = Hanken_Grotesk({ variable: "--font-hanken", subsets: ["latin"], weight: ["300", "400", "500", "600", "700"] });
const nunito = Nunito({ variable: "--font-nunito", subsets: ["latin"] });

/** No title here: the app shell renders <title> per page (React 19 hoists it into <head>). */
export const metadata: Metadata = {
  description: "Your commitments, on your phone and here.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${hanken.variable} ${nunito.variable} h-full antialiased`}>
      <body className="min-h-full">
        {/* The app is browser-only (session in localStorage); the static build ships this fallback. */}
        <Suspense fallback={<Splash />}>
          <AppShell>{children}</AppShell>
        </Suspense>
      </body>
    </html>
  );
}
