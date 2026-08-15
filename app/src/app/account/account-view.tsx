"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CalendarClock, ChevronRight, Heart, LogOut, Mail, MapPin, Package, Search, Star, Trash2, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/empty-state";
import { OrderStatusBadge } from "@/components/shared/order-status-badge";
import { BookingStatusBadge } from "@/components/shared/booking-status-badge";
import { PriceDisplay } from "@/components/shared/price-display";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { AddressFormDialog } from "@/components/marketplace/address-form-dialog";
import { useSignOut } from "@/hooks/use-sign-out";
import { formatDate } from "@/lib/format";
import {
  ordersService,
  buyersService,
  bookingsService,
  addressesService,
  productsService,
  savedSearchesService,
  messagingService,
} from "@/services";

export function AccountView() {
  const signOut = useSignOut();
  const queryClient = useQueryClient();

  const { data: buyer } = useQuery({
    queryKey: ["buyer", "me"],
    queryFn: () => buyersService.getCurrentBuyer(),
  });

  const { data: addresses, isLoading: isAddressesLoading } = useQuery({
    queryKey: ["addresses"],
    queryFn: () => addressesService.listAddresses(),
  });

  const { data: conversations } = useQuery({
    queryKey: ["conversations", "me"],
    queryFn: () => messagingService.listMyConversations(),
  });
  const messagesUnreadCount = conversations?.reduce((sum, c) => sum + c.unreadCount, 0) ?? 0;

  const setDefaultMutation = useMutation({
    mutationFn: (id: string) => addressesService.setDefaultAddress(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["addresses"] });
      toast.success("Default address updated");
    },
    onError: () => toast.error("Couldn't update your default address. Please try again."),
  });

  const deleteAddressMutation = useMutation({
    mutationFn: (id: string) => addressesService.deleteAddress(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["addresses"] });
      toast.success("Address removed");
    },
    onError: () => toast.error("Couldn't remove this address. Please try again."),
  });

  const { data: orders, isLoading } = useQuery({
    queryKey: ["orders", "me"],
    queryFn: () => ordersService.listMyOrders(),
  });

  const { data: bookings, isLoading: isBookingsLoading } = useQuery({
    queryKey: ["bookings", "me"],
    queryFn: () => bookingsService.listMyBookings(),
  });

  const { data: wishlist, isLoading: isWishlistLoading } = useQuery({
    queryKey: ["wishlist"],
    queryFn: () => productsService.listMyWishlist(),
  });

  const { data: savedSearches, isLoading: isSavedSearchesLoading } = useQuery({
    queryKey: ["saved-searches"],
    queryFn: () => savedSearchesService.listSavedSearches(),
  });

  const deleteSavedSearchMutation = useMutation({
    mutationFn: (id: string) => savedSearchesService.deleteSavedSearch(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["saved-searches"] });
      toast.success("Saved search removed");
    },
    onError: () => toast.error("Couldn't remove this saved search. Please try again."),
  });

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">My account</h1>
        <Button type="button" variant="outline" size="sm" onClick={() => signOut()}>
          <LogOut className="size-3.5" /> Sign out
        </Button>
      </div>

      <Card>
        <CardContent className="flex items-start gap-3">
          <span className="bg-primary/10 text-primary flex size-10 shrink-0 items-center justify-center rounded-full">
            <User className="size-5" />
          </span>
          <div>
            <p className="font-medium">{buyer?.name}</p>
            <p className="text-muted-foreground text-sm">{buyer?.email}</p>
            {buyer?.phone ? <p className="text-muted-foreground text-sm">{buyer.phone}</p> : null}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Link href="/account/messages" className="hover:bg-accent/50 -m-6 flex items-center justify-between gap-3 p-6">
            <div className="flex items-center gap-2">
              <Mail className="text-muted-foreground size-4" />
              <h2 className="font-semibold">Messages</h2>
              {messagesUnreadCount > 0 ? (
                <span className="bg-primary text-primary-foreground rounded-full px-2 py-0.5 text-xs font-medium">
                  {messagesUnreadCount}
                </span>
              ) : null}
            </div>
            <ChevronRight className="text-muted-foreground size-4" />
          </Link>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <MapPin className="text-muted-foreground size-4" />
              <h2 className="font-semibold">Saved addresses</h2>
            </div>
            <AddressFormDialog />
          </div>
          {isAddressesLoading ? (
            <TableRowSkeleton columns={1} />
          ) : !addresses || addresses.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              No saved addresses yet — add one now, or check out once and it&apos;ll be saved for
              next time.
            </p>
          ) : (
            <div className="divide-y">
              {addresses.map((address) => (
                <div key={address.id} className="flex items-start justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium">{address.label || address.shipping.fullName}</p>
                      {address.isDefault ? (
                        <span className="bg-primary/10 text-primary flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium">
                          <Star className="size-2.5 fill-current" /> Default
                        </span>
                      ) : null}
                    </div>
                    <p className="text-muted-foreground text-sm">
                      {address.shipping.addressLine1}, {address.shipping.city}, {address.shipping.state}{" "}
                      {address.shipping.postalCode}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1.5">
                    {!address.isDefault ? (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        disabled={setDefaultMutation.isPending}
                        onClick={() => setDefaultMutation.mutate(address.id)}
                      >
                        Set default
                      </Button>
                    ) : null}
                    <AddressFormDialog address={address} />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="text-destructive size-8"
                      disabled={deleteAddressMutation.isPending}
                      onClick={() => deleteAddressMutation.mutate(address.id)}
                    >
                      <Trash2 className="size-3.5" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <Package className="text-muted-foreground size-4" />
            <h2 className="font-semibold">Order history</h2>
          </div>

          {isLoading ? (
            <div className="space-y-2">
              <TableRowSkeleton columns={1} />
              <TableRowSkeleton columns={1} />
            </div>
          ) : !orders || orders.length === 0 ? (
            <EmptyState
              icon={Package}
              title="No orders yet"
              description="Orders you place while signed in will show up here."
              action={
                <Button render={<Link href="/search" />} size="sm">
                  Browse products
                </Button>
              }
            />
          ) : (
            <div className="divide-y">
              {orders.map((order) => (
                <Link
                  key={order.id}
                  href={`/orders/${order.id}`}
                  className="hover:bg-accent/50 -mx-4 flex items-center justify-between gap-3 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{order.orderNumber}</p>
                    <p className="text-muted-foreground text-xs">
                      {order.storeName} · {formatDate(order.createdAt)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <PriceDisplay price={order.total} size="sm" />
                    <OrderStatusBadge status={order.status} />
                  </div>
                </Link>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <CalendarClock className="text-muted-foreground size-4" />
            <h2 className="font-semibold">Booking history</h2>
          </div>

          {isBookingsLoading ? (
            <div className="space-y-2">
              <TableRowSkeleton columns={1} />
              <TableRowSkeleton columns={1} />
            </div>
          ) : !bookings || bookings.length === 0 ? (
            <EmptyState
              icon={CalendarClock}
              title="No bookings yet"
              description="Appointments you book while signed in will show up here."
            />
          ) : (
            <div className="divide-y">
              {bookings.map((booking) => (
                <Link
                  key={booking.id}
                  href={`/bookings/${booking.id}`}
                  className="hover:bg-accent/50 -mx-4 flex items-center justify-between gap-3 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{booking.serviceName}</p>
                    <p className="text-muted-foreground text-xs">
                      {booking.storeName} · {formatDate(booking.scheduledStart)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <PriceDisplay price={booking.total} size="sm" />
                    <BookingStatusBadge status={booking.status} />
                  </div>
                </Link>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <Heart className="text-muted-foreground size-4" />
            <h2 className="font-semibold">Wishlist</h2>
          </div>

          {isWishlistLoading ? (
            <div className="space-y-2">
              <TableRowSkeleton columns={1} />
              <TableRowSkeleton columns={1} />
            </div>
          ) : !wishlist || wishlist.length === 0 ? (
            <EmptyState
              icon={Heart}
              title="Your wishlist is empty"
              description="Save products you like and they'll show up here."
              action={
                <Button render={<Link href="/search" />} size="sm">
                  Browse products
                </Button>
              }
            />
          ) : (
            <div className="divide-y">
              {wishlist.map((product) => (
                <Link
                  key={product.id}
                  href={`/stores/${product.storeSlug}/products/${product.slug}`}
                  className="hover:bg-accent/50 -mx-4 flex items-center justify-between gap-3 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="line-clamp-1 text-sm font-medium">{product.name}</p>
                    <p className="text-muted-foreground text-xs">{product.storeName}</p>
                  </div>
                  <PriceDisplay price={product.price} compareAtPrice={product.compareAtPrice} size="sm" />
                </Link>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <Search className="text-muted-foreground size-4" />
            <h2 className="font-semibold">Saved searches</h2>
          </div>

          {isSavedSearchesLoading ? (
            <div className="space-y-2">
              <TableRowSkeleton columns={1} />
              <TableRowSkeleton columns={1} />
            </div>
          ) : !savedSearches || savedSearches.length === 0 ? (
            <EmptyState
              icon={Search}
              title="No saved searches yet"
              description="Save a search from the search page to quickly re-run it later."
            />
          ) : (
            <div className="divide-y">
              {savedSearches.map((savedSearch) => (
                <div key={savedSearch.id} className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <Link href={`/search?${savedSearch.queryString}`} className="min-w-0 flex-1">
                    <p className="text-sm font-medium">{savedSearch.name}</p>
                    <p className="text-muted-foreground text-xs">{formatDate(savedSearch.createdAt)}</p>
                  </Link>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="text-destructive size-8 shrink-0"
                    disabled={deleteSavedSearchMutation.isPending}
                    onClick={() => deleteSavedSearchMutation.mutate(savedSearch.id)}
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
