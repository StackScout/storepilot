import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Minimal self-contained server bundle (.next/standalone) for the Docker
  // image — see app/Dockerfile and infra/docker/docker-compose.prod.yml.
  output: "standalone",
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "picsum.photos",
        port: "",
        pathname: "/**",
        search: "",
      },
    ],
  },
};

export default nextConfig;
