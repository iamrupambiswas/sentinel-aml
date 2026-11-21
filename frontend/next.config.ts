import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The console never talks to services directly: every API call goes through the gateway (GATEWAY_URL)
  poweredByHeader: false,
  // Self-contained server bundle for the Docker image (frontend/Dockerfile)
  output: "standalone",
  // The Simulation page uploads transaction CSVs through a server action; match the gateway's 50 MB upload limit
  experimental: { serverActions: { bodySizeLimit: "50mb" } },
};

export default nextConfig;
