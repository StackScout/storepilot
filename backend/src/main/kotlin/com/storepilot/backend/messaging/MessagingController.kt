package com.storepilot.backend.messaging

import com.storepilot.backend.common.PageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class MessagingController(
    private val messagingService: MessagingService,
) {
    @PostMapping("/api/stores/{storeId}/conversations")
    fun getOrCreateConversation(@PathVariable storeId: UUID): ResponseEntity<ConversationResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(messagingService.getOrCreateConversation(storeId))

    @GetMapping("/api/me/conversations")
    fun listMyConversations(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ConversationResponse> = messagingService.listMyConversations(page, size)

    @GetMapping("/api/stores/{storeId}/conversations")
    fun listStoreConversations(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ConversationResponse> = messagingService.listStoreConversations(storeId, page, size)

    @GetMapping("/api/conversations/{id}")
    fun getById(@PathVariable id: UUID): ConversationResponse = messagingService.getById(id)

    @GetMapping("/api/conversations/{id}/messages")
    fun listMessages(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<MessageResponse> = messagingService.listMessages(id, page, size)

    @PostMapping("/api/conversations/{id}/messages")
    fun sendMessage(@PathVariable id: UUID, @Valid @RequestBody input: SendMessageInput): ResponseEntity<MessageResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(messagingService.sendMessage(id, input))
}
