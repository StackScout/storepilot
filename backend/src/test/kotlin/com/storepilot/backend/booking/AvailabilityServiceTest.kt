package com.storepilot.backend.booking

import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            category = StoreCategory.BEAUTY,
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
        ).apply { id = storeId }
        bookableService = BookableService(
            store = store,
            name = "Haircut",
            slug = "haircut",
            description = "A haircut",
            category = StoreCategory.BEAUTY,
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
}
