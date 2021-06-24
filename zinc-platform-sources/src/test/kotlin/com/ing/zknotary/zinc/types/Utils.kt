package com.ing.zknotary.zinc.types

import com.ing.serialization.bfl.annotations.FixedLength
import com.ing.serialization.bfl.api.reified.serialize
import com.ing.serialization.bfl.serializers.BFLSerializers
import com.ing.serialization.bfl.serializers.CurrencySerializer
import com.ing.zknotary.common.serialization.bfl.corda.AmountSerializer
import com.ing.zknotary.common.serialization.bfl.serializers.CordaSerializers
import com.ing.zknotary.common.zkp.PublicInput
import com.ing.zknotary.common.zkp.Witness
import com.ing.zknotary.common.zkp.ZincZKService
import com.ing.zknotary.testing.bytesToWitness
import com.ing.zknotary.testing.toJsonArray
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.plus
import net.corda.core.contracts.Amount
import org.slf4j.Logger
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Currency
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import com.ing.serialization.bfl.api.serialize as obliviousSerialize

inline fun <reified T : Any, reified U : Any> toWitness(left: Amount<T>, right: Amount<U>) =
    buildJsonObject {
        put("left", left.toJsonObject())
        put("right", right.toJsonObject())
    }.toString()

inline fun <reified T : Any, reified U : Any> toSerializedWitness(left: Amount<T>, right: Amount<U>) =
    buildJsonObject {
        put("left", left.toJsonArray())
        put("right", right.toJsonArray())
    }.toString()

fun toWitness(left: BigDecimal, right: BigDecimal) = buildJsonObject {
    put("left", left.toJsonObject())
    put("right", right.toJsonObject())
}.toString()

fun toSerializedWitness(left: BigDecimal, right: BigDecimal) = buildJsonObject {
    put("left", left.toJsonArray())
    put("right", right.toJsonArray())
}.toString()

fun toBigWitness(left: BigDecimal, right: BigDecimal) = buildJsonObject {
    put("left", left.toJsonObject(100, 20))
    put("right", right.toJsonObject(100, 20))
}.toString()

inline fun <reified T : Any> toWitness(item: T): String {
    val bytes = serialize(item, serializersModule = CordaSerializers.module)
    return bytesToWitness(bytes)
}

fun <T : Any> toObliviousWitness(item: T, serializersModule: SerializersModule = EmptySerializersModule): String {
    val bytes = obliviousSerialize(item, serializersModule = CordaSerializers.module + serializersModule)
    return bytesToWitness(bytes)
}

@Serializable
data class WrappedAmountString(
    val wrappedValue: @Contextual Amount<String>
)

@Serializable
data class WrappedAmountCurrency(
    val wrappedValue: @Contextual Amount<@Contextual Currency>
)

inline fun <reified T : Any> Amount<T>.toJsonArray() =
    when (this.token) {
        is String -> serialize(
            WrappedAmountString(this as Amount<String>), WrappedAmountString.serializer(),
            SerializersModule {
                contextual(AmountSerializer(SmallStringSerializer))
                contextual(SmallStringSerializer)
            }
        )
        is Currency -> serialize(
            WrappedAmountCurrency(this as Amount<Currency>), WrappedAmountCurrency.serializer(),
            SerializersModule {
                contextual(AmountSerializer(CurrencySerializer))
            }
        )
        else -> throw IllegalArgumentException("Amount<${this.token.javaClass.simpleName}> is not supported")
    }.toJsonArray()

@Serializable
private data class WrappedBigDecimal(
    @FixedLength([24, 6])
    val wrappedValue: @Contextual BigDecimal
)

private fun BigDecimal.wrap(): WrappedBigDecimal = WrappedBigDecimal(this)

fun BigDecimal.toJsonArray() = serialize(this.wrap(), serializersModule = BFLSerializers).toJsonArray()

fun makeBigDecimal(bytes: ByteArray, sign: Int) = BigDecimal(BigInteger(sign, bytes))

fun makeBigDecimal(string: String, scale: Int) = BigDecimal(BigInteger(string), scale)

@ExperimentalTime
fun ZincZKService.setupTimed(log: Logger) {
    val time = measureTime {
        this.setup()
    }
    log.debug("[setup] $time")
}

@ExperimentalTime
fun ZincZKService.proveTimed(witness: Witness, log: Logger): ByteArray {
    var proof: ByteArray
    val time = measureTime {
        proof = this.prove(witness)
    }
    log.debug("[prove] $time")
    return proof
}

@ExperimentalTime
fun ZincZKService.proveTimed(witnessJson: String, log: Logger): ByteArray {
    var proof: ByteArray
    val time = measureTime {
        proof = this.prove(witnessJson)
    }
    log.debug("[prove] $time")
    return proof
}

@ExperimentalTime
fun ZincZKService.verifyTimed(proof: ByteArray, publicInputJson: String, log: Logger) {
    val time = measureTime {
        this.verify(proof, publicInputJson)
    }
    log.debug("[verify] $time")
}

@ExperimentalTime
fun ZincZKService.verifyTimed(proof: ByteArray, publicInput: PublicInput, log: Logger) {
    val time = measureTime {
        this.verify(proof, publicInput)
    }
    log.debug("[verify] $time")
}
