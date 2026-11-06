package org.example.synchro;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.example.synchro.resources.exception.ClientDisconnectExceptionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ClientDisconnectTests {
    private static final String ABORTED = "Uma conexão estabelecida foi anulada pelo software no computador host";
    private final ClientDisconnectExceptionResolver resolver = new ClientDisconnectExceptionResolver();

    @Test
    void wrappedDisconnectsAreResolvedWithoutWritingToTheResponse() {
        var response = mock(HttpServletResponse.class);
        for (Exception cause : new Exception[]{
                new AsyncRequestNotUsableException("ServletOutputStream failed to write", new IOException(ABORTED)),
                new ClientAbortException(new IOException(ABORTED))}) {
            var exception = new HttpMessageNotWritableException("Could not write JSON", cause);
            var handled = resolver.resolveException(new MockHttpServletRequest(), response, null, exception);
            assertNotNull(handled);
            assertTrue(handled.isEmpty());
        }
        verifyNoInteractions(response);
    }

    @Test
    void actualSerializationErrorsStillUseTheNormalServerErrorHandling() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var exception = new HttpMessageNotWritableException("No serializer found for property");
        assertNull(resolver.resolveException(request, response, null, exception));
        new DefaultHandlerExceptionResolver().resolveException(request, response, null, exception);
        assertEquals(500, response.getStatus());
        assertNull(resolver.resolveException(request, response, null, new IOException("Unexpected disk failure")));
    }

    @Test
    void providerAndDatabaseFailuresAreNotMistakenForBrowserDisconnects() {
        var response = new MockHttpServletResponse();
        var cause = new IOException("Broken pipe");
        var provider = new WebClientRequestException(cause, HttpMethod.GET, URI.create("https://provider.test"), new HttpHeaders());
        var database = new DataAccessResourceFailureException("Connection unavailable", cause);
        for (Exception exception : new Exception[]{provider, database}) {
            assertNull(resolver.resolveException(new MockHttpServletRequest(), response, null, exception));
        }
    }

    @RestController
    static class FixtureController {
        @GetMapping("/disconnect-test")
        public Map<String, String> data() { return Map.of("title", "Track"); }
    }

    @Test
    void aRealJsonWriteFailureOnACommittedResponseIsHandledBeforeTheDefaultResolver() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setHandlerExceptionResolvers(resolver, (request, response, handler, error) -> {
                    fail("Disconnected response reached the default error handling: " + error);
                    return null;
                })
                .addFilter((request, response, chain) -> {
                    response.flushBuffer();
                    chain.doFilter(request, new HttpServletResponseWrapper((HttpServletResponse) response) {
                        @Override public ServletOutputStream getOutputStream() {
                            return new ServletOutputStream() {
                                @Override public void write(int value) throws IOException { throw new IOException(ABORTED); }
                                @Override public boolean isReady() { return true; }
                                @Override public void setWriteListener(WriteListener listener) {}
                            };
                        }
                    });
                }).build();
        var result = mvc.perform(get("/disconnect-test")).andReturn();
        assertTrue(result.getResponse().isCommitted());
        assertInstanceOf(HttpMessageNotWritableException.class, result.getResolvedException());
        assertEquals("", result.getResponse().getContentAsString());
    }
}
