import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export type ThemeChoice = "system" | "light" | "dark";

export interface UiState {
  /** preloaded from the sentinel-theme cookie by the root layout */
  theme: ThemeChoice;
  /** mobile navigation drawer */
  navOpen: boolean;
}

export const initialUiState: UiState = { theme: "system", navOpen: false };

const uiSlice = createSlice({
  name: "ui",
  initialState: initialUiState,
  reducers: {
    themeChosen(state, action: PayloadAction<ThemeChoice>) {
      state.theme = action.payload;
    },
    navOpened(state) {
      state.navOpen = true;
    },
    navClosed(state) {
      state.navOpen = false;
    },
  },
  selectors: {
    selectTheme: (s) => s.theme,
    selectNavOpen: (s) => s.navOpen,
  },
});

export const { themeChosen, navOpened, navClosed } = uiSlice.actions;
export const { selectTheme, selectNavOpen } = uiSlice.selectors;
export default uiSlice;
