package com.islandcart.backend.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * Keeps Spring Security's own 401/403 responses in the same ApiError shape
 * GlobalExceptionHandler already uses for every other error (fixed set of
 * codes/messages here, so no need to drag in an ObjectMapper just to
 * serialize two short, non-user-controlled strings).
 */
private fun writeApiError(response: HttpServletResponse, status: Int, code: String, message: String) {
    response.status = status
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.writer.write("""{"error":{"code":"$code","message":"$message"}}""")
}

class JsonAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        writeApiError(response, 401, "UNAUTHENTICATED", "Authentication required")
    }
}

class JsonAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        writeApiError(response, 403, "FORBIDDEN_ROLE", "You don't have permission to perform this action")
    }
}
