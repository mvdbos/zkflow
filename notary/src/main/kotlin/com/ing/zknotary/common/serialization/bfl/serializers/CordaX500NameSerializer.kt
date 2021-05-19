package com.ing.zknotary.common.serialization.bfl.serializers

import com.ing.serialization.bfl.annotations.FixedLength
import com.ing.serialization.bfl.api.Surrogate
import com.ing.serialization.bfl.api.SurrogateSerializer
import kotlinx.serialization.Serializable
import net.corda.core.identity.CordaX500Name

object CordaX500NameSerializer : SurrogateSerializer<CordaX500Name, CordaX500NameSurrogate>(
    CordaX500NameSurrogate.serializer(),
    { CordaX500NameSurrogate.from(it) }
)

@Serializable
data class CordaX500NameSurrogate(
    @FixedLength([CordaX500Name.MAX_LENGTH_COMMON_NAME])
    val commonName: String?,
    @FixedLength([CordaX500Name.MAX_LENGTH_ORGANISATION_UNIT])
    val organisationUnit: String?,
    @FixedLength([CordaX500Name.MAX_LENGTH_ORGANISATION])
    val organisation: String,
    @FixedLength([CordaX500Name.MAX_LENGTH_LOCALITY])
    val locality: String,
    @FixedLength([CordaX500Name.MAX_LENGTH_STATE])
    val state: String?,
    // Country codes are defined in ISO 3166 and are all 2-letter abbreviations.
    @FixedLength([LENGTH_COUNTRY])
    val country: String
) : Surrogate<CordaX500Name> {

    override fun toOriginal() = CordaX500Name(commonName, organisationUnit, organisation, locality, state, country)

    companion object {
        private const val NULLABILITY = 1
        const val LENGTH_COUNTRY = 2
        const val SIZE =
            NULLABILITY + Short.SIZE_BYTES + CordaX500Name.MAX_LENGTH_COMMON_NAME * Char.SIZE_BYTES +
                NULLABILITY + Short.SIZE_BYTES + CordaX500Name.MAX_LENGTH_ORGANISATION_UNIT * Char.SIZE_BYTES +
                Short.SIZE_BYTES + CordaX500Name.MAX_LENGTH_ORGANISATION * Char.SIZE_BYTES +
                Short.SIZE_BYTES + CordaX500Name.MAX_LENGTH_LOCALITY * Char.SIZE_BYTES +
                NULLABILITY + Short.SIZE_BYTES + CordaX500Name.MAX_LENGTH_STATE * Char.SIZE_BYTES +
                Short.SIZE_BYTES + LENGTH_COUNTRY * Char.SIZE_BYTES

        fun from(original: CordaX500Name) = with(original) {
            CordaX500NameSurrogate(commonName, organisationUnit, organisation, locality, state, country)
        }
    }
}
