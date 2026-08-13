"use client";

import Link from "next/link";
import { ChevronDown, LogOut } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
// getStoreInitials/getStoreAvatarColor operate on any string, not just
// store names — reused here rather than duplicating the same
// initials-circle logic for user accounts.
import { getStoreAvatarColor, getStoreInitials } from "@/lib/store-avatar";
import { useSignOut } from "@/hooks/use-sign-out";
import { cn } from "@/lib/utils";

interface UserAccountMenuProps {
  name: string;
  email?: string;
  /** Omit for account types with no dedicated profile/settings page (e.g. admin today). */
  profileLink?: { href: string; label: string; icon: LucideIcon };
  /** Each account type has its own sign-in page — buyer/seller/admin are separate logins. */
  signOutRedirect: string;
  /**
   * "compact": avatar-only trigger, no name/chevron — fits the marketplace
   * header's icon-row. "labeled": avatar + name + chevron — fits a
   * dashboard/admin topbar's roomier top-right corner. Defaults to "labeled",
   * the more common case (both dashboard and admin use it).
   */
  variant?: "compact" | "labeled";
  className?: string;
}

/** The top-right account dropdown shared by the marketplace header, seller dashboard topbar, and admin topbar — same shape (avatar, name/email, profile link, sign out) everywhere, only the content/redirect differ per account type. */
export function UserAccountMenu({
  name,
  email,
  profileLink,
  signOutRedirect,
  variant = "labeled",
  className,
}: UserAccountMenuProps) {
  const signOut = useSignOut(signOutRedirect);
  const initials = getStoreInitials(name);
  const color = getStoreAvatarColor(name);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            aria-label={`Account: ${name}`}
            className={cn(
              variant === "compact" ? "size-9 rounded-full p-0" : "h-auto gap-2 px-2 py-1.5",
              className,
            )}
          />
        }
      >
        <span
          className="flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-white"
          style={{ backgroundColor: color }}
          aria-hidden="true"
        >
          {initials}
        </span>
        {variant === "labeled" ? (
          <>
            <span className="max-w-32 truncate text-sm font-medium">{name}</span>
            <ChevronDown className="text-muted-foreground size-3.5 shrink-0" />
          </>
        ) : null}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="space-y-0.5">
            <p className="truncate text-sm font-medium">{name}</p>
            {email ? <p className="text-muted-foreground truncate text-xs font-normal">{email}</p> : null}
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        {profileLink ? (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem render={<Link href={profileLink.href} />}>
              <profileLink.icon className="size-3.5" /> {profileLink.label}
            </DropdownMenuItem>
          </>
        ) : null}
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onClick={() => signOut()}>
          <LogOut className="size-3.5" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
