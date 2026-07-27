package com.islandcart.backend.common

import com.islandcart.backend.buyer.Buyer
import com.islandcart.backend.buyer.BuyerRepository
import com.islandcart.backend.order.Order
import com.islandcart.backend.order.OrderItem
import com.islandcart.backend.order.OrderRepository
import com.islandcart.backend.order.OrderStatus
import com.islandcart.backend.order.OrderTimelineEntry
import com.islandcart.backend.order.PaymentMethod
import com.islandcart.backend.order.PaymentStatus
import com.islandcart.backend.payout.Payout
import com.islandcart.backend.payout.PayoutOrderRef
import com.islandcart.backend.payout.PayoutRepository
import com.islandcart.backend.payout.PayoutStatus
import com.islandcart.backend.product.Product
import com.islandcart.backend.product.ProductImage
import com.islandcart.backend.product.ProductRepository
import com.islandcart.backend.product.ProductStatus
import com.islandcart.backend.seller.Seller
import com.islandcart.backend.seller.SellerRepository
import com.islandcart.backend.store.SellerType
import com.islandcart.backend.store.Store
import com.islandcart.backend.store.StoreAddress
import com.islandcart.backend.store.StoreCategory
import com.islandcart.backend.store.StoreRepository
import com.islandcart.backend.store.StoreSettings
import com.islandcart.backend.store.StoreSettingsRepository
import com.islandcart.backend.store.StoreVerificationStatus
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
    val district: String,
    val province: String,
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
    val priceLkr: Int,
    val compareAtPriceLkr: Int?,
    val stockQuantity: Int,
    val status: ProductStatus,
    val sku: String,
    val rating: Double,
    val reviewCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Translates the frontend's mock data (app/src/mock) into real rows on first boot (only runs
 * against an empty `stores` table, so it's safe to leave enabled — restarts
 * against an already-seeded database are a no-op). This is what "move the
 * mock data to the database" means concretely: the exact same demo
 * catalogue/orders the frontend used to fabricate from localStorage now
 * exists for real.
 */
