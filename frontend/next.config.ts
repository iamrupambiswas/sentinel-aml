import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The console never talks to services directly: every API call goes through the gateway (GATEWAY_URL)
  poweredByHeader: false,
};

export default nextConfig;
