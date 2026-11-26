import { createSlice } from "@reduxjs/toolkit";

/** One message from GET /api/v1/alerts/stream (alert-case-service's AlertChangedEvent). */
export interface AlertChanged {
  alertId: string;
  accountId: string;
  ruleCode: string;
  change: "CREATED" | "UPDATED" | "STATUS_CHANGED";
  status: string | null;
}

export interface LiveState {
  /** true once the stream said "connected"; false while EventSource is (re)connecting */
  connected: boolean;
}

const initialState: LiveState = { connected: false };

const liveSlice = createSlice({
  name: "live",
  initialState,
  reducers: {
    streamOpened(state) {
      state.connected = true;
    },
    streamLost(state) {
      state.connected = false;
    },
  },
  selectors: {
    selectConnected: (s) => s.connected,
  },
});

export const { streamOpened, streamLost } = liveSlice.actions;
export const { selectConnected } = liveSlice.selectors;
export default liveSlice;
