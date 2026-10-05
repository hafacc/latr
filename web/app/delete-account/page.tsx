import type { Metadata } from "next";
import type { ReactElement } from "react";
import DeleteAccount from "../../components/delete-account";

export const metadata: Metadata = { title: "Delete your account · Latr" };

export default function DeleteAccountPage(): ReactElement {
  return <DeleteAccount />;
}
