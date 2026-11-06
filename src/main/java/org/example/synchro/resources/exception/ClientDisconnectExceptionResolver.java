package org.example.synchro.resources.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.DisconnectedClientHelper;

/** Recognize disconnected clients even when a JSON converter wraps the I/O failure. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientDisconnectExceptionResolver implements HandlerExceptionResolver {
    private final DisconnectedClientHelper disconnectedClients =
            new DisconnectedClientHelper(ClientDisconnectExceptionResolver.class.getName());

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception exception) {
        if (!disconnectedClients.checkAndLogClientDisconnectedException(exception)) return null;
        // The client is gone. Do not try to send another JSON body or alter a committed response.
        return new ModelAndView();
    }
}
