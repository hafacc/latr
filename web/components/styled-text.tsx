import type { ReactElement, ReactNode } from "react";
import { type StyledNode, styledTree } from "../utils/inline-style";

// Spans with the editor's classes, not <strong>/<em>, so a row looks the same at rest and while editing.
function render(nodes: StyledNode[]): ReactNode[] {
  return nodes.map((node, i) => {
    const key = `${i}`;
    if (node.kind === "text") return node.text;
    if (node.kind === "marker") {
      return (
        <span key={key} className="md-marker">
          {node.text}
        </span>
      );
    }
    return (
      <span
        key={key}
        className={node.style === "bold" ? "md-bold" : "md-italic"}
      >
        {render(node.children)}
      </span>
    );
  });
}

/** Renders `*bold*` / `_italic_` with the markers kept, dimmed. */
export default function StyledText({ text }: { text: string }): ReactElement {
  return <>{render(styledTree(text))}</>;
}
