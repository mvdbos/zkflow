package com.ing.zkflow.common.versioning

/**
 * A base interface that marks classes as versioned.
 * This interface should be extended by a marker interface to group states or commands of different versions.
 * A marker interface is an empty interface that extends this interface.
 *
 * ```kotlin
 * interface Marker : Versioned
 * data class Marked(...): Marker { ... }
 * ```
 */
interface Versioned
