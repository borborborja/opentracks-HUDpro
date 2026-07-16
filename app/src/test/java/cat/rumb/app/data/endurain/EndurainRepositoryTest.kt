package cat.rumb.app.data.endurain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The pure interpretation of the API-key upload-probe. The reported bug was that a valid
 * upload-scoped key got a 401 from a read endpoint it can't use; the probe hits the ONE endpoint it
 * CAN use and reads the status. HTTP I/O and the JWT flow are device-verified.
 */
class EndurainRepositoryTest {

    @Test
    fun aRejectedKeyIs401() {
        assertThat(EndurainRepository.interpretProbe(401)).isEqualTo(ConnResult.BadKey)
    }

    @Test
    fun aValidKeyWithoutTheUploadScopeIs403() {
        assertThat(EndurainRepository.interpretProbe(403)).isEqualTo(ConnResult.MissingScope)
    }

    @Test
    fun aMissingEndpointIs404() {
        assertThat(EndurainRepository.interpretProbe(404)).isEqualTo(ConnResult.BadUrl)
    }

    @Test
    fun authPassedButOurSentinelWasRejectedMeansConnected() {
        // The whole point: a valid key reaches the endpoint; the server only rejects our bogus file.
        // Any of these means auth succeeded, so the key works.
        for (code in listOf(200, 201, 400, 413, 415, 422, 500)) {
            assertThat(EndurainRepository.interpretProbe(code))
                .describedAs("HTTP $code").isEqualTo(ConnResult.Ok(null))
        }
    }
}
