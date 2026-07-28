"use client";

import Link from "next/link";
import { Store } from "lucide-react";
import { cn } from "@/lib/utils";
import { usePlatformConfig } from "@/hooks/use-platform-config";

export function Logo({ href = "/", className }: { href?: string; className?: string }) {
  const { name } = usePlatformConfig();
  return (
    <Link
      href={href}
      className={cn("flex items-center gap-2 font-semibold tracking-tight", className)}
    >
      <span className="bg-primary text-primary-foreground flex size-8 items-center justify-center rounded-lg">
        <Store className="size-4.5" />
      </span>
      <span className="text-lg">{name}</span>
    </Link>
  );
}
