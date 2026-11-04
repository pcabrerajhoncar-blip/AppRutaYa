package com.rutaya.app.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesManagerJsonTest {

    @Test
    fun readsLegacyFavoriteFormat() {
        val result = FavoritesManager.parseFavoritesJson("[" + favorite("legacy-id", "18", "") + "]")

        assertFalse(result.hasReadProblems)
        assertEquals(1, result.favorites.size)
        val favorite = result.favorites[0]
        assertEquals("legacy-id", favorite.favoriteId)
        assertEquals(18, favorite.routeOption!!.timeMinutes)
        assertEquals(-1, favorite.routeOption!!.vehicleTimeMinutes)
    }

    @Test
    fun readsNewFavoriteFormatWithoutLosingSegmentMetrics() {
        val segment = ",\"vehicleTimeMinutes\":9,\"totalTimeMinutes\":-1" +
            ",\"boardingStopName\":\"Parque\",\"boardingStopOrder\":2" +
            ",\"alightingStopName\":\"Campus\",\"alightingStopOrder\":3" +
            ",\"warnings\":[\"DEMO_DATA_NOT_OFFICIAL\"]"
        val result = FavoritesManager.parseFavoritesJson("[" + favorite("new-id", "-1", segment) + "]")

        assertFalse(result.hasReadProblems)
        val route = result.favorites[0].routeOption!!
        assertEquals(-1, route.timeMinutes)
        assertEquals(9, route.vehicleTimeMinutes)
        assertEquals(-1, route.totalTimeMinutes)
        assertEquals("Parque", route.boardingStopName)
    }

    @Test
    fun malformedJsonIsReportedWithoutBecomingAValidEmptyList() {
        val result = FavoritesManager.parseFavoritesJson("[{broken json]")

        assertTrue(result.hasMalformedJson)
        assertTrue(result.hasReadProblems)
        assertTrue(result.favorites.isEmpty())
    }

    @Test
    fun incompleteRecordsAreReportedAndOtherRecordsRemainReadable() {
        val incomplete = "{\"favoriteId\":\"broken\",\"origin\":\"Parque\",\"destination\":\"Campus\"}"
        val result = FavoritesManager.parseFavoritesJson(
            "[" + favorite("valid-id", "12", "") + "," + incomplete + "]"
        )

        assertFalse(result.hasMalformedJson)
        assertEquals(1, result.invalidRecordCount)
        assertEquals(1, result.favorites.size)
        assertEquals("valid-id", result.favorites[0].favoriteId)
    }

    @Test
    fun duplicateFavoritesRemainDistinctAndReadable() {
        val first = favorite("duplicate-id", "12", "")
        val second = favorite("duplicate-id", "15", "")
        val result = FavoritesManager.parseFavoritesJson("[$first,$second]")

        assertFalse(result.hasReadProblems)
        assertEquals(2, result.favorites.size)
        assertEquals(15, result.favorites[0].routeOption!!.timeMinutes)
        assertEquals(12, result.favorites[1].routeOption!!.timeMinutes)
    }

    private fun favorite(favoriteId: String, timeMinutes: String, extraFields: String): String =
        "{\"favoriteId\":\"$favoriteId\",\"origin\":\"Parque\"," +
            "\"destination\":\"Campus\",\"savedTimestamp\":123,\"routeOption\":{" +
            "\"id\":\"1\",\"routeName\":\"Ruta DEMO\"," +
            "\"transportType\":\"Demo urbano\",\"timeMinutes\":$timeMinutes," +
            "\"cost\":2.5,\"walkingMeters\":-1,\"badgeTag\":null," +
            "\"stepsDescription\":\"Aborda en Parque\",\"departureInterval\":\"\"" +
            extraFields + "}}"
}
