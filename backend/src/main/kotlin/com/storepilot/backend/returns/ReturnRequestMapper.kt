package com.storepilot.backend.returns

fun ReturnRequest.toResponse(): ReturnRequestResponse =
    ReturnRequestResponse(
        id = requireNotNull(id),
        orderId = requireNotNull(order.id),
        orderNumber = order.orderNumber,
        storeId = requireNotNull(order.store.id),
        storeName = order.store.name,
        paymentMethod = order.paymentMethod.wireValue,
        reasonCategory = reasonCategory.wireValue,
        reasonNote = reasonNote,
        status = status.wireValue,
        sellerDecisionNote = sellerDecisionNote,
        refundReference = refundReference,
        settlementReconciliationNote = settlementReconciliationNote,
        createdAt = requireNotNull(createdAt),
        decidedAt = decidedAt,
        refundedAt = refundedAt,
    )
