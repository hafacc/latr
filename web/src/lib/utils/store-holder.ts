import { FirebaseError } from "firebase/app";
import {
  GoogleAuthProvider,
  onAuthStateChanged,
  reauthenticateWithPopup,
  signInWithPopup,
  type User,
} from "firebase/auth";
import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  serverTimestamp,
  waitForPendingWrites,
  writeBatch,
} from "firebase/firestore";
import { auth, clearDbCache, db, firebaseConfigured } from "./firebase";
import { planMerge } from "./merge";
import { SnoozeStatsStore } from "./snooze-stats-store";
import { fromFirestore, type Todo, toFirestoreFields } from "./todo";
import {
  FirestoreTodoStore,
  LocalTodoStore,
  type TodoStore,
} from "./todo-store";

// Set once this build has dropped the copy older builds left on the device while signed in.
const MOVED_KEY = "latr:local-moved:v1";
const SIGN_OUT_WAIT_MS = 3_000;

/** Owns the live [TodoStore]; swaps it at the auth boundary. Todos move into the account on [signIn] and leave the device on [signOut]; [deleteAccount] can move them back. */
export class TodoStoreHolder {
  private localStore = new LocalTodoStore();
  private firestoreStore: FirestoreTodoStore | null = null;
  private currentStore: TodoStore = this.localStore;
  readonly snoozeStats = new SnoozeStatsStore();
  private listeners = new Set<() => void>();
  private storeUnsub: (() => void) | null = null;
  private unsubAuth: (() => void) | null = null;
  private user: User | null = null;

  constructor() {
    this.setup();
  }

  /** Idempotent; re-run after [dispose] to survive a StrictMode double-mount. */
  setup(): void {
    this.wireStoreListener();
    if (!this.unsubAuth && firebaseConfigured()) {
      this.unsubAuth = onAuthStateChanged(auth(), (u) => {
        const prevUid = this.user?.uid ?? null;
        this.user = u;
        this.dropStaleLocalCopy(u !== null);
        if (u && u.uid !== prevUid) this.swapToFirestore(u.uid);
        // Also how a sign-out made in another tab reaches this one.
        else if (!u && prevUid !== null) void this.leaveAccount();
      });
    }
  }

  getStore(): TodoStore {
    return this.currentStore;
  }

  getUser(): User | null {
    return this.user;
  }

  /** Subscribe to changes to the active store's todos AND to store swaps. */
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  hydrate(): void {
    this.localStore.hydrate();
    this.snoozeStats.hydrate();
  }

  /** Sign in via Google, then push offline local edits into Firestore. */
  async signIn(): Promise<void> {
    if (!firebaseConfigured()) {
      throw new Error("firebase not configured");
    }
    let uid: string;
    try {
      const result = await signInWithPopup(auth(), new GoogleAuthProvider());
      uid = result.user.uid;
    } catch (e) {
      // A dismissed popup is benign; real failures propagate.
      if (
        e instanceof FirebaseError &&
        (e.code === "auth/popup-closed-by-user" ||
          e.code === "auth/cancelled-popup-request")
      ) {
        return;
      }
      throw e;
    }
    try {
      await this.mergeLocalIntoFirestore(uid);
    } finally {
      // Runs even if the todo merge failed: the user is still signed in.
      try {
        await this.snoozeStats.pushToRemote(uid);
      } catch (e) {
        console.error("snooze-stats sign-in push failed", e);
      }
    }
  }

  /**
   * Sign out and wipe the account's data from this device. Returns "pending",
   * changing nothing, when edits haven't reached the account within a short
   * wait; [force] signs out anyway and drops them.
   */
  async signOut(force = false): Promise<"done" | "pending"> {
    if (this.firestoreStore && !force && !(await this.writesSent())) {
      return "pending";
    } else {
      await auth().signOut();
      await this.leaveAccount();
      return "done";
    }
  }

