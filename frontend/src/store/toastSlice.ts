import { createSlice, nanoid, type PayloadAction } from "@reduxjs/toolkit";

/** Colour and icon of a notice. Same meaning as everywhere else: danger = red, warn = amber, ok = green. */
export type ToastTone = "success" | "danger" | "warn" | "info" | "brand";

export interface ToastInput {
  tone: ToastTone;
  title: string;
  message?: string;
  /** makes the whole notice a link */
  href?: string;
  /** ms before it closes by itself; 0 = stays until closed. Defaults per tone (DEFAULT_MS). */
  duration?: number;
}

export interface Toast extends Required<Omit<ToastInput, "message" | "href">> {
  id: string;
  message?: string;
  href?: string;
  /** playing its exit animation; removed when that ends */
  leaving: boolean;
}

export interface ToastState {
  /** newest first */
  items: Toast[];
}

export const MAX_TOASTS = 4;
/** Errors stay longest: they usually need reading. Hovering a notice pauses its countdown. */
export const DEFAULT_MS: Record<ToastTone, number> = { success: 5000, info: 6000, brand: 8000, warn: 8000, danger: 10000 };

const initialState: ToastState = { items: [] };

const toastSlice = createSlice({
  name: "toasts",
  initialState,
  reducers: {
    toastShown: {
      reducer(state, action: PayloadAction<Toast>) {
        state.items = [action.payload, ...state.items].slice(0, MAX_TOASTS);
      },
      // id made here, so the reducer stays pure
      prepare: (t: ToastInput) => ({
        payload: { ...t, id: nanoid(), duration: t.duration ?? DEFAULT_MS[t.tone], leaving: false },
      }),
    },
    /** starts the exit animation; the Toaster removes it when the animation ends */
    toastDismissed(state, action: PayloadAction<string>) {
      const t = state.items.find((x) => x.id === action.payload);
      if (t) t.leaving = true;
    },
    toastRemoved(state, action: PayloadAction<string>) {
      state.items = state.items.filter((x) => x.id !== action.payload);
    },
  },
  selectors: {
    selectToasts: (s) => s.items,
  },
});

export const { toastShown, toastDismissed, toastRemoved } = toastSlice.actions;
export const { selectToasts } = toastSlice.selectors;
export default toastSlice;
