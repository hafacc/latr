import { onAuthStateChanged, type User } from "firebase/auth";
import { createContext } from "svelte";
import { auth, firebaseConfigured } from "./firebase";
import type { TodoStoreHolder } from "./store-holder";

/** The signed-in user and the account actions, with their failure messages. */
export class AuthState {
  user = $state.raw<User | null>(null);
  #holder: TodoStoreHolder;

  constructor(holder: TodoStoreHolder) {
    this.#holder = holder;

    $effect(() => {
      if (!firebaseConfigured()) return;
      return onAuthStateChanged(auth(), (user) => {
        this.user = user;
      });
    });

    // Reattach Firestore snapshot listener after tab wake / online — covers
    // WebChannel drops during long idle that would otherwise leave the
    // listener silent.
    $effect(() => {
      if (!this.user) return;
      const wake = () => {
        if (document.visibilityState !== "visible") return;
        holder.reattach();
      };
      document.addEventListener("visibilitychange", wake);
      window.addEventListener("online", wake);
      return () => {
        document.removeEventListener("visibilitychange", wake);
        window.removeEventListener("online", wake);
      };
    });
  }

  signIn = async (): Promise<void> => {
    if (!firebaseConfigured()) {
      alert(
        "Firebase web config needs an appId. Register a Web app in the Firebase console and paste the appId into web/src/lib/utils/firebase.ts.",
      );
      return;
    }
    try {
      await this.#holder.signIn();
    } catch (e) {
      console.error(e);
      alert(
        "Sign-in didn't complete, or syncing this device's edits failed — nothing was lost, but edits and deletes made while signed out may not have synced. Sign out and back in to retry.",
      );
    }
  };

  signOut = async (force: boolean): Promise<"done" | "pending"> => {
    try {
      return await this.#holder.signOut(force);
    } catch (e) {
      console.error(e);
      return "done";
    }
  };

  deleteAccount = async (keep: boolean): Promise<void> => {
    try {
      await this.#holder.deleteAccount(keep);
    } catch (e) {
      console.error(e);
      alert(
        "Account deletion didn't finish — some remote data may already be gone. Please try again.",
      );
    }
  };
}

export const [getAuthState, setAuthState] = createContext<AuthState>();