  /** Wipe remote, then delete the auth user. [keep] first moves the todos and snooze counts back onto this device. */
  async deleteAccount(keep: boolean): Promise<void> {
    const user = this.user;
    if (!user) return;
    // Reauth before wiping, so a rejected delete() can't leave a live account with no data.
    await reauthenticateWithPopup(user, new GoogleAuthProvider());
    // Todos are matched by id, so an early copy is harmless if the delete fails; counts add up, so they are only kept once it succeeds.
    const counts = keep ? this.snoozeStats.getShared() : null;
    if (keep) {
      const remote = await this.firestoreStore?.snapshot();
      if (remote) this.localStore.replaceAll(remote);
    } else {
      this.localStore.clear();
    }
    if (this.firestoreStore) await this.firestoreStore.deleteAll();
    await deleteDoc(doc(db(), "users", user.uid));
    await user.delete();
    if (counts) this.snoozeStats.keepLocally(counts);
    await this.leaveAccount();
  }

  /** Reattach the Firestore snapshot listener (after tab wake / online). */
  reattach(): void {
    this.firestoreStore?.reattach();
    this.snoozeStats.reattach();
  }

  dispose(): void {
    if (this.unsubAuth) {
      this.unsubAuth();
      this.unsubAuth = null;
    }
    if (this.storeUnsub) {
      this.storeUnsub();
      this.storeUnsub = null;
    }
    this.localStore.dispose();
    this.firestoreStore?.dispose();
    this.firestoreStore = null;
    this.snoozeStats.dispose();
    // Reset so a re-setup with the same uid re-swaps instead of reusing the disposed store.
    this.user = null;
    this.currentStore = this.localStore;
    this.listeners.clear();
  }

  private setStore(next: TodoStore): void {
    this.currentStore = next;
    this.wireStoreListener();
    this.emit();
  }

  private wireStoreListener(): void {
    if (this.storeUnsub) this.storeUnsub();
    this.storeUnsub = this.currentStore.subscribe(() => this.emit());
  }

  private emit(): void {
    for (const l of this.listeners) l();
  }

  private swapToFirestore(uid: string): void {
    this.firestoreStore?.dispose();
    this.firestoreStore = new FirestoreTodoStore(uid);
    this.snoozeStats.attachRemote(uid);
    this.setStore(this.firestoreStore);
  }

  private swapToLocal(): void {
    this.firestoreStore?.dispose();
    this.firestoreStore = null;
    this.snoozeStats.detachRemote();
    this.setStore(this.localStore);
  }

  private async writesSent(): Promise<boolean> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timedOut = new Promise<boolean>((resolve) => {
      timer = setTimeout(() => resolve(false), SIGN_OUT_WAIT_MS);
    });
    try {
      return await Promise.race([
        waitForPendingWrites(db()).then(() => true),
        timedOut,
      ]);
    } catch (e) {
      console.error("waiting for unsent writes failed", e);
      return false;
    } finally {
      clearTimeout(timer);
    }
  }

  // Listeners must be gone before the Firestore instance they hang off is ended.
  private async leaveAccount(): Promise<void> {
    this.user = null;
    // Another tab may have changed the saved todos since this one read them.
    this.localStore.flush();
    this.localStore.hydrate();
    this.swapToLocal();
    try {
      await clearDbCache();
    } catch (e) {
      console.error("clearing the firestore cache failed", e);
    }
  }

  private dropStaleLocalCopy(signedIn: boolean): void {
    try {
      if (localStorage.getItem(MOVED_KEY) !== null) return;
      if (signedIn) this.localStore.clear();
      localStorage.setItem(MOVED_KEY, "1");
    } catch {
      // best-effort
    }
  }

  /** Publish local edits and pending deletes; see [planMerge]. */
  private async mergeLocalIntoFirestore(uid: string): Promise<void> {
    const col = collection(db(), "users", uid, "todos");
    const local = this.localStore.getWithTombstones();
    // Unfiltered, unlike the live listener: the plan needs to see the tombstones.
    const remoteSnap = await getDocs(col);
    const remoteById = new Map<string, Todo>();
    for (const d of remoteSnap.docs) {
      remoteById.set(
        d.id,
        fromFirestore(d.id, d.data() as Record<string, unknown>),
      );
    }
    const { toPush } = planMerge(local, remoteById);
    if (toPush.length > 0) {
      const batch = writeBatch(db());
      for (const t of toPush) {
        batch.set(
          doc(col, t.id),
          { ...toFirestoreFields(t), serverModifiedAt: serverTimestamp() },
          { merge: true },
        );
      }
      await batch.commit();
    }
    this.localStore.clear();
  }
}
