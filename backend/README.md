# IslandCart Backend

Spring Boot (Kotlin) API for [IslandCart](../app) — a multi-vendor
marketplace for Sri Lankan small businesses. This service is what the
frontend's mock `localStorage` service layer (`app/src/services/*.service.ts`)
gets pointed at once the real backend is wired up — see
[`docs/api-contracts.md`](../docs/api-contracts.md) for the full
endpoint contract this is implementing, and
[`docs/database-model.md`](../docs/database-model.md) for the schema
this is translating into JPA entities.

## Stack

Kotlin 2.3 · Spring Boot 4.1 · Spring Data JPA (Hibernate) · PostgreSQL ·
Flyway · Gradle (Kotlin DSL).

## Project structure

Package-by-feature, not package-by-layer — mirrors how the frontend groups
by domain rather than by technical concern:

```
src/main/kotlin/com/islandcart/backend/
├── common/       BaseEntity, ShippingDetails (shared value object),
│                 WireValueEnum (see below), ApiError, GlobalExceptionHandler,
│                 domain exceptions, JPA auditing config
├── store/        Store, StoreSettings, StoreCategory, StoreVerificationStatus,
│                 SellerType, StoreRepository
├── product/      Product, ProductImage, ProductStatus, ProductRepository,
│                 ProductSpecifications, ProductService, ProductController,
│                 ProductDtos — the one complete vertical slice (repository →
│                 service → controller → DTOs) other entities should follow
├── order/        Order, OrderItem, OrderTimelineEntry, OrderStatus,
│                 PaymentMethod, PaymentStatus, OrderRepository
│                 (entities + repository only — service/controller not built yet)
├── payout/       Payout, PayoutOrderRef, PayoutStatus, PayoutRepository
│                 (entities + repository only)
└── buyer/        Buyer, BuyerRepository (entities + repository only)
```

Each entity is a straight translation of the matching `src/types/*.ts`
interface in the frontend — field names, nullability, and (critically) wire
format all match on purpose, so the frontend's TypeScript types don't need to
change when it's pointed at this API instead of the mock.

### The "wire value" enum pattern

Every enum (`StoreCategory`, `ProductStatus`, `OrderStatus`, etc.) implements
`common.WireValue` and serializes/persists as the **exact string literal**
the frontend already uses — `"food-beverage"`, `"out-of-stock"` — not
Kotlin's default `.name` (`FOOD_BEVERAGE`). `WireValueEnumConverter` (JPA) and
`wireValueOf<T>()` (service-layer parsing, throws `IllegalArgumentException`
→ mapped to a 400) both live in `common/WireValueEnum.kt`. Follow this
pattern for any new enum rather than letting Kotlin's default enum
serialization leak into the API.

### Aggregate-root child entities

`OrderItem`/`OrderTimelineEntry` (children of `Order`) and `PayoutOrderRef`
(child of `Payout`) are JPA entities but deliberately have **no repository of
their own** — they're only ever read/written through their parent aggregate
root, same as the domain model implies. `product_id`/`order_id` on those
child tables are plain UUID columns, **not foreign keys** — an immutable
snapshot decoupled from the live row it references, matching
`database-model.md`'s explicit design intent (a product/order can be deleted
without corrupting historical records).

## Running locally

### 1. Database

This machine already has a system-wide PostgreSQL install in use by other
projects, so this project runs its own **isolated** Postgres via Homebrew on
a non-default port (5433) rather than sharing it:

```bash
brew services start postgresql@16
```

(Already configured to listen on port 5433 — see
`/opt/homebrew/var/postgresql@16/postgresql.conf`. The `islandcart` role/database
already exist locally.) A `docker-compose.yml` is also included as an
alternative if you'd rather containerize Postgres — same port (5433 on the
host) and credentials either way, so `application.yml`'s defaults work
unchanged with either option.

### 2. App

```bash
./gradlew bootRun
```

Flyway applies `src/main/resources/db/migration/V1__init_schema.sql`
automatically on startup — `spring.jpa.hibernate.ddl-auto` is `validate`,
never `update`; schema changes belong in a new migration file, not
Hibernate's auto-DDL.

```bash
curl http://localhost:8080/actuator/health
```

### Environment variables (all optional, sensible local defaults)

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/islandcart` |
| `DB_USERNAME` | `islandcart` |
| `DB_PASSWORD` | `islandcart` |
| `PORT` | `8080` |
| `SHOW_SQL` | `false` |

## What's built vs. what's next

**Built**: full schema (all entities from `database-model.md`, one Flyway
migration), and a complete reference vertical slice for `Product`
(`GET /api/products`, `GET /api/products/{id}`,
`GET /api/stores/{storeId}/products`, `POST/PATCH/DELETE` — matching
`api-contracts.md#products`, including the "stockQuantity 0 forces
out-of-stock" business rule enforced server-side).

**Not built yet** — entities/repositories exist, service+controller layers
don't:
- Store (create/list/verification-status workflow, matches `/admin`'s
  approve/reject flow)
- Order (checkout, status-transition state machine — currently **not**
  enforced anywhere, same gap flagged in `gaps-and-assumptions.md`)
- Payout (eligible-orders calculation, batch creation, admin release)
- Buyer (registration, email lookup, default-shipping upsert)
- Auth (no Spring Security yet at all — every endpoint above is
  unauthenticated; see the "Must have" list in `docs/roadmap.md`)
