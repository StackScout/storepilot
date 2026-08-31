package com.storepilot.backend.booking

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAccessService
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreStaffMemberRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Optional
import java.util.UUID

class AvailabilityServiceTest {
    private val storeAvailabilityRepository = mockk<StoreAvailabilityRepository>()
    private val weeklyAvailabilityRuleRepository = mockk<WeeklyAvailabilityRuleRepository>()
    private val availabilityExceptionRepository = mockk<AvailabilityExceptionRepository>()
    private val serviceWeeklyAvailabilityRuleRepository = mockk<ServiceWeeklyAvailabilityRuleRepository>()
    private val bookableServiceRepository = mockk<BookableServiceRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val platformConfigService = mockk<PlatformConfigService>()
    private val storeStaffMemberRepository = mockk<StoreStaffMemberRepository>(relaxed = true)
    private val storeAccessService = StoreAccessService(currentActor, storeStaffMemberRepository)

    private val service = AvailabilityService(
        storeAvailabilityRepository,
        weeklyAvailabilityRuleRepository,
        availabilityExceptionRepository,
        serviceWeeklyAvailabilityRuleRepository,
        bookableServiceRepository,
        bookingRepository,
        storeRepository,
        currentActor,
        platformConfigService,
        storeAccessService,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private val storeId = UUID.randomUUID()
    private lateinit var store: Store
    private val serviceId = UUID.randomUUID()
    private lateinit var bookableService: BookableService

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "test-salon",
            name = "Test Salon",
            tagline = "tagline",
            description = "description",
            category = "beauty",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
        ).apply { id = storeId }
        bookableService = BookableService(
            store = store,
            name = "Haircut",
            slug = "haircut",
            description = "A haircut",
            category = "beauty",
            price = 5000,
            durationMinutes = 60,
            status = ServiceStatus.ACTIVE,
        ).apply { id = serviceId }
        every { bookableServiceRepository.findById(serviceId) } returns Optional.of(bookableService)
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { currentActor.requireSeller() } returns seller
        every { platformConfigService.current() } returns mockk<PlatformSettings> { every { timezone } returns "Australia/Sydney" }
        every { storeAvailabilityRepository.findById(storeId) } returns Optional.of(StoreAvailability(store = store, leadTimeMinutes = 0))
        every { availabilityExceptionRepository.findByStoreIdAndDateBetween(any(), any(), any()) } returns emptyList()
        every { bookingRepository.findByServiceIdAndStatusNotInAndScheduledStartLessThanAndScheduledEndGreaterThan(any(), any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `upsertServiceOverride replaces all 7 rows and turns hasCustomAvailability on`() {
        val savedRules = slot<List<ServiceWeeklyAvailabilityRule>>()
        every { serviceWeeklyAvailabilityRuleRepository.deleteByServiceId(serviceId) } returns Unit
        every { serviceWeeklyAvailabilityRuleRepository.saveAll(capture(savedRules)) } answers { savedRules.captured }
        every { bookableServiceRepository.save(any()) } answers { firstArg() }
        every { serviceWeeklyAvailabilityRuleRepository.findByServiceIdOrderByDayOfWeekAsc(serviceId) } answers { savedRules.captured }

        val input = ServiceAvailabilityOverrideInput(
            rules = (1..7).map { WeeklyAvailabilityRuleInput(dayOfWeek = it, isOpen = it <= 5, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(17, 0)) },
        )
        val response = service.upsertServiceOverride(serviceId, input)

        assertTrue(response.hasCustomAvailability)
        assertEquals(7, response.weeklyRules.size)
        assertTrue(bookableService.hasCustomAvailability)
        assertEquals(7, savedRules.captured.size)
    }

    @Test
    fun `disableServiceOverride turns the flag off and deletes override rows`() {
        bookableService.hasCustomAvailability = true
        every { bookableServiceRepository.save(any()) } answers { firstArg() }
        every { serviceWeeklyAvailabilityRuleRepository.deleteByServiceId(serviceId) } returns Unit

        service.disableServiceOverride(serviceId)

        assertFalse(bookableService.hasCustomAvailability)
    }

    @Test
    fun `computeSlots uses the store's weekly rule when the service has no override`() {
        bookableService.hasCustomAvailability = false
        // Always the next Monday strictly after "today" — a fixed calendar date would eventually
        // fall in the past and get filtered out by computeSlots' lead-time cutoff (Instant.now()).
        val monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns listOf(
            WeeklyAvailabilityRule(store = store, dayOfWeek = DayOfWeek.MONDAY, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(10, 0)),
        )

        val days = service.computeSlots(storeId, serviceId, monday, monday)

        assertEquals(1, days.single().slots.size)
    }

    @Test
    fun `computeSlots uses the service's own override instead of the store's weekly rule when hasCustomAvailability is true`() {
        bookableService.hasCustomAvailability = true
        // Always the next Monday strictly after "today" — a fixed calendar date would eventually
        // fall in the past and get filtered out by computeSlots' lead-time cutoff (Instant.now()).
        val monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        // Store says Monday is closed — if the override weren't honored, this would yield zero slots.
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns listOf(
            WeeklyAvailabilityRule(store = store, dayOfWeek = DayOfWeek.MONDAY, isOpen = false),
        )
        every { serviceWeeklyAvailabilityRuleRepository.findByServiceIdOrderByDayOfWeekAsc(serviceId) } returns listOf(
            ServiceWeeklyAvailabilityRule(service = bookableService, dayOfWeek = DayOfWeek.MONDAY, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(10, 0)),
        )

        val days = service.computeSlots(storeId, serviceId, monday, monday)

        assertEquals(1, days.single().slots.size)
    }

    @Test
    fun `computeSlots still honors a store exception even when the service has a custom weekly override`() {
        bookableService.hasCustomAvailability = true
        // Always the next Monday strictly after "today" — a fixed calendar date would eventually
        // fall in the past and get filtered out by computeSlots' lead-time cutoff (Instant.now()).
        val monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        every { serviceWeeklyAvailabilityRuleRepository.findByServiceIdOrderByDayOfWeekAsc(serviceId) } returns listOf(
            ServiceWeeklyAvailabilityRule(service = bookableService, dayOfWeek = DayOfWeek.MONDAY, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(10, 0)),
        )
        every { availabilityExceptionRepository.findByStoreIdAndDateBetween(storeId, monday, monday) } returns listOf(
            AvailabilityException(store = store, date = monday, isOpen = false, note = "Closed for a public holiday").apply { id = UUID.randomUUID(); createdAt = Instant.now() },
        )

        val days = service.computeSlots(storeId, serviceId, monday, monday)

        assertTrue(days.single().slots.isEmpty())
    }

    @Test
    fun `computeSlots excludes a slot that overlaps an existing booking of the same service`() {
        bookableService.hasCustomAvailability = false
        val monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns listOf(
            WeeklyAvailabilityRule(store = store, dayOfWeek = DayOfWeek.MONDAY, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(11, 0)),
        )
        val slotStart = monday.atTime(9, 0).atZone(java.time.ZoneId.of("Australia/Sydney")).toInstant()
        val existingBooking = Booking(
            bookingNumber = "BK-AU-20260101-1000", store = store, service = bookableService, serviceName = "Haircut", servicePrice = 5000,
            serviceDurationMinutes = 60, scheduledStart = slotStart, scheduledEnd = slotStart.plusSeconds(3600), platformFee = 100, total = 5000,
            status = BookingStatus.CONFIRMED, paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID,
            buyerName = "Jane", buyerPhone = "+61400000002", buyerEmail = "jane@example.com",
        ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { bookingRepository.findByServiceIdAndStatusNotInAndScheduledStartLessThanAndScheduledEndGreaterThan(any(), any(), any(), any()) } returns listOf(existingBooking)

        val days = service.computeSlots(storeId, serviceId, monday, monday)

        assertEquals(0, days.single().slots.count { it.start == slotStart })
    }

    @Test
    fun `computeSlots excludes a slot inside the lead-time cutoff`() {
        bookableService.hasCustomAvailability = false
        // 30 days comfortably exceeds how far out "next Monday" can ever fall (at most 7 days), so every slot is guaranteed inside the cutoff regardless of today's weekday.
        every { storeAvailabilityRepository.findById(storeId) } returns Optional.of(StoreAvailability(store = store, leadTimeMinutes = 30 * 24 * 60))
        val monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns listOf(
            WeeklyAvailabilityRule(store = store, dayOfWeek = DayOfWeek.MONDAY, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(10, 0)),
        )

        val days = service.computeSlots(storeId, serviceId, monday, monday)

        assertTrue(days.single().slots.isEmpty())
    }

    @Test
    fun `computeSlots rejects a service that doesn't belong to the given store`() {
        val otherStore = Store(
            seller = seller, slug = "other-store", name = "Other Store", tagline = "tagline", description = "description",
            category = "beauty", address = StoreAddress(city = "Sydney", state = "NSW"), whatsappNumber = "+61400000000",
        ).apply { id = UUID.randomUUID() }
        val otherServiceId = UUID.randomUUID()
        val otherService = BookableService(
            store = otherStore, name = "Massage", slug = "massage", description = "description", category = "beauty",
            price = 8000, durationMinutes = 60, status = ServiceStatus.ACTIVE,
        ).apply { id = otherServiceId }
        every { bookableServiceRepository.findById(otherServiceId) } returns Optional.of(otherService)

        assertThrows(IllegalArgumentException::class.java) { service.computeSlots(storeId, otherServiceId, LocalDate.now(), LocalDate.now()) }
    }

    @Test
    fun `computeSlots throws for a missing service`() {
        val missingId = UUID.randomUUID()
        every { bookableServiceRepository.findById(missingId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.computeSlots(storeId, missingId, LocalDate.now(), LocalDate.now()) }
    }

    @Test
    fun `get assembles the lead time, weekly rules, and exceptions`() {
        every { storeAvailabilityRepository.findById(storeId) } returns Optional.of(StoreAvailability(store = store, leadTimeMinutes = 90))
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns listOf(
            WeeklyAvailabilityRule(store = store, dayOfWeek = DayOfWeek.MONDAY, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(17, 0)),
        )
        every { availabilityExceptionRepository.findByStoreIdOrderByDateAsc(storeId) } returns emptyList()

        val response = service.get(storeId)

        assertEquals(90, response.leadTimeMinutes)
        assertEquals(1, response.weeklyRules.size)
    }

    @Test
    fun `get defaults the lead time to 120 minutes when unset`() {
        every { storeAvailabilityRepository.findById(storeId) } returns Optional.empty()
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns emptyList()
        every { availabilityExceptionRepository.findByStoreIdOrderByDateAsc(storeId) } returns emptyList()

        assertEquals(120, service.get(storeId).leadTimeMinutes)
    }

    private fun weeklyInput(leadTimeMinutes: Int? = null, override: (Int) -> WeeklyAvailabilityRuleInput = { WeeklyAvailabilityRuleInput(dayOfWeek = it, isOpen = it <= 5, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(17, 0)) }) =
        WeeklyAvailabilityInput(rules = (1..7).map(override), leadTimeMinutes = leadTimeMinutes)

    @Test
    fun `upsertWeeklyRules rejects rules that don't cover every weekday exactly once`() {
        val input = WeeklyAvailabilityInput(rules = (1..6).map { WeeklyAvailabilityRuleInput(dayOfWeek = it, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(17, 0)) })

        assertThrows(IllegalArgumentException::class.java) { service.upsertWeeklyRules(storeId, input) }
    }

    @Test
    fun `upsertWeeklyRules rejects an open day with no valid open-close window`() {
        val input = weeklyInput(override = { WeeklyAvailabilityRuleInput(dayOfWeek = it, isOpen = true, openTime = null, closeTime = null) })

        assertThrows(IllegalArgumentException::class.java) { service.upsertWeeklyRules(storeId, input) }
    }

    @Test
    fun `upsertWeeklyRules rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.upsertWeeklyRules(storeId, weeklyInput()) }
    }

    @Test
    fun `upsertWeeklyRules replaces the 7 rows and updates the lead time`() {
        every { weeklyAvailabilityRuleRepository.deleteByStoreId(storeId) } returns Unit
        every { weeklyAvailabilityRuleRepository.saveAll(any<List<WeeklyAvailabilityRule>>()) } answers { firstArg() }
        val availability = StoreAvailability(store = store, leadTimeMinutes = 60)
        every { storeAvailabilityRepository.findById(storeId) } returns Optional.of(availability)
        every { storeAvailabilityRepository.save(any()) } answers { firstArg() }
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns emptyList()
        every { availabilityExceptionRepository.findByStoreIdOrderByDateAsc(storeId) } returns emptyList()

        service.upsertWeeklyRules(storeId, weeklyInput(leadTimeMinutes = 45))

        assertEquals(45, availability.leadTimeMinutes)
        verify { weeklyAvailabilityRuleRepository.deleteByStoreId(storeId) }
    }

    @Test
    fun `upsertWeeklyRules creates a StoreAvailability row when none exists yet`() {
        every { weeklyAvailabilityRuleRepository.deleteByStoreId(storeId) } returns Unit
        every { weeklyAvailabilityRuleRepository.saveAll(any<List<WeeklyAvailabilityRule>>()) } answers { firstArg() }
        every { storeAvailabilityRepository.findById(storeId) } returns Optional.empty()
        every { storeAvailabilityRepository.save(any()) } answers { firstArg() }
        every { weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId) } returns emptyList()
        every { availabilityExceptionRepository.findByStoreIdOrderByDateAsc(storeId) } returns emptyList()

        service.upsertWeeklyRules(storeId, weeklyInput())

        verify { storeAvailabilityRepository.save(any()) }
    }

    private fun exceptionInput(date: LocalDate = LocalDate.now(), isOpen: Boolean = false, openTime: LocalTime? = null, closeTime: LocalTime? = null, note: String? = "Public holiday") =
        AvailabilityExceptionInput(date = date, isOpen = isOpen, openTime = openTime, closeTime = closeTime, note = note)

    @Test
    fun `createException rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.createException(storeId, exceptionInput()) }
    }

    @Test
    fun `createException rejects an open exception with no valid window`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.createException(storeId, exceptionInput(isOpen = true, openTime = null, closeTime = null))
        }
    }

