import { combineSlices, configureStore, createListenerMiddleware } from "@reduxjs/toolkit";
import liveSlice from "./liveSlice";
import toastSlice from "./toastSlice";
import uiSlice, { themeChosen } from "./uiSlice";

/**
 * Client-side state only: the live alert stream, notices and UI preferences. Server data (alerts, cases,
 * rules…) stays in Server Components, which fetch it with the user's token on the server; the browser never
 * holds the token, so it could not call the gateway itself.
 */
const rootReducer = combineSlices(liveSlice, toastSlice, uiSlice);
export type RootState = ReturnType<typeof rootReducer>;

/** A new store per browser tab (and per server render): never a module-level singleton, which SSR would share between users. */
export function makeStore(preloadedState?: Partial<RootState>) {
  const listener = createListenerMiddleware();

  // Theme side effects live here, not in the reducer: <html data-theme> now, and a cookie so the server
  // renders the right theme on the next request (no flash)
  listener.startListening({
    actionCreator: themeChosen,
    effect: (action) => {
      if (typeof document === "undefined") return;
      const root = document.documentElement;
      if (action.payload === "system") root.removeAttribute("data-theme");
      else root.setAttribute("data-theme", action.payload);
      document.cookie = `sentinel-theme=${action.payload}; path=/; max-age=31536000; samesite=lax`;
    },
  });

  return configureStore({
    reducer: rootReducer,
    preloadedState,
    middleware: (getDefault) => getDefault().prepend(listener.middleware),
  });
}

export type AppStore = ReturnType<typeof makeStore>;
export type AppDispatch = AppStore["dispatch"];
