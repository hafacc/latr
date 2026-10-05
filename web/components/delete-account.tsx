"use client";

import { onAuthStateChanged, type User } from "firebase/auth";
import { type ReactElement, useEffect, useState } from "react";
import { auth, firebaseConfigured } from "../utils/firebase";
import { useTodos } from "../utils/store";
import { GoogleMark } from "./auth-menu";

type Stage = "idle" | "working" | "deleted" | "kept" | "failed";

const button =
  "h-10 px-4 rounded-[10px] text-sm font-medium transition-colors disabled:opacity-50";

/** The whole of `/delete-account/`: sign in if needed, then delete the account from the web alone. */
export default function DeleteAccount(): ReactElement {
  const { holder } = useTodos();
  const [user, setUser] = useState<User | null>(null);
  const [stage, setStage] = useState<Stage>("idle");

  useEffect(() => {
    if (!firebaseConfigured()) return;
    return onAuthStateChanged(auth(), setUser);
  }, []);

  async function signIn(): Promise<void> {
    try {
      await holder.signIn();
    } catch (e) {
      console.error(e);
    }
  }

  async function remove(keep: boolean): Promise<void> {
    setStage("working");
    try {
      await holder.deleteAccount(keep);
      setStage(keep ? "kept" : "deleted");
    } catch (e) {
      console.error(e);
      setStage("failed");
    }
  }

  const finished = stage === "deleted" || stage === "kept";
  return (
    <main className="mx-auto max-w-[620px] px-5 py-12 text-[15px] leading-relaxed text-text-secondary">
      <a href="../" className="text-sm text-accent hover:underline">
        Latr
      </a>
      <h1 className="mt-3 mb-0 text-2xl font-semibold text-text">
        Delete your Latr account
      </h1>
      {finished ? (
        <p className="mt-5 mb-0">
          Your account is deleted.
          {stage === "kept" && " Your todos are still on this device."}
        </p>
      ) : user ? (
        <>
          <p className="mt-5 mb-0">
            Signed in as{" "}
            <span className="text-text">{user.email ?? user.displayName}</span>.{" "}
            <button
              type="button"
              onClick={() => void holder.signOut()}
              className="text-accent hover:underline"
            >
              Use a different account
            </button>
          </p>
          <p className="mt-4 mb-0">
            This deletes your account and everything synced to it: your todos
            and your learned snooze times. It happens right away and can't be
            undone.
          </p>
          {stage === "failed" && (
            <p className="mt-4 mb-0 text-danger">
              Deleting didn't finish. Some data may already be gone. Please try
              again.
            </p>
          )}
          <div className="mt-5 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={stage === "working"}
              onClick={() => void remove(true)}
              className={`${button} bg-surface border border-border text-text hover:bg-surface-hover`}
            >
              Keep todos on this device
            </button>
            <button
              type="button"
              disabled={stage === "working"}
              onClick={() => void remove(false)}
              className={`${button} bg-danger text-on-danger hover:opacity-90`}
            >
              Delete everything
            </button>
          </div>
        </>
      ) : (
        <>
          <p className="mt-5 mb-0">
            Sign in to delete your account and everything synced to it.
          </p>
          <button
            type="button"
            onClick={() => void signIn()}
            className={`${button} mt-5 flex items-center gap-2 bg-surface border border-border text-text hover:bg-surface-hover`}
          >
            <GoogleMark />
            Continue with Google
          </button>
        </>
      )}
      <p className="mt-9 mb-0">
        The Android app has the same option in its account menu.
      </p>
    </main>
  );
}
