package com.ing.zknotary.common.contracts

import com.ing.zknotary.common.dactyloscopy.Fingerprintable
import com.ing.zknotary.common.util.ComponentPaddingConfiguration
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import java.nio.ByteBuffer

interface ZKContractState : Fingerprintable, ContractState

interface ZKCommandData : Fingerprintable, CommandData {
    val id: Int

    /**
     * Command data must contain padding configuration to produce a witness of the appropriate structure.
     * This witness is a serialization of the padded version of the Corda's component groups.
     * See consts.zn in folders corresponding to the respective commands.
     *
     * This public property will have NO influence on the fingerprint of the implementor
     * as ComponentPaddingConfiguration implements Fingerprintable itself with fingerprint
     * being an empty bytearray.
     */
    val paddingConfiguration: ComponentPaddingConfiguration

    override fun fingerprint(): ByteArray =
        ByteBuffer.allocate(4).putInt(id).array()
}
