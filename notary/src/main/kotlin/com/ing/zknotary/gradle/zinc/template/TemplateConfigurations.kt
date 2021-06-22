package com.ing.zknotary.gradle.zinc.template

import com.ing.serialization.bfl.serializers.DoubleSurrogate
import com.ing.serialization.bfl.serializers.FloatSurrogate
import com.ing.zknotary.gradle.zinc.template.parameters.AbstractPartyTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.AmountTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.AttachmentConstraintTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.BigDecimalTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.ByteArrayTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.CollectionTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.CurrencyTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.IssuedTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.LinearPointerTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.NullableTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.PartyAndReferenceTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.PolyTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.SecureHashTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.StringTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.UniqueIdentifierTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.X500PrincipalTemplateParameters

/**
 * Contains all configurations to be rendered by the [TemplateRenderer].
 *
 * This includes [TemplateParameters] for classes supported by this library (see [fixedTemplateParameters]), but also
 * allows additional configurations for [String]s, [ByteArray]s, BigDecimals, and classes with generics, such as Amount.
 *
 * For more information, see: com.ing.zknotary.gradle.extension.ZKNotaryExtension and PrepareCircuits.
 */
open class TemplateConfigurations {
    companion object {
        val floatTemplateParameters = BigDecimalTemplateParameters(
            FloatSurrogate.FLOAT_INTEGER_SIZE,
            FloatSurrogate.FLOAT_FRACTION_SIZE,
            "Float"
        )
        val doubleTemplateParameters = BigDecimalTemplateParameters(
            DoubleSurrogate.DOUBLE_INTEGER_SIZE,
            DoubleSurrogate.DOUBLE_FRACTION_SIZE,
            "Double"
        )
    }

    open var stringConfigurations: List<StringTemplateParameters> = emptyList()

    open var byteArrayConfigurations: List<ByteArrayTemplateParameters> = emptyList()

    open var bigDecimalConfigurations: List<BigDecimalTemplateParameters> = emptyList()

    open var amountConfigurations: List<AmountTemplateParameters> = emptyList()

    open var issuedConfigurations: List<IssuedTemplateParameters<*>> = emptyList()

    open var nullableConfigurations: List<NullableTemplateParameters<*>> = emptyList()

    open var polyConfigurations: List<PolyTemplateParameters<*>> = emptyList()

    open var collectionConfigurations: List<CollectionTemplateParameters<*>> = emptyList()

    /*
     * Pre-defined collection of configurations to generate zinc sources for
     * standard data types like float and double.
     */
    private val fixedTemplateParameters: List<TemplateParameters> by lazy {
        listOf(
            floatTemplateParameters,
            doubleTemplateParameters,
            UniqueIdentifierTemplateParameters,
            LinearPointerTemplateParameters,
            X500PrincipalTemplateParameters,
            CurrencyTemplateParameters,
            SecureHashTemplateParameters,
            StringTemplateParameters(1), // Needed for platform_serial_name.zn
        ) +
            AbstractPartyTemplateParameters.all +
            PartyAndReferenceTemplateParameters.all +
            AttachmentConstraintTemplateParameters.all +
            AttachmentConstraintTemplateParameters.polymorphic +
            NullableTemplateParameters.fixed
    }

    /**
     * Resolve all distinct [TemplateParameters] for this configuration.
     */
    fun resolveAllTemplateParameters(): List<TemplateParameters> {
        return (
            fixedTemplateParameters +
                stringConfigurations +
                byteArrayConfigurations +
                bigDecimalConfigurations +
                amountConfigurations +
                issuedConfigurations +
                nullableConfigurations +
                polyConfigurations +
                collectionConfigurations
            )
            .flatMap { it.resolveAllConfigurations() }
            .distinct()
    }
}