    @Test
    fun `createException creates a new row when no exception exists for the date`() {
        val date = LocalDate.now()
        every { availabilityExceptionRepository.findByStoreIdAndDate(storeId, date) } returns null
        every { availabilityExceptionRepository.save(any()) } answers { (firstArg() as AvailabilityException).apply { id = UUID.randomUUID(); createdAt = Instant.now() } }

        val response = service.createException(storeId, exceptionInput(date = date))

        assertEquals(date, response.date)
        assertFalse(response.isOpen)
    }

    @Test
    fun `createException overwrites an existing exception for the same date`() {
        val date = LocalDate.now()
        val existing = AvailabilityException(store = store, date = date, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(12, 0))
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { availabilityExceptionRepository.findByStoreIdAndDate(storeId, date) } returns existing
        every { availabilityExceptionRepository.save(any()) } answers { firstArg() }

        val response = service.createException(storeId, exceptionInput(date = date, isOpen = false, note = "Now closed"))

        assertFalse(response.isOpen)
        assertEquals("Now closed", response.note)
    }

    @Test
    fun `deleteException throws for a missing exception`() {
        val id = UUID.randomUUID()
        every { availabilityExceptionRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.deleteException(storeId, id) }
    }

    @Test
    fun `deleteException rejects an exception belonging to a different store`() {
        val otherStore = Store(
            seller = seller, slug = "other-store", name = "Other Store", tagline = "tagline", description = "description",
            category = "beauty", address = StoreAddress(city = "Sydney", state = "NSW"), whatsappNumber = "+61400000000",
        ).apply { id = UUID.randomUUID() }
        val exception = AvailabilityException(store = otherStore, date = LocalDate.now(), isOpen = false).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { availabilityExceptionRepository.findById(exception.id!!) } returns Optional.of(exception)

        assertThrows(IllegalArgumentException::class.java) { service.deleteException(storeId, exception.id!!) }
    }

