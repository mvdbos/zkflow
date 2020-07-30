package com.ing.zknotary.common.zkp

import com.ing.zknotary.common.serializer.SerializationFactoryService

open class ZKConfig(
    val zkService: ZKService,
    val serializationFactoryService: SerializationFactoryService
)
