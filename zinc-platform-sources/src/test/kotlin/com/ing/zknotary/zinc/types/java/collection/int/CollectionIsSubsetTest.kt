package com.ing.zknotary.zinc.types.java.collection.int

import com.ing.zknotary.testing.getZincZKService
import com.ing.zknotary.zinc.types.proveTimed
import com.ing.zknotary.zinc.types.setupTimed
import com.ing.zknotary.zinc.types.toJsonObject
import com.ing.zknotary.zinc.types.verifyTimed
import kotlinx.serialization.json.buildJsonObject
import net.corda.core.utilities.loggerFor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.ExperimentalTime

@ExperimentalTime
class CollectionIsSubsetTest {
    private val log = loggerFor<CollectionIsSubsetTest>()
    private val zincZKService = getZincZKService<CollectionIsSubsetTest>()

    init {
        zincZKService.setupTimed(log)
    }

    @AfterAll
    fun `remove zinc files`() {
        zincZKService.cleanup()
    }

    @ParameterizedTest
    @MethodSource("testData")
    fun `zinc isSubset smoke test for collections of iNT`(testSet1: Set<Int>, testSet2: Set<Int>) {
        val input = buildJsonObject {
            put("left", testSet1.toJsonObject(3))
            put("right", testSet2.toJsonObject(3))
        }.toString()
        val expected = "\"${if (testSet2.containsAll(testSet1)) 0 else 1}\""

        zincZKService.proveTimed(input, log).let {
            zincZKService.verifyTimed(it, expected, log)
        }
    }

    companion object {
        private val intSet = setOf(0, 1)
        private val intSubSet = setOf(1)

        @JvmStatic
        fun testData() = listOf(
            Arguments.of(intSubSet, intSet),
            Arguments.of(intSet, intSet),
            Arguments.of(intSet, intSubSet),
        )
    }
}
