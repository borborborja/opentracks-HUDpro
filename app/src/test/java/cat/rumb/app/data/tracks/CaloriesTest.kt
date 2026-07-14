package cat.rumb.app.data.tracks

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class CaloriesTest {

    private val oneHour = Duration.ofHours(1)

    @Test
    fun metFallbackWhenNoHrOrDemographics() {
        // RUN MET 9.8 × 70kg × 1h = 686
        assertThat(Calories.kcal(ActivityTypes.RUN, 70, oneHour)).isEqualTo(686)
    }

    @Test
    fun usesHrFormulaWhenAgeSexAndHrKnown() {
        // Keytel (male, 30y, 70kg, 150 bpm): (-55.0969 + 0.6309*150 + 0.1988*70 + 0.2017*30)/4.184
        //   = (−55.0969 + 94.635 + 13.916 + 6.051)/4.184 ≈ 59.505/4.184 ≈ 14.22 kcal/min × 60 ≈ 853
        val kc = Calories.kcal(ActivityTypes.RUN, 70, oneHour, avgHr = 150.0, ageYears = 30, sex = "M")
        assertThat(kc).isBetween(840, 865)
        // …and differs from the MET fallback.
        assertThat(kc).isNotEqualTo(Calories.kcal(ActivityTypes.RUN, 70, oneHour))
    }

    @Test
    fun ignoresHrWhenSexUnknown() {
        assertThat(Calories.kcal(ActivityTypes.RUN, 70, oneHour, avgHr = 150.0, ageYears = 30, sex = ""))
            .isEqualTo(686)
    }

    @Test
    fun zeroWhenNoTime() {
        assertThat(Calories.kcal(ActivityTypes.RUN, 70, null)).isEqualTo(0)
    }
}
