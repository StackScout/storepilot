import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Minimal self-contained server bundle (.next/standalone) for the Docker
  // image — see app/Dockerfile and infra/docker/docker-compose.prod.yml.
  output: "standalone",
  images: {
    // Product/store images and documents come from the backend's own origin
    // (local disk) or an S3 presigned URL (aws profile) — both hosts are
    // only known at runtime, and the backend/S3 endpoint is frequently a
    // private/internal address the Next.js image-optimizer proxy can't
    // reach anyway (it blocks fetches that resolve to a private IP as an
    // SSRF guard). Skipping optimization sends src straight to the browser
    // instead, which also makes remotePatterns unnecessary.
    unoptimized: true,
  },
};

export default nextConfig;
