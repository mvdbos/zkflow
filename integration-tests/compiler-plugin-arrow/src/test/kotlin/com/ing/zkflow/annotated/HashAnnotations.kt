package com.ing.zkflow.annotated

import com.ing.zkflow.annotations.ZKP
import com.ing.zkflow.annotations.corda.HashSize
import com.ing.zkflow.annotations.corda.Sha256
import net.corda.core.crypto.SecureHash

@ZKP
data class HashAnnotations(
    val sha256: @Sha256 SecureHash = SecureHash.zeroHash,
    val fancyHash: @FancyHash SecureHash = SecureHash.HASH("FancyHash", ByteArray(8) { 0 })
)

@Target(AnnotationTarget.TYPE)
@Suppress("MagicNumber")
@HashSize(8)
annotation class FancyHash
