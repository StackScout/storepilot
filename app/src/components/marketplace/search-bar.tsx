import { Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export function SearchBar({
  defaultValue = "",
  className,
}: {
  defaultValue?: string;
  className?: string;
}) {
  return (
    <form action="/search" method="GET" className={cn("relative w-full", className)}>
      <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
      <Input
        type="search"
        name="q"
        defaultValue={defaultValue}
        placeholder="Search products, stores, categories..."
        className="h-11 pl-9"
      />
    </form>
  );
}
