"use client";

import { useRef, type ReactNode } from "react";
import { Provider } from "react-redux";
import { makeStore, type AppStore } from "./store";
import { initialUiState, type ThemeChoice } from "./uiSlice";

/** Creates this tab's store once, preloaded with what the server already knows (the theme cookie). */
export function StoreProvider({ theme, children }: { theme: ThemeChoice; children: ReactNode }) {
  const store = useRef<AppStore | null>(null);
  if (!store.current) store.current = makeStore({ ui: { ...initialUiState, theme } });
  return <Provider store={store.current}>{children}</Provider>;
}
