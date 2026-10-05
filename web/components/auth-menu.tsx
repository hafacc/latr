"use client";

import { onAuthStateChanged, type User } from "firebase/auth";
import {
  createContext,
  type ReactElement,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  LuChartColumn,
  LuChevronsUpDown,
  LuDownload,
  LuLogOut,
  LuMonitor,
  LuMoon,
  LuShield,
  LuSun,
  LuTrash2,
  LuUser,
} from "react-icons/lu";
import { auth, firebaseConfigured } from "../utils/firebase";
import { usePwa } from "../utils/pwa";
import { useTodos } from "../utils/store";
import Sheet from "./sheet";
import StatsWindow from "./stats-window";
import { type ThemeMode, useTheme } from "./theme";

type AuthValue = {
  user: User | null;
  signIn: () => Promise<void>;
  signOut: (force: boolean) => Promise<"done" | "pending">;
  deleteAccount: (keep: boolean) => Promise<void>;
};

const AuthCtx = createContext<AuthValue | null>(null);

function useAuth(): AuthValue {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}

export function AuthProvider({
  children,
}: {
  children: ReactNode;
}): ReactElement {
  const { holder } = useTodos();
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    if (!firebaseConfigured()) return;
    return onAuthStateChanged(auth(), setUser);
  }, []);

  // Reattach Firestore snapshot listener after tab wake / online — covers
  // WebChannel drops during long idle that would otherwise leave the
  // listener silent.
  useEffect(() => {
    if (!user) return;
    function wake() {
      if (document.visibilityState !== "visible") return;
      holder.reattach();
    }
    document.addEventListener("visibilitychange", wake);
    window.addEventListener("online", wake);
    return () => {
      document.removeEventListener("visibilitychange", wake);
      window.removeEventListener("online", wake);
    };
  }, [user, holder]);

  const value = useMemo<AuthValue>(
    () => ({
      user,
      async signIn() {
        if (!firebaseConfigured()) {
          alert(
            "Firebase web config needs an appId. Register a Web app in the Firebase console and paste the appId into web/utils/firebase.ts.",
          );
          return;
        }
        try {
          await holder.signIn();
        } catch (e) {
          console.error(e);
          alert(
            "Sign-in didn't complete, or syncing this device's edits failed — nothing was lost, but edits and deletes made while signed out may not have synced. Sign out and back in to retry.",
          );
        }
      },
      async signOut(force) {
        try {
          return await holder.signOut(force);
        } catch (e) {
          console.error(e);
          return "done";
        }
      },
      async deleteAccount(keep) {
        try {
          await holder.deleteAccount(keep);
        } catch (e) {
          console.error(e);
          alert(
            "Account deletion didn't finish — some remote data may already be gone. Please try again.",
          );
        }
      },
    }),
    [user, holder],
  );

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

function Avatar({
  user,
  size,
}: {
  user: User | null;
  size: string;
}): ReactElement {
  if (user?.photoURL) {
    return (
      // biome-ignore lint/performance/noImgElement: user avatars come from external providers; optimization not worth the config
      <img
        src={user.photoURL}
        alt=""
        className={`${size} rounded-full shrink-0`}
        referrerPolicy="no-referrer"
      />
    );
  } else {
    return (
      <span
        className={`${size} rounded-full shrink-0 bg-accent-soft text-accent flex items-center justify-center`}
      >
        <LuUser className="w-1/2 h-1/2" aria-hidden="true" />
      </span>
    );
  }
}

const THEME_OPTIONS: { mode: ThemeMode; label: string; icon: ReactElement }[] =
  [
    {
      mode: "system",
      label: "System",
      icon: <LuMonitor className="w-3.5 h-3.5" />,
    },
    { mode: "light", label: "Light", icon: <LuSun className="w-3.5 h-3.5" /> },
    { mode: "dark", label: "Dark", icon: <LuMoon className="w-3.5 h-3.5" /> },
  ];

function ThemeSegmented(): ReactElement {
  const { mode, setMode } = useTheme();
  return (
    <fieldset className="flex p-0.5 rounded-full bg-surface-muted border-0 m-0">
      <legend className="sr-only">Theme</legend>
      {THEME_OPTIONS.map((o) => (
        <button
          key={o.mode}
          type="button"
          aria-pressed={mode === o.mode}
          onClick={() => setMode(o.mode)}
          className={`
            flex-1 h-7 inline-flex items-center justify-center gap-1.5 rounded-full text-xs font-medium transition-colors
            ${mode === o.mode ? "bg-surface text-text shadow-pop" : "text-text-secondary hover:text-text"}
          `}
        >
          {o.icon}
          {o.label}
        </button>
      ))}
    </fieldset>
  );
}

