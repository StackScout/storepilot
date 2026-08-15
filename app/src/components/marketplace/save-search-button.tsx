"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Bookmark, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { useAuthSession } from "@/hooks/use-auth-session";
import { savedSearchesService } from "@/services";

/** Saves the current /search filter state (as its raw query string) under a name for quick re-run later, from the buyer's account page. */
export function SaveSearchButton({ queryString }: { queryString: string }) {
  const { session } = useAuthSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");

  const mutation = useMutation({
    mutationFn: () => savedSearchesService.createSavedSearch({ name: name.trim(), queryString }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["saved-searches"] });
      toast.success("Search saved");
      setOpen(false);
      setName("");
    },
    onError: () => toast.error("Couldn't save this search. Please try again."),
  });

  if (!session.signedIn || session.role !== "buyer") {
    return (
      <Button variant="outline" size="sm" render={<Link href="/login" />}>
        <Bookmark className="size-3.5" /> Save search
      </Button>
    );
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button type="button" variant="outline" size="sm" />}>
        <Bookmark className="size-3.5" /> Save search
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Save this search</DialogTitle>
        </DialogHeader>
        <div className="space-y-1.5">
          <Label htmlFor="savedSearchName">Name</Label>
          <Input
            id="savedSearchName"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Jewelry under $50"
            autoFocus
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button disabled={!name.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
