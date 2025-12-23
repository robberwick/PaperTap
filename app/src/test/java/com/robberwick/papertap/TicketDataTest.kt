package com.robberwick.papertap

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TicketData class, focusing on journey summary formatting
 */
class TicketDataTest {

    @Test
    fun getJourneySummary_withValidDateTime_showsBoth() {
        val ticketData = TicketData(
            originStation = "London Euston",
            destinationStation = "Manchester Piccadilly",
            travelDate = "2024-01-15",
            travelTime = "14:30",
            ticketType = "Advance Single",
            railcardType = null,
            ticketClass = "Standard",
            rawData = "test"
        )

        assertEquals(
            "London Euston → Manchester Piccadilly | 2024-01-15 14:30",
            ticketData.getJourneySummary()
        )
    }

    @Test
    fun getJourneySummary_withZeroTime_hidesTime() {
        val ticketData = TicketData(
            originStation = "London Euston",
            destinationStation = "Manchester Piccadilly",
            travelDate = "2024-01-15",
            travelTime = "00:00",
            ticketType = "Anytime Day Single",
            railcardType = null,
            ticketClass = "Standard",
            rawData = "test"
        )

        assertEquals(
            "London Euston → Manchester Piccadilly | 2024-01-15",
            ticketData.getJourneySummary()
        )
    }

    @Test
    fun getJourneySummary_withEmptyTime_showsOnlyDate() {
        val ticketData = TicketData(
            originStation = "London Euston",
            destinationStation = "Manchester Piccadilly",
            travelDate = "2024-01-15",
            travelTime = "",
            ticketType = "Off-Peak Single",
            railcardType = null,
            ticketClass = "Standard",
            rawData = "test"
        )

        assertEquals(
            "London Euston → Manchester Piccadilly | 2024-01-15",
            ticketData.getJourneySummary()
        )
    }

    @Test
    fun getJourneySummary_withNullTime_showsOnlyDate() {
        val ticketData = TicketData(
            originStation = "London Euston",
            destinationStation = "Manchester Piccadilly",
            travelDate = "2024-01-15",
            travelTime = null,
            ticketType = "Off-Peak Single",
            railcardType = null,
            ticketClass = "Standard",
            rawData = "test"
        )

        assertEquals(
            "London Euston → Manchester Piccadilly | 2024-01-15",
            ticketData.getJourneySummary()
        )
    }

    @Test
    fun getJourneySummary_withEmptyDate_showsOnlyRoute() {
        val ticketData = TicketData(
            originStation = "London Euston",
            destinationStation = "Manchester Piccadilly",
            travelDate = "",
            travelTime = "14:30",
            ticketType = "Season Ticket",
            railcardType = null,
            ticketClass = "Standard",
            rawData = "test"
        )

        assertEquals(
            "London Euston → Manchester Piccadilly",
            ticketData.getJourneySummary()
        )
    }

    @Test
    fun getJourneySummary_withNullDate_showsOnlyRoute() {
        val ticketData = TicketData(
            originStation = "London Euston",
            destinationStation = "Manchester Piccadilly",
            travelDate = null,
            travelTime = "14:30",
            ticketType = "Season Ticket",
            railcardType = null,
            ticketClass = "Standard",
            rawData = "test"
        )

        assertEquals(
            "London Euston → Manchester Piccadilly",
            ticketData.getJourneySummary()
        )
    }

    @Test
    fun getJourneySummary_withNullStations_showsUnknown() {
        val ticketData = TicketData(
            originStation = null,
            destinationStation = null,
            travelDate = "2024-01-15",
            travelTime = "14:30",
            ticketType = "Advance Single",
            railcardType = null,
            ticketClass = "Standard",
            rawData = "test"
        )

        assertEquals(
            "Unknown → Unknown | 2024-01-15 14:30",
            ticketData.getJourneySummary()
        )
    }

    @Test
    fun getJourneySummary_withAllNulls_showsUnknownRoute() {
        val ticketData = TicketData(
            originStation = null,
            destinationStation = null,
            travelDate = null,
            travelTime = null,
            ticketType = null,
            railcardType = null,
            ticketClass = null,
            rawData = "test"
        )

        assertEquals(
            "Unknown → Unknown",
            ticketData.getJourneySummary()
        )
    }

    // Note: JSON serialization tests require Android framework classes (org.json.JSONObject)
    // and would need Robolectric or instrumented tests to run properly.
    // The core journey summary formatting logic is tested above.
}
