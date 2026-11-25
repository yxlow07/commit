"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCommitments } from "./Nav";
import { usePanel } from "./EditPanel";
import { ADD_EVENT } from "./Todos";
import { Button, Dialog } from "./ui";

const SHORTCUTS: [string, string][] = [
  ["N", "Add a todo"],
  ["↑ ↓ or J K", "Move between todos"],
  ["Enter", "Open the focused todo"],
  ["X", "Tick or untick the focused todo"],
  ["Delete", "Delete the focused todo"],
  ["Esc", "Close the panel, dialog or popover"],
  ["Ctrl / Cmd + Enter", "Save in the panel or a dialog"],
  ["G then G, A, H or S", "Go to Groups, Agenda, History or Settings"],
  ["[ and ]", "Previous and next commitment"],
  ["?", "Show these shortcuts"],
];

const typing = (el: EventTarget | null) =>
  el instanceof HTMLElement && (el.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(el.tagName));

/**
 * Single-key shortcuts (web spec, "Keyboard shortcuts"): only when no text field has focus and no
 * dialog is open. Row keys (↑ ↓ J K X Delete) live on the rows themselves.
 */
export function Shortcuts() {
  const router = useRouter();
  const panel = usePanel();
  const pathname = usePathname();
  const params = useSearchParams();
  const commitments = useCommitments();
  const currentId = pathname.startsWith("/c") ? params.get("id") : null;
  const [help, setHelp] = useState(false);
  const goPending = useRef(0);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.defaultPrevented || e.ctrlKey || e.metaKey || e.altKey || typing(e.target)) return;
      if (document.querySelector("dialog[open]")) return;
      const k = e.key;

      if (Date.now() - goPending.current < 1500) {
        goPending.current = 0;
        const to = { g: "/", a: "/agenda", h: "/history", s: "/settings" }[k.toLowerCase()];
        if (to) { e.preventDefault(); router.push(to); return; }
      }
      if (k === "?") { e.preventDefault(); setHelp(true); }
      else if (k === "g" || k === "G") { goPending.current = Date.now(); }
      else if (k === "n" || k === "N") {
        e.preventDefault();
        if (document.querySelector("[data-inline-add]")) window.dispatchEvent(new Event(ADD_EVENT));
        else panel.openNew();
      } else if (k === "[" || k === "]") {
        if (!commitments.length) return;
        const i = commitments.findIndex((c) => c.id === currentId);
        const next = commitments[i < 0 ? 0 : (i + (k === "]" ? 1 : -1) + commitments.length) % commitments.length];
        e.preventDefault();
        router.push(`/c?id=${next.id}`);
      } else if ((k === "ArrowDown" || k === "j") && !document.activeElement?.closest("[data-todo-row], [role=tablist], [role=grid], [role=radiogroup]")) {
        const first = document.querySelector<HTMLElement>("[data-todo-row]");
        if (first && (document.activeElement === document.body || document.activeElement == null)) { e.preventDefault(); first.focus(); }
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [router, panel, commitments, currentId]);

  return (
    <Dialog open={help} onClose={() => setHelp(false)} title="Keyboard shortcuts">
      <table className="w-full text-meta">
        <tbody>
          {SHORTCUTS.map(([keys, what]) => (
            <tr key={keys}>
              <td className="py-1.5 pr-4 align-top"><kbd className="whitespace-nowrap rounded-md bg-fill px-1.5 py-0.5 font-sans text-micro font-medium">{keys}</kbd></td>
              <td className="py-1.5 text-text-strong">{what}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="text-micro text-muted">Single keys work when no text field has focus.</p>
      <div className="flex justify-end"><Button variant="primary" onClick={() => setHelp(false)}>Done</Button></div>
    </Dialog>
  );
}
