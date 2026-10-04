"use client";

import { type FirebaseApp, getApps, initializeApp } from "firebase/app";
import { type Auth, getAuth } from "firebase/auth";
import {
  clearIndexedDbPersistence,
  type Firestore,
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  terminate,
} from "firebase/firestore";

// Public Firebase web config; access is enforced by Firestore rules. Replace when forking.
const firebaseConfig = {
  apiKey: "AIzaSyBmNn_yskTFD7Fk81mfSg3XYwSP-gMWRKI",
  authDomain: "auth.latr.hafa.cc",
  projectId: "hafaio-latr",
  storageBucket: "hafaio-latr.firebasestorage.app",
  messagingSenderId: "598050986641",
  appId: "1:598050986641:web:03d16cae09ba78f079fd30",
};

let cachedApp: FirebaseApp | null = null;
let cachedAuth: Auth | null = null;
let cachedDb: Firestore | null = null;

function app(): FirebaseApp {
  if (cachedApp) return cachedApp;
  cachedApp = getApps()[0] ?? initializeApp(firebaseConfig);
  return cachedApp;
}

export function auth(): Auth {
  if (!cachedAuth) cachedAuth = getAuth(app());
  return cachedAuth;
}

export function db(): Firestore {
  if (cachedDb) return cachedDb;
  try {
    cachedDb = initializeFirestore(app(), {
      localCache: persistentLocalCache(),
    });
  } catch (e) {
    // Firestore was already initialized (e.g. HMR reusing the app instance) —
    // fall back to the existing handle so we don't crash.
    console.warn("firestore: reusing existing instance", e);
    cachedDb = getFirestore(app());
  }
  return cachedDb;
}

/** Ends the Firestore instance and wipes its on-device cache, unsent writes included; the next [db] call starts a fresh one. */
export async function clearDbCache(): Promise<void> {
  const ended = cachedDb;
  if (!ended) return;
  cachedDb = null;
  await terminate(ended);
  await clearIndexedDbPersistence(ended);
}

export function firebaseConfigured(): boolean {
  return firebaseConfig.appId !== "";
}
