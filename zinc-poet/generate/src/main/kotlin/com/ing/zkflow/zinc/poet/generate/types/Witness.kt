package com.ing.zkflow.zinc.poet.generate.types

import com.ing.zinc.bfl.BflModule
import com.ing.zinc.bfl.BflPrimitive
import com.ing.zinc.bfl.BflType
import com.ing.zinc.bfl.CONSTS
import com.ing.zinc.bfl.CORDA_MAGIC_BITS_SIZE_CONSTANT
import com.ing.zinc.bfl.TypeVisitor
import com.ing.zinc.bfl.generator.CodeGenerationOptions
import com.ing.zinc.bfl.generator.WitnessGroupOptions
import com.ing.zinc.bfl.getLengthConstant
import com.ing.zinc.bfl.getSerializedTypeDef
import com.ing.zinc.bfl.toZincId
import com.ing.zinc.naming.camelToSnakeCase
import com.ing.zinc.poet.Indentation.Companion.spaces
import com.ing.zinc.poet.ZincArray
import com.ing.zinc.poet.ZincArray.Companion.zincArray
import com.ing.zinc.poet.ZincFile
import com.ing.zinc.poet.ZincFunction
import com.ing.zinc.poet.ZincMethod.Companion.zincMethod
import com.ing.zinc.poet.ZincPrimitive
import com.ing.zinc.poet.ZincType
import com.ing.zinc.poet.ZincType.Companion.id
import com.ing.zinc.poet.indent
import com.ing.zkflow.common.contracts.ZKTransactionMetadataCommandData
import com.ing.zkflow.util.bitsToByteBoundary
import com.ing.zkflow.zinc.poet.generate.types.CommandGroupFactory.COMMAND_GROUP
import com.ing.zkflow.zinc.poet.generate.types.CommandGroupFactory.getCommandFieldName
import com.ing.zkflow.zinc.poet.generate.types.CommandGroupFactory.getCommandTypeName
import com.ing.zkflow.zinc.poet.generate.types.CommandGroupFactory.getSignerListModule
import com.ing.zkflow.zinc.poet.generate.types.LedgerTransactionFactory.Companion.LEDGER_TRANSACTION
import com.ing.zkflow.zinc.poet.generate.types.StandardTypes.Companion.nonceDigest
import com.ing.zkflow.zinc.poet.generate.types.StandardTypes.Companion.privacySalt
import com.ing.zkflow.zinc.poet.generate.types.StandardTypes.Companion.secureHash
import com.ing.zkflow.zinc.poet.generate.types.StandardTypes.Companion.stateRef
import com.ing.zkflow.zinc.poet.generate.types.StandardTypes.Companion.timeWindow

