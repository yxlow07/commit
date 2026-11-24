"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loading } from "@/components/ui";

/** Workouts live with commitments now (sidebar and Today); old links land on Today. */
export default function WorkoutsPage() {
  const router = useRouter();
  useEffect(() => { router.replace("/"); }, [router]);
  return <Loading />;
}
