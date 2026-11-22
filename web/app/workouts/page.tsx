"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Spinner } from "@/components/ui";

/**
 * Workouts used to have their own page; their routines now show on the main page when a workout
 * commitment is selected (as on the phone's Today). Old links land there.
 */
export default function WorkoutsPage() {
  const router = useRouter();
  useEffect(() => { router.replace("/"); }, [router]);
  return <Spinner />;
}
