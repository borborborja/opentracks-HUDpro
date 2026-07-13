package cat.rumb.app.data.geo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NominatimClientTest {

    @Test
    fun parsesCityFirst() {
        val json = """{"address":{"city":"Barcelona","town":"X","municipality":"BCN"}}"""
        assertThat(NominatimClient.parseMunicipality(json)).isEqualTo("Barcelona")
    }

    @Test
    fun fallsBackToTownVillageMunicipality() {
        assertThat(NominatimClient.parseMunicipality("""{"address":{"town":"Vic"}}""")).isEqualTo("Vic")
        assertThat(NominatimClient.parseMunicipality("""{"address":{"village":"Rupit"}}""")).isEqualTo("Rupit")
        assertThat(NominatimClient.parseMunicipality("""{"address":{"municipality":"Osona"}}""")).isEqualTo("Osona")
    }

    @Test
    fun returnsNullOnMissingAddressOrGarbage() {
        assertThat(NominatimClient.parseMunicipality("""{"error":"Unable to geocode"}""")).isNull()
        assertThat(NominatimClient.parseMunicipality("not json")).isNull()
        assertThat(NominatimClient.parseMunicipality("""{"address":{}}""")).isNull()
    }
}