    @Test
    fun `deleteException removes the store's own exception`() {
        val exception = AvailabilityException(store = store, date = LocalDate.now(), isOpen = false).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { availabilityExceptionRepository.findById(exception.id!!) } returns Optional.of(exception)
        every { availabilityExceptionRepository.delete(exception) } returns Unit

        service.deleteException(storeId, exception.id!!)

        verify { availabilityExceptionRepository.delete(exception) }
    }

    @Test
    fun `getServiceOverride returns the current override state`() {
        bookableService.hasCustomAvailability = true
        every { serviceWeeklyAvailabilityRuleRepository.findByServiceIdOrderByDayOfWeekAsc(serviceId) } returns listOf(
            ServiceWeeklyAvailabilityRule(service = bookableService, dayOfWeek = DayOfWeek.MONDAY, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(17, 0)),
        )

        val response = service.getServiceOverride(serviceId)

        assertTrue(response.hasCustomAvailability)
        assertEquals(1, response.weeklyRules.size)
    }

    @Test
    fun `upsertServiceOverride rejects rules that don't cover every weekday exactly once`() {
        val input = ServiceAvailabilityOverrideInput(rules = (1..6).map { WeeklyAvailabilityRuleInput(dayOfWeek = it, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(17, 0)) })

        assertThrows(IllegalArgumentException::class.java) { service.upsertServiceOverride(serviceId, input) }
    }

    @Test
    fun `upsertServiceOverride rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        val input = ServiceAvailabilityOverrideInput(rules = (1..7).map { WeeklyAvailabilityRuleInput(dayOfWeek = it, isOpen = true, openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(17, 0)) })

        assertThrows(ForbiddenException::class.java) { service.upsertServiceOverride(serviceId, input) }
    }
}
