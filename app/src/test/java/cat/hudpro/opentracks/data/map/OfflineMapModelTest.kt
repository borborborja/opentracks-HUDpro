package cat.hudpro.opentracks.data.map

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OfflineMapModelTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun legacyRecordWithoutSectorsDecodes() {
        // A record persisted before per-sector tracking existed.
        val legacy = """[{"name":"ICGC Topogràfic · àrea","path":"/x/a.mbtiles",
            "attribution":"© ICGC","bounds":[1.0,41.0,1.5,41.5]}]"""
        val maps = json.decodeFromString(ListSerializer(OfflineMap.serializer()), legacy)
        assertThat(maps).hasSize(1)
        assertThat(maps[0].sectors).isEmpty()
        assertThat(maps[0].sourceId).isNull()
        assertThat(maps[0].bounds).containsExactly(1.0, 41.0, 1.5, 41.5)
    }

    @Test
    fun roundTripsWithSectors() {
        val map = OfflineMap(
            name = "ICGC Topogràfic",
            path = "/x/offline_icgc_topografic.mbtiles",
            sourceId = "icgc_topografic",
            bounds = listOf(1.0, 41.0, 2.0, 42.0),
            sectors = listOf(
                OfflineSector("a", listOf(1.0, 41.0, 1.5, 41.5), 9, 14, 1200, 100),
                OfflineSector("b", listOf(1.5, 41.5, 2.0, 42.0), 9, 14, 800, 200),
            ),
        )
        val encoded = json.encodeToString(OfflineMap.serializer(), map)
        assertThat(json.decodeFromString(OfflineMap.serializer(), encoded)).isEqualTo(map)
    }
}
