"use client";

import { useQuery, type QueryClient } from "@tanstack/react-query";
import { supabase } from "./supabase";
import { putRow } from "./store";
import type { CollectionGroup, CollectionItem, CollectionSort } from "@/lib/domain/types";

/**
 * Collections on the web: same rows and rules as the phone (CollectionRepository). Photos live in
 * the private Storage bucket [BUCKET] under `{user_id}/`; the web shows them through signed URLs.
 */
export const BUCKET = "collection-photos";

export const SORTS: { id: CollectionSort; label: string }[] = [
  { id: "manual", label: "Manual" },
  { id: "name", label: "Name A–Z" },
  { id: "missing_first", label: "Missing first" },
  { id: "collected_first", label: "Collected first" },
  { id: "newest", label: "Newest" },
];

const manual = (a: CollectionItem, b: CollectionItem) => a.sort_order - b.sort_order || a.created_at - b.created_at;

/** Items in a group's sort order; ties fall back to manual order, as on the phone (CollectionSorting). */
export function sortItems(items: CollectionItem[], sort: CollectionSort): CollectionItem[] {
  const list = [...items];
  switch (sort) {
    case "name":
      return list.sort((a, b) => Number(!a.name) - Number(!b.name) || (a.name ?? "").localeCompare(b.name ?? "", undefined, { sensitivity: "base" }) || manual(a, b));
    case "missing_first":
      return list.sort((a, b) => a.collected - b.collected || manual(a, b));
    case "collected_first":
      return list.sort((a, b) => b.collected - a.collected || manual(a, b));
    case "newest":
      return list.sort((a, b) => b.created_at - a.created_at || manual(a, b));
    default:
      return list.sort(manual);
  }
}

async function insert<T extends "collection_groups" | "collection_items">(qc: QueryClient, table: T, row: Record<string, unknown>) {
  const { data, error } = await supabase.from(table).insert({ deleted: 0, ...row }).select().single();
  if (error) throw error;
  putRow(qc, table, data as never);
  return data as T extends "collection_groups" ? CollectionGroup : CollectionItem;
}

async function update<T extends "collection_groups" | "collection_items">(qc: QueryClient, table: T, id: string, changes: Record<string, unknown>) {
  const { data, error } = await supabase.from(table).update(changes as never).eq("id", id).select().single();
  if (error) throw error;
  putRow(qc, table, data as never);
  return data as T extends "collection_groups" ? CollectionGroup : CollectionItem;
}

export const createGroup = (qc: QueryClient, commitmentId: string, name: string, sortOrder: number) =>
  insert(qc, "collection_groups", { id: crypto.randomUUID(), commitment_id: commitmentId, name: name.trim(), sort_order: sortOrder, sort_mode: "manual" });

export const updateGroup = (qc: QueryClient, id: string, changes: Partial<CollectionGroup>) => update(qc, "collection_groups", id, changes);

/** Deletes a group and its items (soft deletes, so the phone drops them too). */
export async function deleteGroup(qc: QueryClient, group: CollectionGroup, items: CollectionItem[]) {
  await Promise.all(items.filter((i) => i.group_id === group.id).map((i) => update(qc, "collection_items", i.id, { deleted: 1 })));
  await update(qc, "collection_groups", group.id, { deleted: 1 });
}

export const toggleItem = (qc: QueryClient, item: CollectionItem) => update(qc, "collection_items", item.id, { collected: item.collected ? 0 : 1 });

export interface ItemInput {
  name: string;
  groupId: string;
  collected: boolean;
  /** A new photo to upload, or null to keep the current one. */
  photo: File | null;
  removePhoto: boolean;
}

/** Creates ([item] null) or edits an item; a new photo is downscaled and uploaded first. */
export async function saveItem(qc: QueryClient, userId: string, item: CollectionItem | null, input: ItemInput, nextOrder: number) {
  const id = item?.id ?? crypto.randomUUID();
  let photoPath = input.removePhoto ? null : item?.photo_path ?? null;
  if (input.photo) {
    // PNGs stay PNG (keeps transparency); everything else becomes JPEG. Same rule as the phone.
    const png = input.photo.type === "image/png";
    const type = png ? "image/png" : "image/jpeg";
    const blob = await downscale(input.photo, type);
    const key = `${userId}/${id}-${Date.now()}.${png ? "png" : "jpg"}`;
    const { error } = await supabase.storage.from(BUCKET).upload(key, blob, { contentType: type, upsert: true });
    if (error) throw error;
    photoPath = key;
  }
  const row = {
    name: input.name.trim() || null, group_id: input.groupId, collected: input.collected ? 1 : 0, photo_path: photoPath,
  };
  if (item) {
    // Moving to another group puts it at the end there.
    return update(qc, "collection_items", id, item.group_id !== input.groupId ? { ...row, sort_order: nextOrder } : row);
  }
  return insert(qc, "collection_items", { id, ...row, sort_order: nextOrder, created_at: Date.now() });
}

export const deleteItem = (qc: QueryClient, id: string) => update(qc, "collection_items", id, { deleted: 1 });
export const restoreItem = (qc: QueryClient, id: string) => update(qc, "collection_items", id, { deleted: 0 });

/** At most 1280 px on the long side (same as the phone), as [type]. */
async function downscale(file: File, type: "image/png" | "image/jpeg"): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 1280 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Couldn't read that image"))), type, 0.85));
}

/** Signed URLs (1 hour) for photo keys, batched; refreshed before they expire. */
export function usePhotoUrls(keys: string[]) {
  const sorted = [...new Set(keys)].sort();
  return useQuery({
    queryKey: ["photo-urls", sorted],
    enabled: sorted.length > 0,
    staleTime: 50 * 60_000,
    queryFn: async () => {
      const { data, error } = await supabase.storage.from(BUCKET).createSignedUrls(sorted, 3600);
      if (error) throw error;
      return new Map<string, string>((data ?? []).flatMap((d) => (d.signedUrl && d.path ? [[d.path, d.signedUrl] as [string, string]] : [])));
    },
  });
}
