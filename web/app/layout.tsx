import type { Metadata } from "next";
import { Suspense } from "react";
import { Geist, Geist_Mono, Nunito } from "next/font/google";
import { AppShell, Splash } from "@/components/AppShell";
import "./globals.css";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });
const nunito = Nunito({ variable: "--font-nunito", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Commit",
  description: "Todos, chores and workouts, in sync with the Commit Android app.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable} ${nunito.variable} h-full antialiased`}>
      <body className="min-h-full">
        {/* The app is browser-only (session in localStorage); the static build ships this fallback. */}
        <Suspense fallback={<Splash />}>
          <AppShell>{children}</AppShell>
        </Suspense>
      </body>
    </html>
  );
}
