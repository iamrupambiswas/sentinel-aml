import { describe, expect, it } from "vitest";
import { streamLost, streamOpened } from "./liveSlice";
import { makeStore } from "./store";
import { DEFAULT_MS, MAX_TOASTS, toastDismissed, toastRemoved, toastShown } from "./toastSlice";
import { navClosed, navOpened, themeChosen } from "./uiSlice";

describe("live slice", () => {
  it("tracks the stream connection", () => {
    const store = makeStore();
    expect(store.getState().live.connected).toBe(false);
    store.dispatch(streamOpened());
    expect(store.getState().live.connected).toBe(true);
    store.dispatch(streamLost());
    expect(store.getState().live.connected).toBe(false);
  });
});

describe("toast slice", () => {
  it("keeps the newest first, at most MAX_TOASTS, each with its own id", () => {
    const store = makeStore();
    for (let i = 1; i <= MAX_TOASTS + 2; i++) store.dispatch(toastShown({ tone: "info", title: `t${i}` }));
    const items = store.getState().toasts.items;
    expect(items.map((t) => t.title)).toEqual(["t6", "t5", "t4", "t3"]);
    expect(new Set(items.map((t) => t.id)).size).toBe(MAX_TOASTS);
  });

  it("defaults the duration by tone, and keeps an explicit one (0 = sticky)", () => {
    const store = makeStore();
    store.dispatch(toastShown({ tone: "danger", title: "e" }));
    store.dispatch(toastShown({ tone: "success", title: "s", duration: 0 }));
    const [sticky, error] = store.getState().toasts.items;
    expect(error.duration).toBe(DEFAULT_MS.danger);
    expect(sticky.duration).toBe(0);
  });

  it("dismissing starts the exit; removing deletes only that notice", () => {
    const store = makeStore();
    store.dispatch(toastShown({ tone: "success", title: "a" }));
    store.dispatch(toastShown({ tone: "success", title: "b" }));
    const [b, a] = store.getState().toasts.items;

    store.dispatch(toastDismissed(b.id));
    expect(store.getState().toasts.items.map((t) => [t.title, t.leaving])).toEqual([["b", true], ["a", false]]);

    store.dispatch(toastRemoved(b.id));
    expect(store.getState().toasts.items.map((t) => t.id)).toEqual([a.id]);
  });

  it("ignores ids it doesn't know", () => {
    const store = makeStore();
    store.dispatch(toastShown({ tone: "info", title: "a" }));
    store.dispatch(toastDismissed("nope"));
    store.dispatch(toastRemoved("nope"));
    expect(store.getState().toasts.items).toHaveLength(1);
  });
});

describe("ui slice", () => {
  it("starts from the preloaded theme and records changes", () => {
    const store = makeStore({ ui: { theme: "dark", navOpen: false } });
    expect(store.getState().ui.theme).toBe("dark");
    store.dispatch(themeChosen("system")); // the DOM/cookie listener skips itself outside a browser
    expect(store.getState().ui.theme).toBe("system");
  });

  it("opens and closes the mobile drawer", () => {
    const store = makeStore();
    store.dispatch(navOpened());
    expect(store.getState().ui.navOpen).toBe(true);
    store.dispatch(navClosed());
    expect(store.getState().ui.navOpen).toBe(false);
  });
});
