"use client";

import {
  createContext,
  type PropsWithChildren,
  type ReactElement,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

export type ThemeMode = "system" | "light" | "dark";

type ThemeContextValue = {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
};

const ThemeContext = createContext<ThemeContextValue>({
  mode: "system",
  setMode: () => {},
});

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}

interface Matcher {
  matches: boolean;
  addEventListener(typ: "change", cb: () => void): void;
  removeEventListener(typ: "change", cb: () => void): void;
}

const defaultMatcher: Matcher = {
  matches: false,
  addEventListener() {},
  removeEventListener() {},
};

export default function ThemeProvider({
  children,
}: PropsWithChildren): ReactElement {
  const [theme, setTheme] = useState<boolean | undefined>(undefined);

  useEffect(() => {
    function listener(): void {
      const stored = window.localStorage.getItem("theme");
      if (stored === "dark") setTheme(true);
      else if (stored === "light") setTheme(false);
      else {
        window.localStorage.removeItem("theme");
        setTheme(undefined);
      }
    }
    listener();
    window.addEventListener("storage", listener);
    return () => window.removeEventListener("storage", listener);
  }, []);

  useEffect(() => {
    if (theme === undefined) window.localStorage.removeItem("theme");
    else if (theme) window.localStorage.setItem("theme", "dark");
    else window.localStorage.setItem("theme", "light");
  }, [theme]);

  const [matcher, setMatcher] = useState<Matcher>(defaultMatcher);
  useEffect(() => {
    setMatcher(window.matchMedia("(prefers-color-scheme: dark)"));
  }, []);

  const [dark, setDark] = useState(theme ?? matcher.matches);
  useEffect(() => {
    setDark(theme ?? matcher.matches);
    const cb = () => setDark(theme ?? matcher.matches);
    matcher.addEventListener("change", cb);
    return () => matcher.removeEventListener("change", cb);
  }, [matcher, theme]);

  useEffect(() => {
    if (dark) document.documentElement.classList.add("dark");
    else document.documentElement.classList.remove("dark");
    for (const meta of document.querySelectorAll('meta[name="theme-color"]')) {
      meta.setAttribute("content", dark ? "#171615" : "#fbfaf8");
    }
  }, [dark]);

  const setMode = useCallback((mode: ThemeMode) => {
    setTheme(mode === "system" ? undefined : mode === "dark");
  }, []);
  const mode: ThemeMode =
    theme === undefined ? "system" : theme ? "dark" : "light";

  const value = useMemo(() => ({ mode, setMode }), [mode, setMode]);

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}
