package com.ing.zkflow.ksp.implementations

import kotlin.reflect.KClass

/**
 * A processor that processes implementations of [interfaceClass].
 */
interface ImplementationsProcessor<T : Any> {
    val interfaceClass: KClass<T>
    fun process(implementations: List<ScopedDeclaration>): ServiceLoaderRegistration
}

data class ServiceLoaderRegistration(val providerClass: KClass<*>, val implementations: List<String>)
