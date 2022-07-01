package com.ing.zkflow.processors.serialization.hierarchy.types

import com.google.devtools.ksp.symbol.KSType
import com.ing.zkflow.annotations.ASCIIChar
import com.ing.zkflow.annotations.UnicodeChar
import com.ing.zkflow.ksp.getNonRepeatableAnnotationByType
import com.ing.zkflow.processors.serialization.hierarchy.SerializingHierarchy
import com.ing.zkflow.serialization.serializer.char.ASCIICharSerializer
import com.ing.zkflow.serialization.serializer.char.UnicodeCharSerializer
import com.ing.zkflow.tracking.Tracker

internal fun KSType.asChar(tracker: Tracker): SerializingHierarchy {
    // Require com.ing.zkflow.annotations.ASCIIChar/com.ing.zkflow.annotations.UnicodeChar annotation.
    val serializer = try {
        this.getNonRepeatableAnnotationByType(ASCIIChar::class)
        ASCIICharSerializer::class
    } catch (e: Exception) {
        this.getNonRepeatableAnnotationByType(UnicodeChar::class)
        UnicodeCharSerializer::class
    }

    return this.asBasic(tracker, serializer)
}
