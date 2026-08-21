package com.storepilot.backend.common

import com.storepilot.backend.buyer.Address
import com.storepilot.backend.buyer.AddressRepository
import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.buyer.BuyerRepository
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderItem
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.OrderTimelineEntry
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.payout.Payout
import com.storepilot.backend.payout.PayoutSourceRef
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutStatus
import com.storepilot.backend.product.Product
import com.storepilot.backend.product.ProductImage
import com.storepilot.backend.product.ProductRepository
import com.storepilot.backend.product.ProductStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerRepository
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private fun d(dateOnly: String): Instant = LocalDate.parse(dateOnly).atStartOfDay(ZoneOffset.UTC).toInstant()
private fun dt(offsetDateTime: String): Instant = OffsetDateTime.parse(offsetDateTime).toInstant()

private data class SeedStore(
    val key: String,
    val slug: String,
    val name: String,
    val tagline: String,
    val description: String,
    val category: StoreCategory,
    val city: String,
    val state: String,
    val whatsapp: String,
    val rating: Double,
    val reviewCount: Int,
    val productCount: Int,
    val isVerified: Boolean,
    val joinedAt: String,
    val followerCount: Int,
)

private data class SeedSeller(
    val storeKey: String,
    val cognitoSub: String,
    val email: String,
    val name: String,
)

