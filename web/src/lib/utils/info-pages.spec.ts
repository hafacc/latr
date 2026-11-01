import { describe, expect, test } from "bun:test";
import { PRIVACY, textParts } from "./info-pages";

describe("textParts", () => {
  test("splits links out of the text", () => {
    expect(textParts("Use the [page](a/), then go.")).toEqual([
      { text: "Use the " },
      { text: "page", href: "a/" },
      { text: ", then go." },
    ]);
  });

  test("keeps text without links whole", () => {
    expect(textParts("plain")).toEqual([{ text: "plain" }]);
  });

  test("the privacy page links to account deletion", () => {
    const links = PRIVACY.sections
      .flatMap((section) => textParts(section.body))
      .filter((part) => part.href);
    expect(links).toEqual([
      { text: "delete account page", href: "delete-account/" },
    ]);
  });
});
