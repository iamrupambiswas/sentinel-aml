import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The console never talks to services directly: every API call goes through the gateway (GATEWAY_URL)
  poweredByHeader: false,
  // Self-contained server bundle for the Docker image (frontend/Dockerfile)
  output: "standalone",
};

export default nextConfig;