private data class SeedProduct(
    val storeKey: String,
    val name: String,
    val slug: String,
    val description: String,
    val imageSeed: String,
    val category: StoreCategory,
    val price: Int,
    val compareAtPrice: Int?,
    val stockQuantity: Int,
    val status: ProductStatus,
    val sku: String,
    val rating: Double,
    val reviewCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Translates demo marketplace content into real rows on first boot (only runs
 * against an empty `stores` table, so it's safe to leave enabled — restarts
 * against an already-seeded database are a no-op).
 *
 * Demo content is Australian — AU is the near-term deployment target while
 * the Sri Lanka launch sits on hold pending business registration (see
 * PlatformProperties.kt) — not a faithful port of the original Sri Lankan
 * mock catalogue. Payment methods are deliberately COD/bank-transfer only:
 * PayHere (PaymentMethod.PAYHERE) is a Sri Lanka-specific gateway with no AU
 * equivalent wired up yet (an AU deployment needs Stripe Connect — not
 * built in this pass), so no seed order uses it.
 *
 * StoreSettings.driverLicenceNumber/abn are the seller-verification fields
 * (renamed from an earlier Sri Lanka NIC/Business Registration Number model
 * now that this platform is AU-only) — populated here with plausible
 * placeholder values.
 *
 * Money literals below (SeedProduct.price/compareAtPrice, order() call
 * sites' subtotal/item unitPrice, seedPayout's amounts) are written as
 * plain whole dollars for readability and converted to cents (this
 * codebase's actual storage unit — see Product.price's doc comment) right
 * where each entity is constructed, not here.
 */
@Component
class DataSeeder(
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val productRepository: ProductRepository,
    private val buyerRepository: BuyerRepository,
    private val addressRepository: AddressRepository,
    private val orderRepository: OrderRepository,
    private val payoutRepository: PayoutRepository,
    private val sellerRepository: SellerRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val platformProperties: PlatformProperties,
    private val platformSettingsRepository: PlatformSettingsRepository,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(DataSeeder::class.java)

    @Transactional
    override fun run(vararg args: String) {
        seedPlatformSettingsIfMissing()

        if (storeRepository.count() > 0) {
            log.info("Seed skipped — stores table already has data.")
            return
        }
        log.info("Seeding database from demo marketplace data...")

        val sellers = seedSellers()
        val storeIds = seedStores(sellers)
        seedStoreSettings(storeIds.getValue("store-01"))
        val productIds = seedProducts(storeIds)
        seedBuyer()
        val orderIds = seedOrders(storeIds.getValue("store-01"), productIds)
        seedPayout(storeIds.getValue("store-01"), orderIds.getValue("order-1004"))

        log.info(
            "Seed complete: {} stores, {} products, {} orders, 1 buyer, 1 payout.",
            storeIds.size,
            productIds.size,
            orderIds.size,
        )
    }

    /**
     * The single platform_settings row — inserted once from PlatformProperties'
     * bootstrap env-var values (see its doc comment); a no-op on every later
     * boot. This is what makes the DB (not application.yml) the running
     * app's actual config source, and what an operator would edit directly
     * to reconfigure a deployment without rebuilding/redeploying.
     */
    private fun seedPlatformSettingsIfMissing() {
        if (platformSettingsRepository.count() > 0) return
        platformSettingsRepository.save(
            PlatformSettings(
                name = platformProperties.name,
                tagline = platformProperties.tagline,
                countryName = platformProperties.countryName,
                countryCode = platformProperties.countryCode,
                currencyCode = platformProperties.currencyCode,
                currencySymbol = platformProperties.currencySymbol,
                currencyLocale = platformProperties.currencyLocale,
                platformFeePercent = platformProperties.platformFeePercent,
                flatShippingFee = platformProperties.flatShippingFee,
                proMonthlyPriceCents = platformProperties.proMonthlyPriceCents,
                defaultCodEnabled = platformProperties.defaultCodEnabled,
                defaultOnlinePaymentEnabled = platformProperties.defaultOnlinePaymentEnabled,
                defaultBankTransferEnabled = platformProperties.defaultBankTransferEnabled,
                supportEmail = platformProperties.supportEmail,
                companyLocation = platformProperties.companyLocation,
                timezone = platformProperties.timezone,
                returnWindowDays = platformProperties.returnWindowDays,
            ),
        )
        log.info("Seeded platform_settings from bootstrap PlatformProperties (name={}).", platformProperties.name)
    }

    /**
     * One Seller row per seed store. store-01 (Blue Mountains Roasters, the
     * store every dashboard/order flow demo interacts with) is linked to the
     * real `test-seller@storepilot.test` Cognito user created for local dev
     * (see infra's manually-created Cognito pool) — logging in as that user
     * manages store-01. The other 7 are placeholder rows with synthetic
     * cognitoSub values — nobody needs to log in as them, they exist only to
     * satisfy Store.sellerId and populate the marketplace catalogue.
     */
    private fun seedSellers(): Map<String, Seller> {
        val seeds = listOf(
            SeedSeller("store-01", "41b39d1a-0051-70ad-50b2-9dac620a0ff0", "test-seller@storepilot.test", "Blue Mountains Roasters Seller"),
            SeedSeller("store-02", "seed-store-02", "seller-store-02@storepilot.test", "Yarra Valley Weavers Seller"),
            SeedSeller("store-03", "seed-store-03", "seller-store-03@storepilot.test", "Bondi Streetwear Seller"),
            SeedSeller("store-04", "seed-store-04", "seller-store-04@storepilot.test", "Byron Bay Botanicals Seller"),
            SeedSeller("store-05", "seed-store-05", "seller-store-05@storepilot.test", "Outback Opal Co. Seller"),
            SeedSeller("store-06", "seed-store-06", "seller-store-06@storepilot.test", "TechHub Australia Seller"),
            SeedSeller("store-07", "seed-store-07", "seller-store-07@storepilot.test", "Aussie Farm Basket Seller"),
            SeedSeller("store-08", "seed-store-08", "seller-store-08@storepilot.test", "Coastal Home Co. Seller"),
        )
        return seeds.associate { s ->
            s.storeKey to sellerRepository.save(Seller(cognitoSub = s.cognitoSub, email = s.email, name = s.name))
        }
    }

    private fun seedStores(sellers: Map<String, Seller>): Map<String, UUID> {
        val seeds = listOf(
            SeedStore("store-01", "blue-mountains-roasters", "Blue Mountains Roasters", "Small-batch coffee & tea from the Blue Mountains", "We roast single-origin coffee and blend loose-leaf tea in small batches out of Katoomba, sourcing green beans direct from growers we know by name.", StoreCategory.FOOD_BEVERAGE, "Katoomba", "New South Wales", "+61412345601", 4.8, 214, 4, true, "2024-02-11", 1320),
            SeedStore("store-02", "yarra-valley-weavers", "Yarra Valley Weavers", "Handwoven wool textiles from the Yarra Valley", "A small weaving studio turning locally sourced Merino wool into throws, scarves and cushion covers on traditional floor looms.", StoreCategory.HANDICRAFTS, "Yarra Valley", "Victoria", "+61412345602", 4.9, 156, 3, true, "2023-11-02", 980),
            SeedStore("store-03", "bondi-streetwear", "Bondi Streetwear", "Everyday streetwear designed and printed in Bondi", "Small local streetwear label making graphic tees, caps and sneakers inspired by Bondi's beach and skate culture.", StoreCategory.FASHION, "Bondi Beach", "New South Wales", "+61412345603", 4.6, 342, 3, true, "2024-05-20", 2110),
            SeedStore("store-04", "byron-bay-botanicals", "Byron Bay Botanicals", "Native-botanical skincare from Byron Bay", "Small-batch skincare made with native Australian botanicals and macadamia oil — cruelty-free and locally formulated.", StoreCategory.BEAUTY, "Byron Bay", "New South Wales", "+61412345604", 4.7, 98, 3, false, "2025-01-15", 410),
            SeedStore("store-05", "outback-opal-co", "Outback Opal Co.", "Certified Australian opals, ethically sourced from Coober Pedy", "Family-run jewellers offering certified boulder and doublet opals set in silver and gold, sourced directly from Coober Pedy fields.", StoreCategory.JEWELRY, "Coober Pedy", "South Australia", "+61412345605", 4.9, 67, 3, true, "2023-08-09", 540),
            SeedStore("store-06", "techhub-australia", "TechHub Australia", "Affordable phone & gadget accessories, delivered nationwide", "Your neighbourhood tech shop online — chargers, earbuds, cables and accessories at fair prices with fast Melbourne dispatch.", StoreCategory.ELECTRONICS, "Melbourne", "Victoria", "+61412345606", 4.4, 501, 4, true, "2024-09-03", 1875),
            SeedStore("store-07", "aussie-farm-basket", "Aussie Farm Basket", "Organic produce direct from Toowoomba farms", "Connecting Darling Downs farmers to city kitchens — organic honey, macadamias, flour and free-range eggs with no middlemen.", StoreCategory.GROCERY, "Toowoomba", "Queensland", "+61412345607", 4.7, 133, 4, false, "2025-03-28", 305),
            SeedStore("store-08", "coastal-home-co", "Coastal Home Co.", "Handmade seagrass, glass & driftwood decor for the home", "Coastal-inspired everyday homeware — woven seagrass baskets, recycled glass vases and driftwood pieces, made in Fremantle.", StoreCategory.HOME_LIVING, "Fremantle", "Western Australia", "+61412345608", 4.5, 74, 3, false, "2025-02-02", 260),
        )

        val ids = mutableMapOf<String, UUID>()
        for (s in seeds) {
            val store = Store(
                seller = sellers.getValue(s.key),
                slug = s.slug,
                name = s.name,
                tagline = s.tagline,
                description = s.description,
                logoUrl = "https://picsum.photos/seed/${s.slug}-logo/200/200",
                bannerUrl = "https://picsum.photos/seed/${s.slug}-banner/1200/400",
                category = s.category,
                address = StoreAddress(s.city, s.state),
                whatsappNumber = s.whatsapp,
                rating = s.rating,
                reviewCount = s.reviewCount,
                productCount = s.productCount,
                isVerified = s.isVerified,
                followerCount = s.followerCount,
                verificationStatus = StoreVerificationStatus.ACTIVE,
            )
            val saved = storeRepository.saveAndFlush(store)
            val id = requireNotNull(saved.id)
            ids[s.key] = id
            backdate("stores", id, d(s.joinedAt), d(s.joinedAt))
        }
        return ids
    }

    private fun seedStoreSettings(store01Id: UUID) {
        val store = storeRepository.findById(store01Id).orElseThrow()
        storeSettingsRepository.save(
            StoreSettings(
                store = store,
                contactEmail = "hello@bluemountainsroasters.com.au",
                contactPhone = "+61412345601",
                bankAccountName = "Blue Mountains Roasters Pty Ltd",
                bankAccountNumber = "BSB 062-000 Acc 1234 5678",
                bankName = "Commonwealth Bank of Australia",
                transactionFeePercent = BigDecimal("3.5"),
                codEnabled = true,
                onlinePaymentEnabled = false,
                bankTransferEnabled = true,
                sellerType = SellerType.BUSINESS,
                driverLicenceNumber = "12345678",
                abn = "51 824 753 556",
            ),
        )
    }

    private fun seedProducts(storeIds: Map<String, UUID>): Map<String, UUID> {
        val seeds = listOf(
            SeedProduct("store-01", "Colombian Single-Origin Coffee Beans (250g)", "colombian-single-origin-coffee-beans-250g", "Washed Colombian beans, roasted in small batches to a bright, fruit-forward medium roast. Whole bean.", "coffeebeans", StoreCategory.FOOD_BEVERAGE, 18, 22, 42, ProductStatus.ACTIVE, "BMR-COF-250", 4.9, 88, "2025-03-01", "2026-06-12"),
            SeedProduct("store-01", "English Breakfast Tea Leaves (200g)", "english-breakfast-tea-leaves-200g", "A bold, malty loose-leaf blend — hand-packed in Katoomba, great with milk.", "blacktea", StoreCategory.FOOD_BEVERAGE, 14, null, 76, ProductStatus.ACTIVE, "BMR-TEA-200", 4.8, 121, "2025-02-14", "2026-05-30"),
            SeedProduct("store-01", "Cold-Pressed Macadamia Oil (500ml)", "cold-pressed-macadamia-oil-500ml", "Unrefined, cold-pressed macadamia oil — great for cooking, hair and skin.", "macoil", StoreCategory.FOOD_BEVERAGE, 24, null, 0, ProductStatus.OUT_OF_STOCK, "BMR-OIL-500", 4.6, 54, "2025-04-20", "2026-07-01"),
            SeedProduct("store-01", "House Blend Ground Coffee (250g)", "house-blend-ground-coffee-250g", "Our everyday house blend, pre-ground for the filter or plunger.", "groundcoffee", StoreCategory.FOOD_BEVERAGE, 16, null, 5, ProductStatus.ACTIVE, "BMR-GRD-250", 4.7, 39, "2025-05-11", "2026-06-25"),

            SeedProduct("store-02", "Handwoven Wool Throw Blanket", "handwoven-wool-throw-blanket", "Merino wool throw, handwoven on a traditional floor loom in the Yarra Valley. One-of-a-kind patterns.", "throwblanket", StoreCategory.HANDICRAFTS, 89, 110, 18, ProductStatus.ACTIVE, "YVW-THR-01", 4.9, 47, "2025-01-22", "2026-06-18"),
            SeedProduct("store-02", "Merino Wool Scarf", "merino-wool-scarf", "Lightweight Merino wool scarf, hand-dyed and woven in-house.", "woolscarf", StoreCategory.FASHION, 45, null, 9, ProductStatus.ACTIVE, "YVW-SCF-02", 4.8, 31, "2025-06-02", "2026-07-05"),
            SeedProduct("store-02", "Woven Cushion Cover Set (2pc)", "woven-cushion-cover-set-2pc", "Set of two 45x45cm cushion covers in complementary handwoven patterns.", "cushioncover", StoreCategory.HOME_LIVING, 55, null, 23, ProductStatus.ACTIVE, "YVW-CUS-03", 4.7, 22, "2025-07-19", "2026-04-14"),

            SeedProduct("store-03", "\"Bondi Beach\" Graphic Tee", "bondi-beach-graphic-tee", "100% combed cotton tee with a screen-printed Bondi Beach graphic. Unisex fit.", "graphictee", StoreCategory.FASHION, 35, null, 64, ProductStatus.ACTIVE, "BST-TEE-01", 4.6, 203, "2025-02-28", "2026-07-10"),
            SeedProduct("store-03", "Australiana Dad Cap", "australiana-dad-cap", "Adjustable cotton twill cap with embroidered kangaroo emblem.", "dadcap", StoreCategory.FASHION, 28, null, 3, ProductStatus.ACTIVE, "BST-CAP-02", 4.5, 66, "2025-03-15", "2026-06-01"),
            SeedProduct("store-03", "Canvas Low-Top Sneakers", "canvas-low-top-sneakers", "Locally made canvas sneakers with rubber soles, unisex sizing.", "sneakers", StoreCategory.FASHION, 79, 95, 15, ProductStatus.ACTIVE, "BST-SNK-03", 4.4, 73, "2025-05-04", "2026-05-22"),

            SeedProduct("store-04", "Native Botanicals Face Serum (30ml)", "native-botanicals-face-serum-30ml", "Lightweight serum with native Kakadu plum extract and niacinamide for brightening.", "faceserum", StoreCategory.BEAUTY, 42, null, 31, ProductStatus.ACTIVE, "BBB-SER-01", 4.7, 41, "2025-04-09", "2026-06-30"),
            SeedProduct("store-04", "Tea Tree Foaming Cleanser (150ml)", "tea-tree-foaming-cleanser-150ml", "Gentle daily cleanser with Australian tea tree oil.", "facewash", StoreCategory.BEAUTY, 22, null, 58, ProductStatus.ACTIVE, "BBB-CLN-02", 4.6, 29, "2025-04-09", "2026-06-30"),
            SeedProduct("store-04", "Macadamia Body Scrub (250g)", "macadamia-body-scrub-250g", "Exfoliating body scrub with crushed macadamia shell and coconut oil.", "bodyscrub", StoreCategory.BEAUTY, 26, null, 12, ProductStatus.ACTIVE, "BBB-SCR-03", 4.8, 18, "2025-08-01", "2026-07-02"),

            SeedProduct("store-05", "Boulder Opal Pendant (Sterling Silver)", "boulder-opal-pendant-sterling-silver", "Certified natural boulder opal set in a sterling silver pendant. Comes with an Australian gem certificate.", "opalpendant", StoreCategory.JEWELRY, 320, null, 4, ProductStatus.ACTIVE, "OOC-PEN-01", 5.0, 12, "2025-01-30", "2026-06-11"),
            SeedProduct("store-05", "Opal Doublet Ring", "opal-doublet-ring", "Sterling silver ring set with a Coober Pedy opal doublet.", "opalring", StoreCategory.JEWELRY, 145, null, 20, ProductStatus.ACTIVE, "OOC-RIN-02", 4.8, 33, "2025-03-18", "2026-05-19"),
            SeedProduct("store-05", "Opal Stud Earrings", "opal-stud-earrings", "Petite opal studs in sterling silver, everyday wear.", "opalearrings", StoreCategory.JEWELRY, 210, null, 7, ProductStatus.ACTIVE, "OOC-EAR-03", 4.9, 9, "2025-09-02", "2026-04-28"),

            SeedProduct("store-06", "USB-C Fast Charger 33W", "usb-c-fast-charger-33w", "33W PD fast charger, compatible with most Android and iPhone devices.", "fastcharger", StoreCategory.ELECTRONICS, 25, null, 140, ProductStatus.ACTIVE, "THA-CHG-01", 4.5, 312, "2025-01-05", "2026-07-15"),
            SeedProduct("store-06", "Wireless Earbuds Pro", "wireless-earbuds-pro", "Bluetooth 5.3 earbuds with ANC and 30-hour case battery life.", "earbuds", StoreCategory.ELECTRONICS, 69, 89, 54, ProductStatus.ACTIVE, "THA-EAR-02", 4.3, 189, "2025-02-19", "2026-07-08"),
            SeedProduct("store-06", "Adjustable Phone Stand", "adjustable-phone-stand", "360° rotating stand and kickstand for phones.", "phonestand", StoreCategory.ELECTRONICS, 12, null, 220, ProductStatus.ACTIVE, "THA-STA-03", 4.2, 98, "2025-03-11", "2026-06-20"),
            SeedProduct("store-06", "Power Bank 10000mAh", "power-bank-10000mah", "Slim 10000mAh power bank with dual USB output and LED indicator.", "powerbank", StoreCategory.ELECTRONICS, 39, null, 0, ProductStatus.OUT_OF_STOCK, "THA-PWR-04", 4.4, 145, "2025-04-02", "2026-07-19"),

            SeedProduct("store-07", "Raw Australian Bush Honey (500g)", "raw-australian-bush-honey-500g", "Unprocessed native bush honey harvested from Darling Downs apiaries.", "honey", StoreCategory.GROCERY, 14, null, 90, ProductStatus.ACTIVE, "AFB-HON-01", 4.9, 61, "2025-05-25", "2026-07-03"),
            SeedProduct("store-07", "Macadamia Nuts Roasted & Salted (300g)", "macadamia-nuts-roasted-salted-300g", "Queensland-grown macadamias, roasted and lightly salted.", "macadamias", StoreCategory.GROCERY, 12, null, 44, ProductStatus.ACTIVE, "AFB-MAC-02", 4.8, 52, "2025-06-14", "2026-06-27"),
            SeedProduct("store-07", "Sourdough Rye Flour (2kg)", "sourdough-rye-flour-2kg", "Stone-ground rye flour, milled on the farm.", "ryeflour", StoreCategory.GROCERY, 9, null, 33, ProductStatus.ACTIVE, "AFB-FLR-03", 4.7, 40, "2025-06-14", "2026-05-16"),
            SeedProduct("store-07", "Free-Range Farm Eggs (Dozen)", "free-range-farm-eggs-dozen", "Pasture-raised free-range eggs, collected fresh daily.", "eggs", StoreCategory.GROCERY, 8, null, 4, ProductStatus.ACTIVE, "AFB-EGG-04", 4.5, 27, "2025-07-30", "2026-07-11"),

            SeedProduct("store-08", "Handwoven Seagrass Basket", "handwoven-seagrass-basket", "Natural seagrass storage basket, handwoven in Fremantle.", "seagrassbasket", StoreCategory.HOME_LIVING, 45, null, 26, ProductStatus.ACTIVE, "CHC-BAS-01", 4.6, 21, "2025-04-27", "2026-06-05"),
            SeedProduct("store-08", "Recycled Glass Vase Set (3pc)", "recycled-glass-vase-set-3pc", "Hand-blown vases made from recycled glass, in coastal tones.", "glassvase", StoreCategory.HOME_LIVING, 38, null, 37, ProductStatus.ACTIVE, "CHC-VAS-02", 4.7, 16, "2025-05-08", "2026-06-09"),
            SeedProduct("store-08", "Driftwood Table Runner", "driftwood-table-runner", "Hand-finished driftwood table runner, 180cm, sourced from local beaches.", "driftwoodrunner", StoreCategory.HOME_LIVING, 32, null, 19, ProductStatus.ACTIVE, "CHC-RUN-03", 4.4, 11, "2025-08-14", "2026-07-06"),
        )

        val ids = mutableMapOf<String, UUID>()
        for ((index, p) in seeds.withIndex()) {
            val store = storeRepository.findById(storeIds.getValue(p.storeKey)).orElseThrow()
            val product = Product(
                store = store,
                name = p.name,
                slug = p.slug,
                description = p.description,
                category = p.category,
                price = p.price * 100,
                compareAtPrice = p.compareAtPrice?.times(100),
                stockQuantity = p.stockQuantity,
                status = p.status,
                sku = p.sku,
                rating = p.rating,
                reviewCount = p.reviewCount,
            )
            product.images.add(ProductImage(product = product, url = "https://picsum.photos/seed/${p.imageSeed}/700/700", alt = p.name))
            val saved = productRepository.saveAndFlush(product)
            val id = requireNotNull(saved.id)
            val key = "prod-%03d".format(index + 1)
            ids[key] = id
            backdate("products", id, d(p.createdAt), d(p.updatedAt))
        }
        return ids
    }

    private fun seedBuyer(): UUID {
        val buyer = Buyer(
            name = "Jack Thompson",
            email = "jack.thompson@example.com",
            phone = "+61 412 890 123",
        )
        val saved = buyerRepository.saveAndFlush(buyer)
        val id = requireNotNull(saved.id)
        backdate("buyers", id, dt("2026-06-01T09:00:00+10:00"), dt("2026-06-01T09:00:00+10:00"))

        val address = Address(
            buyer = saved,
            label = "Home",
            shipping = ShippingDetails(
                fullName = "Jack Thompson",
                phone = "+61 412 890 123",
                addressLine1 = "45 Harbour Street",
                city = "Sydney",
                state = "New South Wales",
                postalCode = "2000",
            ),
            isDefault = true,
        )
        val savedAddress = addressRepository.saveAndFlush(address)
        backdate("addresses", requireNotNull(savedAddress.id), dt("2026-06-01T09:00:00+10:00"), dt("2026-06-01T09:00:00+10:00"))
        return id
    }

    private fun seedOrders(store01Id: UUID, productIds: Map<String, UUID>): Map<String, UUID> {
        val store = storeRepository.findById(store01Id).orElseThrow()
        val ids = mutableMapOf<String, UUID>()

        fun order(
            key: String,
            orderNumber: String,
            items: List<Triple<String, Int, Int>>, // productKey, unitPrice, quantity
            subtotal: Int,
            status: OrderStatus,
            paymentMethod: PaymentMethod,
            paymentStatus: PaymentStatus,
            buyerName: String,
            buyerEmail: String,
            phone: String,
            addressLine1: String,
            city: String,
            state: String,
            postalCode: String,
            timeline: List<Triple<OrderStatus, String, String>>, // status, note?, timestamp
            createdAt: String,
        ) {
            // subtotal (and each item's unitPrice below) arrive as whole-dollar
            // literals from the call sites — converted to cents here, once.
            val subtotalCents = subtotal * 100
            val platformFee = (BigDecimal(subtotalCents) * platformProperties.platformFeePercent).divide(BigDecimal(100), 0, java.math.RoundingMode.HALF_UP).toInt()
            val order = Order(
                orderNumber = orderNumber,
                store = store,
                subtotal = subtotalCents,
                shippingFee = platformProperties.flatShippingFee,
                platformFee = platformFee,
                total = subtotalCents + platformProperties.flatShippingFee,
                status = status,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus,
                shipping = ShippingDetails(buyerName, phone, addressLine1, city, state, postalCode),
                buyerEmail = buyerEmail,
            )
            items.forEach { (productKey, unitPrice, quantity) ->
                val product = productRepository.findById(productIds.getValue(productKey)).orElseThrow()
                order.items.add(
                    OrderItem(
                        order = order,
                        productId = requireNotNull(product.id),
                        productName = product.name,
                        productImageUrl = product.images.firstOrNull()?.url ?: "",
                        unitPrice = unitPrice * 100,
                        quantity = quantity,
                    ),
                )
            }
            timeline.forEach { (s, note, ts) ->
                order.timeline.add(
                    OrderTimelineEntry(order = order, status = s, label = statusLabel(s), timestamp = dt(ts), note = note.ifBlank { null }),
                )
            }
            val saved = orderRepository.saveAndFlush(order)
            val id = requireNotNull(saved.id)
            ids[key] = id
            backdate("orders", id, dt(createdAt), dt(createdAt))
        }

        order(
            "order-1001", "AU-20260722-1001",
            items = listOf(Triple("prod-001", 18, 2), Triple("prod-002", 14, 1)),
            subtotal = 50, status = OrderStatus.PENDING, paymentMethod = PaymentMethod.COD, paymentStatus = PaymentStatus.UNPAID,
            buyerName = "Amelia Clarke", buyerEmail = "amelia.clarke@example.com", phone = "+61 478 456 789",
            addressLine1 = "24/3 George Street", city = "Parramatta", state = "New South Wales", postalCode = "2150",
            timeline = listOf(Triple(OrderStatus.PENDING, "", "2026-07-22T09:14:00+10:00")),
            createdAt = "2026-07-22T09:14:00+10:00",
        )
        order(
            "order-1002", "AU-20260721-1002",
            items = listOf(Triple("prod-004", 16, 3)),
            subtotal = 48, status = OrderStatus.CONFIRMED, paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID,
            buyerName = "Liam Walker", buyerEmail = "liam.walker@example.com", phone = "+61 471 234 567",
            addressLine1 = "12 Ruthven Street", city = "Toowoomba", state = "Queensland", postalCode = "4350",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-21T14:02:00+10:00"),
                Triple(OrderStatus.CONFIRMED, "", "2026-07-21T16:40:00+10:00"),
            ),
            createdAt = "2026-07-21T14:02:00+10:00",
        )
        order(
            "order-1003", "AU-20260719-1003",
            items = listOf(Triple("prod-002", 14, 2), Triple("prod-001", 18, 1)),
            subtotal = 46, status = OrderStatus.SHIPPED, paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID,
            buyerName = "Sophie Nguyen", buyerEmail = "sophie.nguyen@example.com", phone = "+61 476 789 012",
            addressLine1 = "88 Beach Road", city = "Byron Bay", state = "New South Wales", postalCode = "2481",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-19T10:20:00+10:00"),
                Triple(OrderStatus.CONFIRMED, "", "2026-07-19T11:05:00+10:00"),
                Triple(OrderStatus.SHIPPED, "Tracking: AusPost 8827412", "2026-07-20T15:30:00+10:00"),
            ),
            createdAt = "2026-07-19T10:20:00+10:00",
        )
        order(
            "order-1004", "AU-20260715-1004",
            items = listOf(Triple("prod-003", 24, 1)),
            subtotal = 24, status = OrderStatus.DELIVERED, paymentMethod = PaymentMethod.COD, paymentStatus = PaymentStatus.PAID,
            buyerName = "Noah Mitchell", buyerEmail = "noah.mitchell@example.com", phone = "+61 470 111 223",
            addressLine1 = "5 Lakeside Drive", city = "Katoomba", state = "New South Wales", postalCode = "2780",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-15T08:00:00+10:00"),
                Triple(OrderStatus.CONFIRMED, "", "2026-07-15T09:12:00+10:00"),
                Triple(OrderStatus.SHIPPED, "", "2026-07-16T13:00:00+10:00"),
                Triple(OrderStatus.DELIVERED, "", "2026-07-17T17:45:00+10:00"),
            ),
            createdAt = "2026-07-15T08:00:00+10:00",
        )
        order(
            "order-1005", "AU-20260710-1005",
            items = listOf(Triple("prod-004", 16, 1)),
            subtotal = 16, status = OrderStatus.CANCELLED, paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.REFUNDED,
            buyerName = "Chloe Anderson", buyerEmail = "chloe.anderson@example.com", phone = "+61 475 222 445",
            addressLine1 = "17 Hill Street", city = "Leura", state = "New South Wales", postalCode = "2780",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-10T12:00:00+10:00"),
                Triple(OrderStatus.CANCELLED, "Buyer requested cancellation before dispatch", "2026-07-10T18:20:00+10:00"),
            ),
            createdAt = "2026-07-10T12:00:00+10:00",
        )

        return ids
    }

    private fun seedPayout(store01Id: UUID, order1004Id: UUID) {
        val store = storeRepository.findById(store01Id).orElseThrow()
        val order1004 = orderRepository.findById(order1004Id).orElseThrow()
        val subtotal = 24 * 100
        val platformFee = 1 * 100
        val net = 23 * 100
        val payout = Payout(
            store = store,
            subtotal = subtotal,
            platformFee = platformFee,
            net = net,
            status = PayoutStatus.PAID,
            paidAt = dt("2026-07-18T14:32:00+10:00"),
            bankReference = "CBA-TRF-88213",
        )
        payout.sourceRefs.add(
            PayoutSourceRef(
                payout = payout,
                orderId = order1004Id,
                orderNumber = order1004.orderNumber,
                subtotal = subtotal,
                platformFee = platformFee,
                net = net,
            ),
        )
        val saved = payoutRepository.saveAndFlush(payout)
        backdate("payouts", requireNotNull(saved.id), dt("2026-07-18T09:00:00+10:00"), dt("2026-07-18T09:00:00+10:00"))
    }

    private fun statusLabel(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "Order placed"
        OrderStatus.CONFIRMED -> "Order confirmed by seller"
        OrderStatus.SHIPPED -> "Handed over to courier"
        OrderStatus.DELIVERED -> "Delivered"
        OrderStatus.CANCELLED -> "Cancelled"
    }

    /** Bypasses @CreatedDate/@LastModifiedDate auditing (which would otherwise stamp "now" on every seed row) so seed data keeps its realistic historical dates. */
    private fun backdate(table: String, id: UUID, createdAt: Instant, updatedAt: Instant) {
        jdbcTemplate.update(
            "update $table set created_at = ?, updated_at = ? where id = ?",
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(updatedAt),
            id,
        )
    }
}
