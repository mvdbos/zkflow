package com.ing.zknotary.descriptors

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.ing.zknotary.descriptors.types.AnnotatedSizedClass
import com.ing.zknotary.descriptors.types.DefaultableClass
import com.ing.zknotary.descriptors.types.Int_
import com.ing.zknotary.descriptors.types.List_
import com.ing.zknotary.descriptors.types.Pair_
import com.ing.zknotary.descriptors.types.Triple_

/**
 * To describe the property structure in a way allowing
 * derivation of its fixed length version, it is required to know
 * - the name of the property,
 * - list of user classes, for which fixed length version will be generated.
 *
 * The latter is required because potentially the property type may include
 * such classes as internal components.
 */
fun KSPropertyDeclaration.describe(
    propertyName: String,
    sizedClasses: List<KSClassDeclaration>
): PropertyDescriptor {
    val name = simpleName.asString()
    val typeDef = type.resolve()

    val descriptor = typeDef.describe(Support.SizedClasses(sizedClasses))

    return PropertyDescriptor(
        name = name,
        type = descriptor.type,
        fromInstance = descriptor.toCodeBlock("$propertyName.$name"),
        default = descriptor.default
    )
}

/**
 * `support` adds context on how to treat classes which
 * are not supported by `TypeDescriptor` and adds a shortcut
 * for types for which user-defined empty constructor needs to be used.
 */
fun KSType.describe(support: Support): TypeDescriptor {
    support.requireFor(this)

    return when ("$declaration") {
        // Primitive types
        Int::class.simpleName -> Int_(0, declaration)

        //
        // Compound types
        Pair::class.simpleName -> Pair_(
            arguments.subList(0, 2).map {
                val innerType = it.type?.resolve()
                require(innerType != null) { "Pair must have type arguments" }
                innerType.describe(support)
            }
        )

        Triple::class.simpleName -> Triple_(
            arguments.subList(0, 3).map {
                val innerType = it.type?.resolve()
                require(innerType != null) { "Pair must have type arguments" }
                innerType.describe(support)
            }
        )

        //
        // Collections
        List::class.simpleName -> List_.fromKSP(this, support)

        // Unknown type allowing fixed length representation.
        else -> {
            // Say, such class is called `SiClass`,
            // it is either expected either
            // 1. to have a sized version called SiClassSized,
            //    meaning it is expected to have a sized version called
            //    `KSClassDeclaration<SiClass>.sizedName`, or
            // 2. to have a default constructor, in this case,
            //    flag `useDefault` will be set to true
            val clazz = declaration as KSClassDeclaration
            when (support) {
                is Support.Default -> DefaultableClass(clazz)
                is Support.SizedClasses -> AnnotatedSizedClass(clazz)
            }
        }
    }
}

val KSClassDeclaration.sizedName: String
    get() = "${simpleName.asString()}Sized"

sealed class Support {
    abstract fun requireFor(type: KSType)

    object Default : Support() {
        override fun requireFor(type: KSType) {}
    }

    data class SizedClasses(val classes: List<KSClassDeclaration>): Support() {
        override fun requireFor(type: KSType) {
            val typename = "${type.declaration}"
            val errors = mutableListOf("Type $typename is not supported\n")

            // Check type is one of those listed in TypeDecriptor
            if (TypeDescriptor.supports(typename)) {
                return
            }
            errors += "Supported types:\n${TypeDescriptor.supported.joinToString(separator = ",\n")}"

            // Check type will (or already) have a generated fixed length version.
            if (classes.any { it.simpleName.asString() == typename }) {
                return
            }
            errors += "Class $typename is not expected to have fixed length"

            error(errors.joinToString(separator = "\n"))
        }
    }
}