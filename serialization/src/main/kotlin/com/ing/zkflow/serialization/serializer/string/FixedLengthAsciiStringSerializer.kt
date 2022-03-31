package com.ing.zkflow.serialization.serializer.string

import com.ing.zkflow.serialization.FixedLengthType

/**
 * Serializer for [String], using ASCII encoding. The ASCII encoded byte array is restrained by [maxBytes].
 *
 * ASCII is a fixed-length character encoding, where each character will always be encoded in 8 bits.
 *
 * Actually this implementation uses ISO-8859-1 or latin-1 encoding, which is sometimes referred to as extended ASCII.
 * Where the ASCII table only defines the first slots, i.e. 0-127, ISO-8859-1 defines the additional slots 128-255 as
 * well, so that many accented characters from latin languages are supported as well.
 *
 * Any characters not supported by this charset will be serialized as '?' (ascii code 63).
 */
open class FixedLengthAsciiStringSerializer(maxBytes: Int) :
    AbstractFixedLengthStringSerializer(maxBytes, FixedLengthType.ASCII_STRING, Charsets.ISO_8859_1)
