package com.ing.zknotary.node.services

import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializeAsToken

/**
 * Returns a singleton of type [T].
 *
 * This function throws an exception if:
 * - the config key does not exist in the CorDapp
 * - the config value refers to a class that can't be found on the classpath. (Note that it should be a fully qualified name).
 * - the class is not a properly registered Corda service: It should be annotated with @CordaService
 */
fun <T : SerializeAsToken> ServiceHub.getCordaServiceFromConfig(configKey: String): T {
    val serviceClassName = this.getAppContext().config.getString(configKey)

    @Suppress("UNCHECKED_CAST")
    val clazz = Class.forName(serviceClassName) as Class<T>

    return this.cordaService(clazz)
}

object ServiceNames {
    const val ZK_PROVER_TX_STORAGE = "zkProverTxStorage"
}
