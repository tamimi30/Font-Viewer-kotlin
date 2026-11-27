package com.oneui.fontviewer.data.trash.model

class OperationResult(
    val succeeded: Int,
    val failed: Int,
    val operationType: OperationType,
    val wasCancelled: Boolean
)
