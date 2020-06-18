package com.ing.zknotary.common.transactions

import com.ing.zknotary.common.serializer.SerializationFactoryService
import com.ing.zknotary.common.states.ZKStateRef
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.lazyMapped
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ComponentGroup
import net.corda.core.utilities.OpaqueBytes

class ZKPartialMerkleTree(
    vtx: ZKVerifierTransaction
) : AbstractZKMerkleTree(buildComponentGroups(vtx), vtx.componentGroupLeafDigestService, vtx.nodeDigestService) {
    override val groupHashes: List<SecureHash> by lazy {
        val componentGroupHashes = mutableListOf<SecureHash>()
        // Even if empty and not used, we should at least send oneHashes for each known
        // or received but unknown (thus, bigger than known ordinal) component groups.
        for (i in 0..componentGroups.map { it.groupIndex }.max()!!) {
            val root = groupsMerkleRoots[i] ?: vtx.groupHashes[i]
            componentGroupHashes.add(root)
        }
        componentGroupHashes
    }


    override val componentNonces: Map<Int, List<SecureHash>> = vtx.componentNonces

    companion object {
        fun buildComponentGroups(vtx: ZKVerifierTransaction): List<ComponentGroup> {
            return buildComponentGroups(
                vtx.inputs,
                vtx.outputs,
                vtx.references,
                vtx.notary,
                vtx.timeWindow,
                vtx.networkParametersHash,
                vtx.serializationFactoryService
            )
        }

        private fun buildComponentGroups(
            inputs: List<ZKStateRef>,
            outputs: List<ZKStateRef>,
            references: List<ZKStateRef>,
            notary: Party?,
            timeWindow: TimeWindow?,
            networkParametersHash: SecureHash?,
            serializationFactoryService: SerializationFactoryService
        ): List<ComponentGroup> {
            val serialize = { value: Any, _: Int -> value.serialize(serializationFactoryService.factory) }

            val componentGroupMap: MutableList<ComponentGroup> = mutableListOf()
            if (inputs.isNotEmpty()) componentGroupMap.add(
                ComponentGroup(
                    ComponentGroupEnum.INPUTS_GROUP.ordinal,
                    inputs.lazyMapped(serialize)
                )
            )
            if (references.isNotEmpty()) componentGroupMap.add(
                ComponentGroup(
                    ComponentGroupEnum.REFERENCES_GROUP.ordinal,
                    references.lazyMapped(serialize)
                )
            )
            if (outputs.isNotEmpty()) componentGroupMap.add(
                ComponentGroup(
                    ComponentGroupEnum.OUTPUTS_GROUP.ordinal,
                    outputs.lazyMapped(serialize)
                )
            )
            if (notary != null) componentGroupMap.add(
                ComponentGroup(
                    ComponentGroupEnum.NOTARY_GROUP.ordinal,
                    listOf(notary).lazyMapped(serialize)
                )
            )
            if (timeWindow != null) componentGroupMap.add(
                ComponentGroup(
                    ComponentGroupEnum.TIMEWINDOW_GROUP.ordinal,
                    listOf(timeWindow).lazyMapped(serialize)
                )
            )
            if (networkParametersHash != null) componentGroupMap.add(
                ComponentGroup(
                    ComponentGroupEnum.PARAMETERS_GROUP.ordinal,
                    listOf(networkParametersHash).lazyMapped(serialize)
                )
            )
            return componentGroupMap
        }
    }
}
