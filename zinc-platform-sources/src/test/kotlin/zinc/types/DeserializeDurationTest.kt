package zinc.types

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
class DeserializeDurationTest {
    private val zincZKService = getZincZKService<DeserializeDurationTest>()

    @Test
    fun `an Instance should be deserialized correctly`() {
        val data = Data(Duration.ofSeconds(42, 79))
        val witness = toWitness(data)

        val expected = data.data.toZincJson()
        zincZKService.run(witness, expected)
    }

    @Serializable
    private data class Data(
        val data: @Contextual Duration
    )
}