@Component
class DataSeeder(
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val productRepository: ProductRepository,
    private val buyerRepository: BuyerRepository,
    private val orderRepository: OrderRepository,
    private val payoutRepository: PayoutRepository,
    private val sellerRepository: SellerRepository,
    private val jdbcTemplate: JdbcTemplate,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(DataSeeder::class.java)

    @Transactional
    override fun run(vararg args: String) {
        if (storeRepository.count() > 0) {
            log.info("Seed skipped — stores table already has data.")
            return
        }
        log.info("Seeding database from frontend mock data...")

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
     * One Seller row per seed store. store-01 (Ceylon Spice Co., the store
     * every dashboard/order flow demo interacts with) is linked to the real
     * `test-seller@islandcart.test` Cognito user created for local dev
     * (see infra's manually-created Cognito pool) — logging in as that user
     * manages store-01, the same role the old hardcoded
     * CURRENT_SELLER_STORE_ID demo login played. The other 7 are placeholder
     * rows with synthetic cognitoSub values — nobody needs to log in as
     * them, they exist only to satisfy Store.sellerId and populate the
     * marketplace catalogue.
     */
    private fun seedSellers(): Map<String, Seller> {
        val seeds = listOf(
            SeedSeller("store-01", "41b39d1a-0051-70ad-50b2-9dac620a0ff0", "test-seller@islandcart.test", "Ceylon Spice Co. Seller"),
            SeedSeller("store-02", "seed-store-02", "seller-store-02@islandcart.test", "Kolam Batik House Seller"),
            SeedSeller("store-03", "seed-store-03", "seller-store-03@islandcart.test", "Colombo Streetwear Seller"),
            SeedSeller("store-04", "seed-store-04", "seller-store-04@islandcart.test", "Nuwara Glow Beauty Seller"),
            SeedSeller("store-05", "seed-store-05", "seller-store-05@islandcart.test", "Lanka Gems & Jewels Seller"),
            SeedSeller("store-06", "seed-store-06", "seller-store-06@islandcart.test", "TechHub Lanka Seller"),
            SeedSeller("store-07", "seed-store-07", "seller-store-07@islandcart.test", "Village Basket Seller"),
            SeedSeller("store-08", "seed-store-08", "seller-store-08@islandcart.test", "Home & Hearth Lanka Seller"),
        )
        return seeds.associate { s ->
            s.storeKey to sellerRepository.save(Seller(cognitoSub = s.cognitoSub, email = s.email, name = s.name))
        }
    }

    private fun seedStores(sellers: Map<String, Seller>): Map<String, UUID> {
        val seeds = listOf(
            SeedStore("store-01", "ceylon-spice-co", "Ceylon Spice Co.", "Farm-fresh spices & tea from the hills of Kandy", "We source cinnamon, tea and spices directly from small growers around Kandy and Matale, roasting and packing everything in small batches for freshness.", StoreCategory.FOOD_BEVERAGE, "Kandy", "Kandy", "Central", "+94771234501", 4.8, 214, 4, true, "2024-02-11", 1320),
            SeedStore("store-02", "kolam-batik-house", "Kolam Batik House", "Handmade batik, straight from the Galle Fort workshops", "Three generations of batik artisans creating hand-painted sarongs, dresses and home textiles using traditional wax-resist dyeing.", StoreCategory.HANDICRAFTS, "Galle", "Galle", "Southern", "+94771234502", 4.9, 156, 3, true, "2023-11-02", 980),
            SeedStore("store-03", "colombo-streetwear", "Colombo Streetwear", "Everyday streetwear designed and printed in Colombo", "Small local streetwear label making graphic tees, caps and sneakers inspired by Colombo's neighbourhoods and skate scene.", StoreCategory.FASHION, "Colombo", "Colombo", "Western", "+94771234503", 4.6, 342, 3, true, "2024-05-20", 2110),
            SeedStore("store-04", "nuwara-glow-beauty", "Nuwara Glow Beauty", "Tea-infused skincare from the highlands", "Small-batch skincare made with Ceylon green tea, herbs and highland botanicals — cruelty-free and locally formulated.", StoreCategory.BEAUTY, "Nuwara Eliya", "Nuwara Eliya", "Central", "+94771234504", 4.7, 98, 3, false, "2025-01-15", 410),
            SeedStore("store-05", "lanka-gems-jewels", "Lanka Gems & Jewels", "Certified Ceylon gemstones, ethically sourced from Ratnapura", "Family-run jewellers offering certified sapphires, moonstones and custom settings, sourced directly from Ratnapura gem pits.", StoreCategory.JEWELRY, "Ratnapura", "Ratnapura", "Sabaragamuwa", "+94771234505", 4.9, 67, 3, true, "2023-08-09", 540),
            SeedStore("store-06", "techhub-lanka", "TechHub Lanka", "Affordable phone & gadget accessories, delivered island-wide", "Your neighbourhood tech shop online — chargers, earbuds, cables and accessories at fair prices with fast Colombo dispatch.", StoreCategory.ELECTRONICS, "Colombo", "Colombo", "Western", "+94771234506", 4.4, 501, 4, true, "2024-09-03", 1875),
            SeedStore("store-07", "village-basket", "Village Basket", "Organic produce direct from Kurunegala farms", "Connecting Kurunegala farmers to city kitchens — organic jaggery, honey, rice and dried goods with no middlemen.", StoreCategory.GROCERY, "Kurunegala", "Kurunegala", "North Western", "+94771234507", 4.7, 133, 4, false, "2025-03-28", 305),
            SeedStore("store-08", "home-hearth-lanka", "Home & Hearth Lanka", "Handmade clay, coconut shell & reed decor for the home", "Reviving traditional Sri Lankan craft techniques into everyday homeware — clay pots, coconut shell bowls and reed weaves.", StoreCategory.HOME_LIVING, "Negombo", "Gampaha", "Western", "+94771234508", 4.5, 74, 3, false, "2025-02-02", 260),
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
                address = StoreAddress(s.city, s.district, s.province),
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
                contactEmail = "hello@ceylonspiceco.lk",
                contactPhone = "+94771234501",
                bankAccountName = "Ceylon Spice Co. (Pvt) Ltd",
                bankAccountNumber = "0081 4562 1190",
                bankName = "Commercial Bank of Ceylon",
                transactionFeePercent = BigDecimal("3.5"),
                codEnabled = true,
                onlinePaymentEnabled = true,
                sellerType = SellerType.BUSINESS,
                nicNumber = "851234567V",
                businessRegistrationNumber = "PV 00219845",
            ),
        )
    }

    private fun seedProducts(storeIds: Map<String, UUID>): Map<String, UUID> {
        val seeds = listOf(
            SeedProduct("store-01", "Ceylon Cinnamon Sticks (100g)", "ceylon-cinnamon-sticks-100g", "True Ceylon cinnamon (Cinnamomum verum) hand-rolled by Matale growers. Sweet, delicate flavour — ideal for tea, baking and curries.", "cinnamon", StoreCategory.FOOD_BEVERAGE, 850, 950, 42, ProductStatus.ACTIVE, "CSC-CIN-100", 4.9, 88, "2025-03-01", "2026-06-12"),
            SeedProduct("store-01", "Pure Ceylon Black Tea (200g)", "pure-ceylon-black-tea-200g", "High-grown black tea from Kandy estates, hand-picked and slow-dried for a bold, malty cup.", "blacktea", StoreCategory.FOOD_BEVERAGE, 650, null, 76, ProductStatus.ACTIVE, "CSC-TEA-200", 4.8, 121, "2025-02-14", "2026-05-30"),
            SeedProduct("store-01", "Cold-Pressed King Coconut Oil (500ml)", "cold-pressed-king-coconut-oil-500ml", "Unrefined, cold-pressed coconut oil from king coconuts — great for cooking, hair and skin.", "coconutoil", StoreCategory.FOOD_BEVERAGE, 1200, null, 0, ProductStatus.OUT_OF_STOCK, "CSC-OIL-500", 4.6, 54, "2025-04-20", "2026-07-01"),
            SeedProduct("store-01", "Roasted Curry Powder (250g)", "roasted-curry-powder-250g", "Traditional roasted curry powder blend — coriander, cumin, fennel and Ceylon spices roasted in small batches.", "currypowder", StoreCategory.FOOD_BEVERAGE, 450, null, 5, ProductStatus.ACTIVE, "CSC-CUR-250", 4.7, 39, "2025-05-11", "2026-06-25"),

            SeedProduct("store-02", "Hand-Painted Batik Sarong", "hand-painted-batik-sarong", "Traditional wax-resist batik sarong, hand-painted in Galle Fort using natural dyes. One-of-a-kind patterns.", "batiksarong", StoreCategory.HANDICRAFTS, 2800, 3200, 18, ProductStatus.ACTIVE, "KBH-SAR-01", 4.9, 47, "2025-01-22", "2026-06-18"),
            SeedProduct("store-02", "Batik Cotton Midi Dress", "batik-cotton-midi-dress", "Breathable cotton midi dress with hand-stamped batik print, tailored in-house.", "batikdress", StoreCategory.FASHION, 4500, null, 9, ProductStatus.ACTIVE, "KBH-DRS-02", 4.8, 31, "2025-06-02", "2026-07-05"),
            SeedProduct("store-02", "Batik Cushion Cover Set (2pc)", "batik-cushion-cover-set-2pc", "Set of two 45x45cm cushion covers in complementary batik prints.", "batikcushion", StoreCategory.HOME_LIVING, 1800, null, 23, ProductStatus.ACTIVE, "KBH-CUS-03", 4.7, 22, "2025-07-19", "2026-04-14"),

            SeedProduct("store-03", "\"Colombo 07\" Graphic Tee", "colombo-07-graphic-tee", "100% combed cotton tee with a screen-printed Colombo 07 skyline graphic. Unisex fit.", "graphictee", StoreCategory.FASHION, 1990, null, 64, ProductStatus.ACTIVE, "CST-TEE-07", 4.6, 203, "2025-02-28", "2026-07-10"),
            SeedProduct("store-03", "Ceylon Dad Cap", "ceylon-dad-cap", "Adjustable cotton twill cap with embroidered lion emblem.", "dadcap", StoreCategory.FASHION, 1450, null, 3, ProductStatus.ACTIVE, "CST-CAP-05", 4.5, 66, "2025-03-15", "2026-06-01"),
            SeedProduct("store-03", "Canvas Low-Top Sneakers", "canvas-low-top-sneakers", "Locally made canvas sneakers with rubber soles, unisex sizing.", "sneakers", StoreCategory.FASHION, 6500, 7200, 15, ProductStatus.ACTIVE, "CST-SNK-09", 4.4, 73, "2025-05-04", "2026-05-22"),

            SeedProduct("store-04", "Green Tea Face Serum (30ml)", "green-tea-face-serum-30ml", "Lightweight serum with Ceylon green tea extract and niacinamide for brightening.", "faceserum", StoreCategory.BEAUTY, 2200, null, 31, ProductStatus.ACTIVE, "NGB-SER-01", 4.7, 41, "2025-04-09", "2026-06-30"),
            SeedProduct("store-04", "Herbal Foaming Face Wash (150ml)", "herbal-foaming-face-wash-150ml", "Gentle daily cleanser with neem, turmeric and highland herbs.", "facewash", StoreCategory.BEAUTY, 950, null, 58, ProductStatus.ACTIVE, "NGB-WSH-02", 4.6, 29, "2025-04-09", "2026-06-30"),
            SeedProduct("store-04", "Ceylon Tea Body Scrub (250g)", "ceylon-tea-body-scrub-250g", "Exfoliating body scrub with spent tea leaves and coconut oil.", "bodyscrub", StoreCategory.BEAUTY, 1650, null, 12, ProductStatus.ACTIVE, "NGB-SCR-03", 4.8, 18, "2025-08-01", "2026-07-02"),

            SeedProduct("store-05", "Blue Sapphire Pendant (18k Gold)", "blue-sapphire-pendant-18k-gold", "Certified natural blue sapphire (1.2ct) set in 18k gold pendant. Comes with GIA-equivalent local gem certificate.", "sapphirependant", StoreCategory.JEWELRY, 45000, null, 4, ProductStatus.ACTIVE, "LGJ-PEN-01", 5.0, 12, "2025-01-30", "2026-06-11"),
            SeedProduct("store-05", "Moonstone Silver Ring", "moonstone-silver-ring", "Sterling silver ring set with a rainbow moonstone from Meetiyagoda.", "moonstonering", StoreCategory.JEWELRY, 8500, null, 20, ProductStatus.ACTIVE, "LGJ-RIN-02", 4.8, 33, "2025-03-18", "2026-05-19"),
            SeedProduct("store-05", "Ceylon Sapphire Stud Earrings", "ceylon-sapphire-stud-earrings", "Petite blue sapphire studs in sterling silver, everyday wear.", "sapphireearrings", StoreCategory.JEWELRY, 32000, null, 7, ProductStatus.ACTIVE, "LGJ-EAR-03", 4.9, 9, "2025-09-02", "2026-04-28"),

            SeedProduct("store-06", "Type-C Fast Charger 33W", "type-c-fast-charger-33w", "33W PD fast charger, compatible with most Android and iPhone devices.", "fastcharger", StoreCategory.ELECTRONICS, 2450, null, 140, ProductStatus.ACTIVE, "THL-CHG-01", 4.5, 312, "2025-01-05", "2026-07-15"),
            SeedProduct("store-06", "Wireless Earbuds Pro", "wireless-earbuds-pro", "Bluetooth 5.3 earbuds with ANC and 30-hour case battery life.", "earbuds", StoreCategory.ELECTRONICS, 5990, 6990, 54, ProductStatus.ACTIVE, "THL-EAR-02", 4.3, 189, "2025-02-19", "2026-07-08"),
            SeedProduct("store-06", "Adjustable Phone Ring Stand", "adjustable-phone-ring-stand", "360° rotating ring holder and kickstand for phones.", "ringstand", StoreCategory.ELECTRONICS, 590, null, 220, ProductStatus.ACTIVE, "THL-RIN-03", 4.2, 98, "2025-03-11", "2026-06-20"),
            SeedProduct("store-06", "Power Bank 10000mAh", "power-bank-10000mah", "Slim 10000mAh power bank with dual USB output and LED indicator.", "powerbank", StoreCategory.ELECTRONICS, 4200, null, 0, ProductStatus.OUT_OF_STOCK, "THL-PWR-04", 4.4, 145, "2025-04-02", "2026-07-19"),

            SeedProduct("store-07", "Organic Kithul Jaggery (500g)", "organic-kithul-jaggery-500g", "Traditionally tapped kithul palm jaggery, unrefined and organic.", "jaggery", StoreCategory.GROCERY, 480, null, 90, ProductStatus.ACTIVE, "VB-JAG-01", 4.9, 61, "2025-05-25", "2026-07-03"),
            SeedProduct("store-07", "Raw Bee Honey (750ml)", "raw-bee-honey-750ml", "Unprocessed wildflower honey harvested from Kurunegala apiaries.", "honey", StoreCategory.GROCERY, 1350, null, 44, ProductStatus.ACTIVE, "VB-HON-02", 4.8, 52, "2025-06-14", "2026-06-27"),
            SeedProduct("store-07", "Red Rice (5kg)", "red-rice-5kg", "Traditional unpolished red rice, stone-ground and sun-dried.", "redrice", StoreCategory.GROCERY, 1450, null, 33, ProductStatus.ACTIVE, "VB-RIC-03", 4.7, 40, "2025-06-14", "2026-05-16"),
            SeedProduct("store-07", "Dried Anchovies / Karawala (250g)", "dried-anchovies-karawala-250g", "Sun-dried anchovies, cleaned and ready to cook.", "karawala", StoreCategory.GROCERY, 620, null, 4, ProductStatus.ACTIVE, "VB-KAR-04", 4.5, 27, "2025-07-30", "2026-07-11"),

            SeedProduct("store-08", "Handmade Clay Cooking Pot", "handmade-clay-cooking-pot", "Traditional unglazed clay pot from Molagoda potters, seasoned and ready to use.", "claypot", StoreCategory.HOME_LIVING, 1650, null, 26, ProductStatus.ACTIVE, "HHL-POT-01", 4.6, 21, "2025-04-27", "2026-06-05"),
            SeedProduct("store-08", "Coconut Shell Bowl Set (4pc)", "coconut-shell-bowl-set-4pc", "Hand-polished coconut shell bowls, food-safe lacquer finish.", "shellbowl", StoreCategory.HOME_LIVING, 1200, null, 37, ProductStatus.ACTIVE, "HHL-BWL-02", 4.7, 16, "2025-05-08", "2026-06-09"),
            SeedProduct("store-08", "Woven Reed Table Runner", "woven-reed-table-runner", "Hand-woven pan (reed) table runner in natural tones, 180cm.", "reedrunner", StoreCategory.HOME_LIVING, 950, null, 19, ProductStatus.ACTIVE, "HHL-RUN-03", 4.4, 11, "2025-08-14", "2026-07-06"),
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
                priceLkr = p.priceLkr,
                compareAtPriceLkr = p.compareAtPriceLkr,
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
            name = "Tharindu Silva",
            email = "tharindu@example.com",
            phone = "+94 77 890 1234",
            defaultShipping = ShippingDetails(
                fullName = "Tharindu Silva",
                phone = "+94 77 890 1234",
                addressLine1 = "45 Independence Avenue",
                city = "Colombo",
                district = "Colombo",
                postalCode = "00700",
            ),
        )
        val saved = buyerRepository.saveAndFlush(buyer)
        val id = requireNotNull(saved.id)
        backdate("buyers", id, dt("2026-06-01T09:00:00+05:30"), dt("2026-06-01T09:00:00+05:30"))
        return id
    }

    private fun seedOrders(store01Id: UUID, productIds: Map<String, UUID>): Map<String, UUID> {
        val store = storeRepository.findById(store01Id).orElseThrow()
        val ids = mutableMapOf<String, UUID>()

        fun order(
            key: String,
            orderNumber: String,
            items: List<Triple<String, Int, Int>>, // productKey, unitPriceLkr, quantity
            subtotalLkr: Int,
            status: OrderStatus,
            paymentMethod: PaymentMethod,
            paymentStatus: PaymentStatus,
            buyerName: String,
            buyerEmail: String,
            phone: String,
            addressLine1: String,
            city: String,
            district: String,
            postalCode: String,
            timeline: List<Triple<OrderStatus, String, String>>, // status, note?, timestamp
            createdAt: String,
        ) {
            val platformFeeLkr = (BigDecimal(subtotalLkr) * PLATFORM_FEE_PERCENT).divide(BigDecimal(100), 0, java.math.RoundingMode.HALF_UP).toInt()
            val order = Order(
                orderNumber = orderNumber,
                store = store,
                subtotalLkr = subtotalLkr,
                shippingFeeLkr = FLAT_SHIPPING_FEE_LKR,
                platformFeeLkr = platformFeeLkr,
                totalLkr = subtotalLkr + FLAT_SHIPPING_FEE_LKR,
                status = status,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus,
                shipping = ShippingDetails(buyerName, phone, addressLine1, city, district, postalCode),
                buyerEmail = buyerEmail,
            )
            items.forEach { (productKey, unitPriceLkr, quantity) ->
                val product = productRepository.findById(productIds.getValue(productKey)).orElseThrow()
                order.items.add(
                    OrderItem(
                        order = order,
                        productId = requireNotNull(product.id),
                        productName = product.name,
                        productImageUrl = product.images.firstOrNull()?.url ?: "",
                        unitPriceLkr = unitPriceLkr,
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
            "order-1001", "SL-20260722-1001",
            items = listOf(Triple("prod-001", 850, 2), Triple("prod-002", 650, 1)),
            subtotalLkr = 2350, status = OrderStatus.PENDING, paymentMethod = PaymentMethod.COD, paymentStatus = PaymentStatus.UNPAID,
            buyerName = "Nadeesha Perera", buyerEmail = "nadeesha.perera@example.com", phone = "+94 77 456 7890",
            addressLine1 = "24/3 Galle Road", city = "Dehiwala", district = "Colombo", postalCode = "10350",
            timeline = listOf(Triple(OrderStatus.PENDING, "", "2026-07-22T09:14:00+05:30")),
            createdAt = "2026-07-22T09:14:00+05:30",
        )
        order(
            "order-1002", "SL-20260721-1002",
            items = listOf(Triple("prod-004", 450, 3)),
            subtotalLkr = 1350, status = OrderStatus.CONFIRMED, paymentMethod = PaymentMethod.PAYHERE, paymentStatus = PaymentStatus.PAID,
            buyerName = "Kasun Jayawardena", buyerEmail = "kasun.jayawardena@example.com", phone = "+94 71 234 5678",
            addressLine1 = "12 Temple Road", city = "Kurunegala", district = "Kurunegala", postalCode = "60000",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-21T14:02:00+05:30"),
                Triple(OrderStatus.CONFIRMED, "", "2026-07-21T16:40:00+05:30"),
            ),
            createdAt = "2026-07-21T14:02:00+05:30",
        )
        order(
            "order-1003", "SL-20260719-1003",
            items = listOf(Triple("prod-002", 650, 2), Triple("prod-001", 850, 1)),
            subtotalLkr = 2150, status = OrderStatus.SHIPPED, paymentMethod = PaymentMethod.PAYHERE, paymentStatus = PaymentStatus.PAID,
            buyerName = "Ishara Fernando", buyerEmail = "ishara.fernando@example.com", phone = "+94 76 789 0123",
            addressLine1 = "88 Station Road", city = "Negombo", district = "Gampaha", postalCode = "11500",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-19T10:20:00+05:30"),
                Triple(OrderStatus.CONFIRMED, "", "2026-07-19T11:05:00+05:30"),
                Triple(OrderStatus.SHIPPED, "Tracking: Domex 8827412", "2026-07-20T15:30:00+05:30"),
            ),
            createdAt = "2026-07-19T10:20:00+05:30",
        )
        order(
            "order-1004", "SL-20260715-1004",
            items = listOf(Triple("prod-003", 1200, 1)),
            subtotalLkr = 1200, status = OrderStatus.DELIVERED, paymentMethod = PaymentMethod.COD, paymentStatus = PaymentStatus.PAID,
            buyerName = "Ruwan Wickramasinghe", buyerEmail = "ruwan.wickramasinghe@example.com", phone = "+94 70 111 2233",
            addressLine1 = "5 Lake Drive", city = "Kandy", district = "Kandy", postalCode = "20000",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-15T08:00:00+05:30"),
                Triple(OrderStatus.CONFIRMED, "", "2026-07-15T09:12:00+05:30"),
                Triple(OrderStatus.SHIPPED, "", "2026-07-16T13:00:00+05:30"),
                Triple(OrderStatus.DELIVERED, "", "2026-07-17T17:45:00+05:30"),
            ),
            createdAt = "2026-07-15T08:00:00+05:30",
        )
        order(
            "order-1005", "SL-20260710-1005",
            items = listOf(Triple("prod-004", 450, 1)),
            subtotalLkr = 450, status = OrderStatus.CANCELLED, paymentMethod = PaymentMethod.PAYHERE, paymentStatus = PaymentStatus.REFUNDED,
            buyerName = "Dilani Rathnayake", buyerEmail = "dilani.rathnayake@example.com", phone = "+94 75 222 4455",
            addressLine1 = "17 Hill Street", city = "Matale", district = "Matale", postalCode = "21000",
            timeline = listOf(
                Triple(OrderStatus.PENDING, "", "2026-07-10T12:00:00+05:30"),
                Triple(OrderStatus.CANCELLED, "Buyer requested cancellation before dispatch", "2026-07-10T18:20:00+05:30"),
            ),
            createdAt = "2026-07-10T12:00:00+05:30",
        )

        return ids
    }

    private fun seedPayout(store01Id: UUID, order1004Id: UUID) {
        val store = storeRepository.findById(store01Id).orElseThrow()
        val order1004 = orderRepository.findById(order1004Id).orElseThrow()
        val payout = Payout(
            store = store,
            subtotalLkr = 1200,
            platformFeeLkr = 42,
            netLkr = 1158,
            status = PayoutStatus.PAID,
            paidAt = dt("2026-07-18T14:32:00+05:30"),
            bankReference = "CBC-TRF-88213",
        )
        payout.orders.add(
            PayoutOrderRef(
                payout = payout,
                orderId = order1004Id,
                orderNumber = order1004.orderNumber,
                subtotalLkr = 1200,
                platformFeeLkr = 42,
                netLkr = 1158,
            ),
        )
        val saved = payoutRepository.saveAndFlush(payout)
        backdate("payouts", requireNotNull(saved.id), dt("2026-07-18T09:00:00+05:30"), dt("2026-07-18T09:00:00+05:30"))
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
