package pl.filebit.dietetyk.core.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.Gender

class BodyFatEstimatorTest {

    @Test
    fun `Deurenberg z BMI gdy brak obwodow`() {
        // 80 kg / 1.80 m -> BMI 24.69; M, 30 lat -> 20.3
        val e = BodyFatEstimator.estimate(
            weightKg = 80.0, heightCm = 180, ageYears = 30, gender = Gender.MALE
        )!!
        assertEquals(BodyFatMethod.DEURENBERG, e.method)
        assertEquals(20.3, e.percent, 0.2)
    }

    @Test
    fun `Navy ma pierwszenstwo gdy sa obwody (mezczyzna)`() {
        val e = BodyFatEstimator.estimate(
            weightKg = 80.0, heightCm = 180, ageYears = 30, gender = Gender.MALE,
            waistCm = 85.0, neckCm = 40.0
        )!!
        assertEquals(BodyFatMethod.NAVY, e.method)
        assertEquals(14.5, e.percent, 0.4)
    }

    @Test
    fun `Navy dla kobiety wymaga bioder`() {
        val withoutHips = BodyFatEstimator.estimate(
            weightKg = 62.0, heightCm = 165, ageYears = 30, gender = Gender.FEMALE,
            waistCm = 75.0, neckCm = 33.0
        )
        assertEquals(BodyFatMethod.DEURENBERG, withoutHips!!.method)

        val withHips = BodyFatEstimator.estimate(
            weightKg = 62.0, heightCm = 165, ageYears = 30, gender = Gender.FEMALE,
            waistCm = 75.0, neckCm = 33.0, hipsCm = 95.0
        )!!
        assertEquals(BodyFatMethod.NAVY, withHips.method)
        assertTrue("kobieta zwykle wyższy %BF", withHips.percent in 22.0..32.0)
    }

    @Test
    fun `brak wagi i obwodow daje null`() {
        assertNull(
            BodyFatEstimator.estimate(
                weightKg = null, heightCm = 180, ageYears = 30, gender = Gender.MALE
            )
        )
    }

    @Test
    fun `absurdalne dane odrzucone (poza zakresem 2-70 proc)`() {
        val e = BodyFatEstimator.estimate(
            weightKg = 80.0, heightCm = 180, ageYears = 30, gender = Gender.MALE,
            waistCm = 40.5, neckCm = 40.0
        )
        assertTrue(e == null || e.method == BodyFatMethod.DEURENBERG)
    }
}
