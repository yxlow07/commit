"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { Commitment, CommitmentType } from "@/lib/domain/types";
import { PALETTE, cssColor } from "@/lib/domain/format";
import { createCommitment, updateCommitment } from "@/lib/data/mutations";
import { Button, ErrorText, Field, IconButton, Icons, Input, Modal, useAction } from "./ui";

/** Create (commitment = null) or edit a commitment (F1.1–F1.3). */
export function CommitmentDialog({
  open, onClose, commitment, nextSortOrder, onCreated,
}: { open: boolean; onClose: () => void; commitment: Commitment | null; nextSortOrder: number; onCreated?: (id: string) => void }) {
  return (
    <Modal open={open} onClose={onClose} title={commitment ? "Edit commitment" : "New commitment"}>
      {open && <Form commitment={commitment} nextSortOrder={nextSortOrder} onDone={onClose} onCreated={onCreated} />}
    </Modal>
  );
}

function Form({ commitment, nextSortOrder, onDone, onCreated }: { commitment: Commitment | null; nextSortOrder: number; onDone: () => void; onCreated?: (id: string) => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState(commitment?.name ?? "");
  const [color, setColor] = useState(commitment?.color ?? PALETTE[0]);
  const [type, setType] = useState<CommitmentType>(commitment?.type ?? "standard");
  const [showInWidget, setShowInWidget] = useState(commitment ? commitment.show_in_widget === 1 : true);
  const action = useAction();

  async function save() {
    const ok = await action.run(async () => {
      if (commitment) {
        await updateCommitment(qc, commitment.id, { name: name.trim(), color, show_in_widget: showInWidget ? 1 : 0 });
      } else {
        const created = await createCommitment(qc, name, type, color, nextSortOrder);
        onCreated?.(created.id);
      }
    });
    if (ok) onDone();
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); save(); }}>
      <Field label="Name">
        <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Study, Work, Home…" />
      </Field>
      <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Colour">
        {PALETTE.map((c) => (
          <button
            key={c}
            type="button"
            role="radio"
            aria-checked={c === color}
            aria-label={`Colour ${cssColor(c)}`}
            onClick={() => setColor(c)}
            className={`h-8 w-8 rounded-full transition ${c === color ? "ring-2 ring-fg ring-offset-2 ring-offset-surface" : ""}`}
            style={{ background: cssColor(c) }}
          />
        ))}
      </div>
      {commitment ? (
        <label className="flex items-center justify-between text-sm">
          Show on the widget
          <input type="checkbox" checked={showInWidget} onChange={(e) => setShowInWidget(e.target.checked)} className="h-5 w-5 accent-[var(--primary)]" />
        </label>
      ) : (
        <div className="flex gap-2" role="radiogroup" aria-label="Type">
          {(["standard", "workout"] as const).map((t) => (
            <Button key={t} variant={type === t ? "primary" : "secondary"} onClick={() => setType(t)} aria-pressed={type === t} className="flex-1">
              {t === "standard" ? "Todos" : "Workout"}
            </Button>
          ))}
        </div>
      )}
      <ErrorText error={action.error} />
      <div className="flex items-center justify-end gap-2">
        {/* Archive bottom left, as on the phone; restore it from Settings > Archived. */}
        {commitment && (
          <IconButton
            label={`Archive ${commitment.name}`}
            className="mr-auto h-10 w-10 rounded-xl bg-surface-2"
            onClick={async () => { if (await action.run(() => updateCommitment(qc, commitment.id, { archived: 1 }))) onDone(); }}
          >
            {Icons.archive}
          </IconButton>
        )}
        <Button variant="ghost" onClick={onDone}>Cancel</Button>
        <Button variant="primary" type="submit" disabled={!name.trim() || action.busy}>Save</Button>
      </div>
    </form>
  );
}
