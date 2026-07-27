"use client";

import { useState } from "react";
import { Check, Link2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface CopyLinkButtonProps {
  path?: string;
  className?: string;
  /** Compact icon-only variant for table row actions — falls back to the labeled button. */
  iconOnly?: boolean;
}

/**
 * Copies `path` to the clipboard with a brief visual + toast confirmation.
 * Accepts either an absolute URL or a site-relative path (resolved against
 * `window.location.origin` at click time, since Server Components rendering
 * this button don't have access to the request origin); defaults to the
 * current page's URL when omitted entirely.
 */
export function CopyLinkButton({ path, className, iconOnly = false }: CopyLinkButtonProps) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    const target = !path
      ? window.location.href
      : path.startsWith("http")
        ? path
        : `${window.location.origin}${path}`;
    try {
      await navigator.clipboard.writeText(target);
      setCopied(true);
      toast.success("Link copied to clipboard");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Couldn't copy the link — please copy it manually");
    }
  }

  if (iconOnly) {
    return (
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className={cn("size-8", className)}
        onClick={handleCopy}
        aria-label="Copy product link"
      >
        {copied ? <Check className="size-3.5" /> : <Link2 className="size-3.5" />}
      </Button>
    );
  }

  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      onClick={handleCopy}
      className={cn("gap-1.5", className)}
    >
      {copied ? <Check className="size-3.5" /> : <Link2 className="size-3.5" />}
      {copied ? "Copied" : "Copy link"}
    </Button>
  );
}
