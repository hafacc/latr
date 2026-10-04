import type { Metadata } from "next";
import type { ReactElement, ReactNode } from "react";

export const metadata: Metadata = { title: "Privacy · latr" };

function Section({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}): ReactElement {
  return (
    <section className="mt-7">
      <h2 className="m-0 text-base font-semibold text-text">{title}</h2>
      <p className="mt-1.5 mb-0">{children}</p>
    </section>
  );
}

export default function Privacy(): ReactElement {
  return (
    <main className="mx-auto max-w-[620px] px-5 py-12 text-[15px] leading-relaxed text-text-secondary">
      <a href="../" className="text-sm text-accent hover:underline">
        latr
      </a>
      <h1 className="mt-3 mb-0 text-2xl font-semibold text-text">Privacy</h1>
      <Section title="Not signed in">
        Your todos stay on your device. Nothing is sent anywhere.
      </Section>
      <Section title="Signed in with Google">
        Your todos and a count of the snooze times you pick are stored in Google
        Firebase under your Google account, so they sync between your devices.
        Google gives latr your name, email and profile photo, shown in the
        account menu. Other users can't read your data. Signing out removes it
        from the device you sign out on.
      </Section>
      <Section title="Improve snooze suggestions">
        Off unless you turn it on. Each snooze then adds one to a shared tally
        of which menu position was picked. It holds no todo text, no times, and
        nothing that identifies you.
      </Section>
      <Section title="What latr doesn't do">
        No ads, no analytics, no selling or sharing of data. Google handles
        sign-in and storage.
      </Section>
      <Section title="Delete your account">
        Open latr on the web or on Android, open the account menu, and choose
        Delete account. Your todos, snooze counts and account are removed right
        away. To have it deleted for you, email support@latr.hafa.cc.
      </Section>
      <p className="mt-7 mb-0">Deleting a single todo erases its text.</p>
    </main>
  );
}
