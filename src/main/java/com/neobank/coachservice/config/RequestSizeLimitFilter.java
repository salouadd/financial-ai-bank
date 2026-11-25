package com.neobank.coachservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final int maxRequestBytes;

    public RequestSizeLimitFilter(@Value("${server.max-http-request-size:65536}") int maxRequestBytes) {
        if (maxRequestBytes < 1) {
            throw new IllegalArgumentException("The maximum HTTP request size must be positive.");
        }
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxRequestBytes) {
            writePayloadTooLarge(response);
            return;
        }
        if (request.getContentLengthLong() == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxRequestBytes + 1);
        if (body.length > maxRequestBytes) {
            writePayloadTooLarge(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"status\":413,\"title\":\"Payload Too Large\",\"detail\":\"La taille maximale de la requête est dépassée.\"}"
        );
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    try {
                        if (input.available() > 0) {
                            readListener.onDataAvailable();
                        }
                        if (input.available() == 0) {
                            readListener.onAllDataRead();
                        }
                    } catch (IOException exception) {
                        readListener.onError(exception);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : java.nio.charset.Charset.forName(getCharacterEncoding())));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