export function GoogleMark(): ReactElement {
  return (
    <svg viewBox="0 0 24 24" className="w-4 h-4" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.4h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.2-2.1 3.5-5.1 3.5-8.7z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.2 0 6-1.1 7.9-2.9l-3.9-3c-1.1.7-2.4 1.2-4 1.2-3.1 0-5.7-2.1-6.6-4.9h-4v3.1A12 12 0 0 0 12 24z"
      />
      <path
        fill="#FBBC05"
        d="M5.4 14.4a7.2 7.2 0 0 1 0-4.7V6.6h-4a12 12 0 0 0 0 10.9l4-3.1z"
      />
      <path
        fill="#EA4335"
        d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4A12 12 0 0 0 1.4 6.6l4 3.1C6.3 6.9 8.9 4.8 12 4.8z"
      />
    </svg>
  );
}

const dialogButton =
  "h-8 px-3 rounded-lg text-sm text-text hover:bg-surface-hover transition-colors";
const dialogDangerButton =
  "h-8 px-3 rounded-lg text-sm font-medium bg-danger text-on-danger hover:opacity-90 transition-opacity";

function ConfirmDialog({
  id,
  title,
  body,
  onCancel,
  children,
}: {
  id: string;
  title: string;
  body: string;
  onCancel: () => void;
  children: ReactNode;
}): ReactElement {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={id}
      onCancel={(e) => {
        e.preventDefault();
        onCancel();
      }}
      onKeyDown={(e) => e.stopPropagation()}
      className="m-auto p-5 w-[min(400px,calc(100vw-32px))] rounded-[14px] bg-surface-raised text-text shadow-pop animate-rise"
    >
      <h2 id={id} className="m-0 text-base font-semibold">
        {title}
      </h2>
      <p className="mt-2 mb-5 text-sm text-text-secondary">{body}</p>
      <div className="flex flex-wrap justify-end gap-2">{children}</div>
    </dialog>
  );
}

function ConfirmDelete({
  onConfirm,
  onCancel,
}: {
  onConfirm: (keep: boolean) => void;
  onCancel: () => void;
}): ReactElement {
  return (
    <ConfirmDialog
      id="delete-account-title"
      title="Delete your account?"
      body="This deletes your account and everything synced to it."
      onCancel={onCancel}
    >
      <button type="button" onClick={onCancel} className={dialogButton}>
        Cancel
      </button>
      <button
        type="button"
        onClick={() => onConfirm(true)}
        className={dialogButton}
      >
        Keep todos on this device
      </button>
      <button
        type="button"
        onClick={() => onConfirm(false)}
        className={dialogDangerButton}
      >
        Delete everything
      </button>
    </ConfirmDialog>
  );
}

function ConfirmSignOut({
  onConfirm,
  onCancel,
}: {
  onConfirm: () => void;
  onCancel: () => void;
}): ReactElement {
  return (
    <ConfirmDialog
      id="sign-out-title"
      title="Changes haven't synced"
      body="Some changes on this device haven't reached your account yet. Signing out now drops them."
      onCancel={onCancel}
    >
      <button type="button" onClick={onCancel} className={dialogButton}>
        Wait
      </button>
      <button type="button" onClick={onConfirm} className={dialogDangerButton}>
        Sign out now
      </button>
    </ConfirmDialog>
  );
}

function InstallRow(): ReactElement | null {
  const { install, promptInstall } = usePwa();
  const [showSteps, setShowSteps] = useState(false);
  if (install === null) return null;
  return (
    <div className="flex flex-col">
      <button
        type="button"
        onClick={() =>
          install === "prompt" ? promptInstall() : setShowSteps((v) => !v)
        }
        aria-expanded={install === "ios" ? showSteps : undefined}
        className="flex items-center gap-2.5 h-10 px-2 rounded-[10px] text-sm text-text hover:bg-surface-hover transition-colors"
      >
        <LuDownload className="w-4 h-4 text-text-secondary" />
        Install app
      </button>
      {showSteps && (
        <p className="m-0 px-2 pb-1 text-xs text-text-secondary">
          Tap Share, then Add to Home Screen.
        </p>
      )}
    </div>
  );
}

