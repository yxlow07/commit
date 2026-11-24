"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import type { Commitment, CommitmentType } from "@/lib/domain/types";
import { PALETTE, cssColor } from "@/lib/domain/format";
import { createCommitment, updateCommitment } from "@/lib/data/mutations";
import { useOnline, OFFLINE_REASON } from "./Online";
import { useToast } from "./Toast";
import { Button, Dialog, ErrorText, Field, FieldGroup, IconTile, Icons, Segmented, Switch, TextField, useAction } from "./ui";

/** New or edit commitment (F1.1–F1.3). Archive sits bottom left, as on the phone, with an undo toast. */
export function CommitmentDialog({
  open, onClose, commitment, type = "standard", nextSortOrder,
}: { open: boolean; onClose: () => void; commitment: Commitment | null; type?: CommitmentType; nextSortOrder: number }) {
  const title = commitment ? (commitment.type === "workout" ? "Edit workout" : "Edit commitment") : type === "workout" ? "New workout commitment" : "New commitment";
  return (
    <Dialog open={open} onClose={onClose} title={title}>
      {open && <Form commitment={commitment} initialType={type} nextSortOrder={nextSortOrder} onDone={onClose} />}
    </Dialog>
  );
}

function Form({ commitment, initialType, nextSortOrder, onDone }: { commitment: Commitment | null; initialType: CommitmentType; nextSortOrder: number; onDone: () => void }) {
  const qc = useQueryClient();
  const router = useRouter();
  const toast = useToast();
  const online = useOnline();
  const [name, setName] = useState(commitment?.name ?? "");
  const [color, setColor] = useState(commitment?.color ?? PALETTE[0]);
  const [type, setType] = useState<CommitmentType>(commitment?.type ?? initialType);
  const [showInWidget, setShowInWidget] = useState(commitment ? commitment.show_in_widget === 1 : true);
  const action = useAction();

  async function save() {
    let createdId = "";
    const ok = await action.run(async () => {
      if (commitment) {
        await updateCommitment(qc, commitment.id, { name: name.trim(), color, show_in_widget: showInWidget ? 1 : 0 });
      } else {
        createdId = (await createCommitment(qc, name, type, color, nextSortOrder)).id;
      }
    });
    if (!ok) return;
    onDone();
    if (createdId) router.push(`/c?id=${createdId}`);
  }

  async function archive() {
    if (!commitment) return;
    const ok = await action.run(() => updateCommitment(qc, commitment.id, { archived: 1 }));
    if (!ok) return;
    onDone();
    toast({ message: `${commitment.name} archived`, action: { label: "Undo", run: () => updateCommitment(qc, commitment.id, { archived: 0 }) } });
  }

  return (
    <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); save(); }}
      onKeyDown={(e) => { if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) { e.preventDefault(); save(); } }}>
      <Field label="Name" htmlFor="commitment-name">
        <TextField id="commitment-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder={type === "workout" ? "Gym, Running…" : "Study, Work, Home…"} />
      </Field>
      {!commitment && (
        <FieldGroup label="Type">
          <Segmented label="Type" value={type} onChange={setType} items={[{ id: "standard", label: "Todos" }, { id: "workout", label: "Workouts" }]} />
        </FieldGroup>
      )}
      <FieldGroup label="Colour">
        <div className="flex flex-wrap gap-1" role="radiogroup" aria-label="Colour">
          {PALETTE.map((c) => (
            <button
              key={c}
              type="button"
              role="radio"
              aria-checked={c === color}
              aria-label={`Colour ${cssColor(c)}`}
              onClick={() => setColor(c)}
              className="flex size-10 cursor-pointer items-center justify-center rounded-full"
            >
              <span className={`size-7 rounded-full ${c === color ? "ring-2 ring-ink ring-offset-2 ring-offset-surface" : ""}`} style={{ background: cssColor(c) }} />
            </button>
          ))}
        </div>
      </FieldGroup>
      {commitment && commitment.type !== "workout" && (
        <div className="flex items-center justify-between gap-3">
          <span className="text-body">Show on the widget</span>
          <Switch label="Show on the widget" checked={showInWidget} onChange={setShowInWidget} />
        </div>
      )}
      <ErrorText error={action.error} />
      <div className="flex items-center gap-2">
        {commitment && <IconTile label={`Archive ${commitment.name}`} disabled={!online || action.busy} onClick={archive}>{Icons.archive}</IconTile>}
        <span className="flex-1" />
        <Button variant="quiet" onClick={onDone}>Cancel</Button>
        <Button variant="primary" type="submit" title={online ? undefined : OFFLINE_REASON} disabled={!name.trim() || action.busy || !online}>
          {commitment ? "Save" : type === "workout" ? "Add workout" : "Add commitment"}
        </Button>
      </div>
    </form>
  );
}