@Suppress("TooManyFunctions")
class Witness(
    zkTransaction: ZKTransactionMetadataCommandData,
    private val inputs: Map<BflModule, Int>,
    private val outputs: Map<BflModule, Int>,
    private val references: Map<BflModule, Int>,
    internal val notaryModule: BflModule,
    internal val signerModule: BflModule,
) : BflModule {
    internal val serializedOutputGroup = SerializedStateGroup(OUTPUTS, "OutputGroup", outputs)
    internal val serializedInputUtxos = SerializedStateGroup(SERIALIZED_INPUT_UTXOS, "InputUtxos", inputs)
    internal val serializedReferenceUtxos =
        SerializedStateGroup(SERIALIZED_REFERENCE_UTXOS, "ReferenceUtxos", references)
    private val transactionMetadata = zkTransaction.transactionMetadata

    private val dependencies =
        listOf(stateRef, secureHash, privacySalt, timeWindow, notaryModule, signerModule, nonceDigest)

    private fun arrayOfSerializedData(capacity: Int, module: BflModule): ZincArray {
        val paddingBits = module.bitSize.bitsToByteBoundary() - module.bitSize
        return if (paddingBits > 0) {
            arrayOfSerializedData(capacity, "${module.getLengthConstant()} + $paddingBits as u24")
        } else {
            arrayOfSerializedData(capacity, module.getLengthConstant())
        }
    }

    private fun arrayOfSerializedData(capacity: Int, type: BflType) =
        arrayOfSerializedData(capacity, "${type.bitSize.bitsToByteBoundary()} as u24")

    private fun arrayOfSerializedData(capacity: Int, stateSize: String) = zincArray {
        size = capacity.toString()
        elementType = zincArray {
            size = "$CORDA_MAGIC_BITS_SIZE_CONSTANT + $stateSize"
            elementType = ZincPrimitive.Bool
        }
    }

    private fun arrayOfNonceDigests(capacity: Int) = zincArray {
        size = "$capacity"
        elementType = nonceDigest.toZincId()
    }

    @Suppress("LongMethod")
    override fun generateZincFile(codeGenerationOptions: CodeGenerationOptions): ZincFile = ZincFile.zincFile {
        mod { module = CONSTS }
        newLine()
        listOf(serializedOutputGroup, serializedInputUtxos, serializedReferenceUtxos).forEach {
            mod { module = it.getModuleName() }
            use { path = "${it.getModuleName()}::${it.id}" }
            newLine()
        }
        dependencies.forEach { dependency ->
            mod { module = dependency.getModuleName() }
            use { path = "${dependency.getModuleName()}::${dependency.id}" }
            use { path = "${dependency.getModuleName()}::${dependency.getLengthConstant()}" }
            use { path = "${dependency.getModuleName()}::${dependency.getSerializedTypeDef().getName()}" }
            newLine()
        }
        (
            listOf(LEDGER_TRANSACTION, COMMAND_GROUP) + transactionMetadata.commands.flatMap {
                listOf(
                    getCommandTypeName(it),
                    getSignerListModule(it, signerModule).id,
                )
            }.distinct()
            ).forEach {
            mod { module = it.camelToSnakeCase() }
            use { path = "${it.camelToSnakeCase()}::$it" }
            newLine()
        }
        struct {
            name = Witness::class.java.simpleName
            field { name = INPUTS; type = arrayOfSerializedData(transactionMetadata.numberOfInputs, stateRef) }
            field { name = OUTPUTS; type = serializedOutputGroup.toZincId() }
            field { name = REFERENCES; type = arrayOfSerializedData(transactionMetadata.numberOfReferences, stateRef) }
            field { name = COMMANDS; type = arrayOfSerializedData(transactionMetadata.commands.size, BflPrimitive.U32) }
            field { name = ATTACHMENTS; type = arrayOfSerializedData(transactionMetadata.attachmentCount, secureHash) }
            field { name = NOTARY; type = arrayOfSerializedData(1, notaryModule) }
            if (transactionMetadata.hasTimeWindow) {
                field { name = TIME_WINDOW; type = arrayOfSerializedData(1, timeWindow) }
            }
            field { name = PARAMETERS; type = arrayOfSerializedData(1, secureHash) }
            field { name = SIGNERS; type = arrayOfSerializedData(transactionMetadata.numberOfSigners, signerModule) }
            field { name = PRIVACY_SALT; type = privacySalt.getSerializedTypeDef() }
            field { name = INPUT_NONCES; type = arrayOfNonceDigests(transactionMetadata.numberOfInputs) }
            field { name = REFERENCE_NONCES; type = arrayOfNonceDigests(transactionMetadata.numberOfReferences) }
            field { name = SERIALIZED_INPUT_UTXOS; type = serializedInputUtxos.toZincId() }
            field { name = SERIALIZED_REFERENCE_UTXOS; type = serializedReferenceUtxos.toZincId() }
        }
        newLine()
        impl {
            name = Witness::class.java.simpleName
            addFunctions(generateMethods(codeGenerationOptions))
            function {
                val fieldConstructors = transactionMetadata.commands.joinToString("\n") {
                    """
                        let ${getCommandFieldName(it)}: ${getCommandTypeName(it)} = {
                            let mut ${getCommandFieldName(it)}_array: [${signerModule.id}; ${it.numberOfSigners}] = [${signerModule.defaultExpr()}; ${it.numberOfSigners}];
                            for i in (0 as u8)..${it.numberOfSigners} {
                                ${getCommandFieldName(it)}_array[i] = signers[i + j];
                            }                        
                            ${getCommandTypeName(it)}::new(${getSignerListModule(it, signerModule).id}::list_of(${getCommandFieldName(it)}_array))
                        };
                        j += ${it.numberOfSigners};
                    """.trimIndent()
                }
                val fieldAssignments = transactionMetadata.commands.joinToString("\n") {
                    "${getCommandFieldName(it)}: ${getCommandFieldName(it)},"
                }
                name = "${COMMAND_GROUP}_from_$SIGNERS"
                parameter {
                    name = "signers"
                    type = zincArray {
                        elementType = signerModule.toZincId()
                        size = "${transactionMetadata.numberOfSigners}"
                    }
                }
                returnType = id(COMMAND_GROUP)
                body = """
                    let mut j: u8 = 0;
                    ${fieldConstructors.indent(20.spaces)}
                    
                    $COMMAND_GROUP {
                        ${fieldAssignments.indent(24.spaces)}
                    }
                """.trimIndent()
            }
            method {
                name = "deserialize"
                returnType = ZincType.id(LEDGER_TRANSACTION)
                body = """
                    let $SIGNERS = self.deserialize_$SIGNERS();
                    $LEDGER_TRANSACTION {
                        $INPUTS: self.deserialize_$INPUTS(),
                        $OUTPUTS: self.$OUTPUTS.deserialize(),
                        $REFERENCES: self.deserialize_$REFERENCES(),
                        $COMMANDS: ${COMMAND_GROUP}_from_$SIGNERS($SIGNERS),
                        $ATTACHMENTS: self.deserialize_$ATTACHMENTS(),
                        $NOTARY: self.deserialize_$NOTARY()[0],
                        ${if (transactionMetadata.hasTimeWindow) "$TIME_WINDOW: self.deserialize_$TIME_WINDOW()," else "// $TIME_WINDOW not present"}
                        $PARAMETERS: self.deserialize_$PARAMETERS()[0],
                        $SIGNERS: $SIGNERS,
                        ${PRIVACY_SALT}_field: ${
                generateDeserializeExpression(
                    codeGenerationOptions,
                    PRIVACY_SALT,
                    privacySalt,
                    "self.$PRIVACY_SALT",
                    "0 as u24"
                )
                },
                        $INPUT_NONCES: self.$INPUT_NONCES,
                        $REFERENCE_NONCES: self.$REFERENCE_NONCES,
                        $SERIALIZED_INPUT_UTXOS: self.$SERIALIZED_INPUT_UTXOS.deserialize(),
                        $SERIALIZED_REFERENCE_UTXOS: self.$SERIALIZED_REFERENCE_UTXOS.deserialize(),
                    }
                """.trimIndent()
            }
        }
    }

    fun getWitnessConfigurations(): List<WitnessGroupOptions> {
        return listOfNotNull(
            WitnessGroupOptions.cordaWrapped(INPUTS, stateRef),
            // outputs
            WitnessGroupOptions.cordaWrapped(REFERENCES, stateRef),
            WitnessGroupOptions.cordaWrapped(COMMANDS, BflPrimitive.U32),
            WitnessGroupOptions.cordaWrapped(ATTACHMENTS, secureHash),
            WitnessGroupOptions.cordaWrapped(NOTARY, notaryModule),
            if (transactionMetadata.hasTimeWindow) WitnessGroupOptions.cordaWrapped(TIME_WINDOW, timeWindow) else null,
            WitnessGroupOptions.cordaWrapped(PARAMETERS, secureHash),
            WitnessGroupOptions.cordaWrapped(SIGNERS, signerModule),
            WitnessGroupOptions(PRIVACY_SALT, privacySalt),
            WitnessGroupOptions(INPUT_NONCES, nonceDigest),
            WitnessGroupOptions(REFERENCE_NONCES, nonceDigest),
            // serialized_input_utxos
            // serialized_reference_utxos
        ) + outputs.keys.map {
            WitnessGroupOptions.cordaWrapped("${OUTPUTS}_${it.id.camelToSnakeCase()}", it)
        } + inputs.keys.map {
            WitnessGroupOptions.cordaWrapped("${SERIALIZED_INPUT_UTXOS}_${it.id.camelToSnakeCase()}", it)
        } + references.keys.map {
            WitnessGroupOptions.cordaWrapped("${SERIALIZED_REFERENCE_UTXOS}_${it.id.camelToSnakeCase()}", it)
        }
    }

    override fun generateMethods(codeGenerationOptions: CodeGenerationOptions): List<ZincFunction> {
        return listOfNotNull(
            Triple(INPUTS, stateRef, transactionMetadata.numberOfInputs),
            Triple(REFERENCES, stateRef, transactionMetadata.numberOfReferences),
            Triple(COMMANDS, BflPrimitive.U32, transactionMetadata.commands.size),
            Triple(ATTACHMENTS, secureHash, transactionMetadata.attachmentCount),
            Triple(NOTARY, notaryModule, 1),
            if (transactionMetadata.hasTimeWindow) Triple(TIME_WINDOW, timeWindow, 1) else null,
            Triple(PARAMETERS, secureHash, 1),
            Triple(SIGNERS, signerModule, transactionMetadata.numberOfSigners),
        ).map {
            val deserializeExpression = generateDeserializeExpression(
                codeGenerationOptions,
                it.first,
                it.second,
                "self.${it.first}[i]",
                CORDA_MAGIC_BITS_SIZE_CONSTANT
            )
            zincMethod {
                name = "deserialize_${it.first}"
                returnType = zincArray {
                    elementType = it.second.toZincId()
                    size = "${it.third}"
                }
                body = """
                    let mut ${it.first}_array: [${it.second.id}; ${it.third}] = [${it.second.defaultExpr()}; ${it.third}];
                    for i in 0..${it.third} {
                        ${it.first}_array[i] = ${deserializeExpression.indent(24.spaces)};
                    }
                    ${it.first}_array
                """.trimIndent()
            }
        }
    }

    private fun generateDeserializeExpression(
        codeGenerationOptions: CodeGenerationOptions,
        groupName: String,
        bflType: BflType,
        witnessVariable: String,
        offset: String
    ): String {
        val witnessGroup: WitnessGroupOptions = codeGenerationOptions.witnessGroupOptions.single { witnessGroup ->
            witnessGroup.deserializeMethodName.endsWith(groupName)
        }
        return bflType.deserializeExpr(witnessGroup, offset, groupName, witnessVariable)
    }

    override val id: String = Witness::class.java.simpleName

    override val bitSize: Int
        get() = TODO("Not yet implemented")

    override fun typeName(): String = id

    override fun deserializeExpr(
        witnessGroupOptions: WitnessGroupOptions,
        offset: String,
        variablePrefix: String,
        witnessVariable: String
    ): String {
        throw UnsupportedOperationException()
    }

    override fun defaultExpr(): String {
        throw UnsupportedOperationException()
    }

    override fun equalsExpr(self: String, other: String): String {
        throw UnsupportedOperationException()
    }

    override fun accept(visitor: TypeVisitor) {
        listOf(serializedOutputGroup, serializedInputUtxos, serializedReferenceUtxos).forEach { type ->
            visitor.visitType(type)
        }
        dependencies.forEach { dependency ->
            visitor.visitType(dependency)
        }
    }

    companion object {
        internal const val OUTPUTS = "outputs"
        internal const val INPUTS = "inputs"
        internal const val REFERENCES = "references"
        internal const val COMMANDS = "commands"
        internal const val ATTACHMENTS = "attachments"
        internal const val NOTARY = "notary"
        internal const val TIME_WINDOW = "time_window"
        internal const val PARAMETERS = "parameters"
        internal const val SIGNERS = "signers"
        internal const val PRIVACY_SALT = "privacy_salt"
        internal const val INPUT_NONCES = "input_nonces"
        internal const val REFERENCE_NONCES = "reference_nonces"
        internal const val SERIALIZED_INPUT_UTXOS = "serialized_input_utxos"
        internal const val SERIALIZED_REFERENCE_UTXOS = "serialized_reference_utxos"
    }
}
