import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const email = process.argv[2];
if (!email) {
  console.error("usage: bun scripts/set-admin.ts <email>");
  process.exit(1);
}

initializeApp({ credential: applicationDefault(), projectId: "hafaio-latr" });
const auth = getAuth();
const user = await auth.getUserByEmail(email);
await auth.setCustomUserClaims(user.uid, {
  ...(user.customClaims ?? {}),
  admin: true,
});
console.log(`${email} (${user.uid}) is now an admin; it applies on their next ID token refresh.`);