function PickLogRow(): ReactElement {
  const { snoozePickLog, setSnoozePickLog } = useTodos();
  return (
    <button
      type="button"
      role="switch"
      aria-checked={snoozePickLog}
      onClick={() => setSnoozePickLog(!snoozePickLog)}
      className="flex items-center gap-3 px-2 py-2 rounded-[10px] text-left hover:bg-surface-hover transition-colors"
    >
      <span className="flex-1 min-w-0 text-sm text-text">
        Improve snooze suggestions
      </span>
      <span
        aria-hidden="true"
        className={`relative w-9 h-5 shrink-0 rounded-full transition-colors ${
          snoozePickLog ? "bg-accent" : "bg-border"
        }`}
      >
        <span
          className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-surface shadow-pop transition-transform ${
            snoozePickLog ? "translate-x-4" : ""
          }`}
        />
      </span>
    </button>
  );
}

function StatsRow({ user }: { user: User }): ReactElement | null {
  const [admin, setAdmin] = useState(false);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    let live = true;
    user
      .getIdTokenResult()
      .then((result) => {
        if (live) setAdmin(result.claims.admin === true);
      })
      .catch((e) => console.error("id token read failed", e));
    return () => {
      live = false;
    };
  }, [user]);
  if (!admin) return null;
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex items-center gap-2.5 h-10 px-2 rounded-[10px] text-sm text-text hover:bg-surface-hover transition-colors"
      >
        <LuChartColumn className="w-4 h-4 text-text-secondary" />
        Stats
      </button>
      {open && <StatsWindow onClose={() => setOpen(false)} />}
    </>
  );
}

/** Account details, theme and sign-in/out; shared by the desktop popover and the phone sheet. */
function AccountPanel({ onDone }: { onDone: () => void }): ReactElement {
  const { user, signIn, signOut, deleteAccount } = useAuth();
  const [confirming, setConfirming] = useState(false);
  const [unsynced, setUnsynced] = useState(false);

  return (
    <div className="flex flex-col gap-3 p-3">
      {user ? (
        <div className="flex items-center gap-3 px-1">
          <Avatar user={user} size="w-9 h-9" />
          <div className="min-w-0">
            <div className="text-sm font-medium truncate">
              {user.displayName ?? user.email}
            </div>
            <div className="text-xs text-text-secondary truncate">
              {user.email}
            </div>
          </div>
        </div>
      ) : (
        <div className="px-1">
          <div className="text-sm font-medium">Sync across devices</div>
          <div className="text-xs text-text-secondary mt-0.5">
            Sign in to keep your todos on every device.
          </div>
        </div>
      )}
      <ThemeSegmented />
      <InstallRow />
      <a
        href={`${process.env.NEXT_PUBLIC_BASE_PATH ?? ""}/privacy/`}
        className="flex items-center gap-2.5 h-10 px-2 rounded-[10px] text-sm text-text hover:bg-surface-hover transition-colors"
      >
        <LuShield className="w-4 h-4 text-text-secondary" />
        Privacy
      </a>
      {user && <PickLogRow />}
      {user && <StatsRow user={user} />}
      {user ? (
        <div className="flex flex-col">
          <button
            type="button"
            onClick={async () => {
              if ((await signOut(false)) === "pending") setUnsynced(true);
              else onDone();
            }}
            className="flex items-center gap-2.5 h-10 px-2 rounded-[10px] text-sm text-text hover:bg-surface-hover transition-colors"
          >
            <LuLogOut className="w-4 h-4 text-text-secondary" />
            Sign out
          </button>
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="flex items-center gap-2.5 h-10 px-2 rounded-[10px] text-sm text-danger hover:bg-danger-soft transition-colors"
          >
            <LuTrash2 className="w-4 h-4" />
            Delete account
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={async () => {
            await signIn();
            onDone();
          }}
          className="flex items-center justify-center gap-2 h-10 rounded-[10px] bg-surface border border-border text-sm font-medium text-text hover:bg-surface-hover transition-colors"
        >
          <GoogleMark />
          Continue with Google
        </button>
      )}
      {confirming && (
        <ConfirmDelete
          onCancel={() => setConfirming(false)}
          onConfirm={async (keep) => {
            setConfirming(false);
            await deleteAccount(keep);
            onDone();
          }}
        />
      )}
      {unsynced && (
        <ConfirmSignOut
          onCancel={() => setUnsynced(false)}
          onConfirm={async () => {
            setUnsynced(false);
            await signOut(true);
            onDone();
          }}
        />
      )}
    </div>
  );
}

/** Desktop sidebar footer: account row that opens a popover above it. */
export function SidebarAccount(): ReactElement {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    // A collapsed sidebar shrinks back when the pointer leaves it; close with it.
    const sidebar = rootRef.current?.closest("aside");
    function onLeave() {
      if (sidebar?.dataset.collapsed === "true") setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    sidebar?.addEventListener("mouseleave", onLeave);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
      sidebar?.removeEventListener("mouseleave", onLeave);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="w-full flex items-center gap-2.5 h-11 px-2 rounded-[10px] text-sm text-text hover:bg-surface-hover transition-colors"
        title={user?.email ?? "Account"}
      >
        <Avatar user={user} size="w-7 h-7" />
        <span className="flex-1 min-w-0 text-left truncate font-medium group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:inline">
          {user ? (user.displayName ?? user.email) : "Sign in"}
        </span>
        <LuChevronsUpDown className="w-4 h-4 text-text-secondary shrink-0 group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:block" />
      </button>
      {open && (
        <div className="absolute bottom-full mb-2 inset-x-0 rounded-[14px] bg-surface-raised shadow-pop z-30 animate-rise">
          <AccountPanel onDone={() => setOpen(false)} />
        </div>
      )}
    </div>
  );
}

/** Phone app bar: avatar that opens the account sheet. */
export function AccountButton(): ReactElement {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Account"
        className="w-10 h-10 flex items-center justify-center rounded-full hover:bg-surface-hover transition-colors"
      >
        <Avatar user={user} size="w-8 h-8" />
      </button>
      {open && (
        <Sheet label="Account" onClose={() => setOpen(false)}>
          <AccountPanel onDone={() => setOpen(false)} />
        </Sheet>
      )}
    </>
  );
}
