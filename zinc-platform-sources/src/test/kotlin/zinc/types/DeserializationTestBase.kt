package zinc.types

import com.ing.zknotary.common.zkp.ZincZKService
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

abstract class DeserializationTestBase<T : DeserializationTestBase<T, D>, D : Any>(
    private val zincJsonSerializer: ZincJsonSerializer<D>
) {
    abstract fun getZincZKService(): ZincZKService

    open fun getSerializersModule(): SerializersModule = EmptySerializersModule

    private val zinc by lazy { getZincZKService() }

    @ParameterizedTest
    @MethodSource("testData")
    fun performDeserializationTest(data: D) {
        val witness = toObliviousWitness(data, getSerializersModule())

        val expected = zincJsonSerializer.toZincJson(data)
        val actual = zinc.run(witness, "")

        val expectedJson = Json.parseToJsonElement(expected)
        val actualJson = Json.parseToJsonElement(actual)

        actualJson shouldBe expectedJson
    }

    fun interface ZincJsonSerializer<D> {
        fun toZincJson(data: D): String
    }
}
