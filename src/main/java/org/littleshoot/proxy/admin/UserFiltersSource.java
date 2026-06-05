package org.littleshoot.proxy.admin;

import com.google.common.io.BaseEncoding;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HttpFiltersSource that enforces per-user bandwidth limits and site blocking.
 */
public class UserFiltersSource extends HttpFiltersSourceAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(UserFiltersSource.class);
    
    private final CSVUserStore userStore;
    private final Map<String, UserData> authenticatedUsers = new ConcurrentHashMap<>();

    public UserFiltersSource(CSVUserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        // Extract username from Proxy-Authorization header
        String username = extractUsername(originalRequest);
        UserData user = null;
        String channelId = ctx != null ? ctx.channel().id().asLongText() : null;
        
        if (username != null) {
            user = userStore.getUser(username);
            if (user != null && user.isEnabled()) {
                if (channelId != null) {
                    authenticatedUsers.put(channelId, user);
                }
            } else if (channelId != null) {
                authenticatedUsers.remove(channelId);
            }
        } else if (channelId != null) {
            user = authenticatedUsers.get(channelId);
        }
        
        final UserData finalUser = user;
        
        return new HttpFiltersAdapter(originalRequest) {
            @Override
            public HttpResponse clientToProxyRequest(HttpObject httpObject) {
                if (httpObject instanceof HttpRequest) {
                    HttpRequest request = (HttpRequest) httpObject;
                    
                    // Check if site is blacklisted for this user
                    if (finalUser != null) {
                        String host = extractHost(request);
                        if (host != null && finalUser.isDomainBlacklisted(host)) {
                            LOG.warn("Blocking access to {} for user {}", host, finalUser.getUsername());
                            return createBlockedResponse(host);
                        }
                    }
                }
                
                return null; // Continue processing
            }
            
            @Override
            public HttpObject serverToProxyResponse(HttpObject httpObject) {
                // Apply bandwidth throttling if needed (handled at connection level)
                return httpObject;
            }
        };
    }

    private String extractUsername(HttpRequest request) {
        String authHeader = request.headers().get("Proxy-Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring("Basic ".length());
                String credentials = new String(BaseEncoding.base64().decode(base64Credentials), StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2) {
                    return parts[0];
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse Proxy-Authorization header", e);
            }
        }
        return null;
    }

    private String extractHost(HttpRequest request) {
        String hostAndPort = ProxyUtils.parseHostAndPort(request);
        if (hostAndPort == null || hostAndPort.trim().isEmpty()) {
            hostAndPort = request.headers().get(HttpHeaders.Names.HOST);
        }
        if (hostAndPort == null || hostAndPort.trim().isEmpty()) {
            return null;
        }

        return normalizeHost(hostAndPort);
    }

    private String normalizeHost(String hostAndPort) {
        String value = hostAndPort.trim();
        int slashIndex = value.indexOf('/');
        if (slashIndex >= 0) {
            value = value.substring(0, slashIndex);
        }

        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket > 0) {
                value = value.substring(1, closingBracket);
            } else {
                value = value.substring(1);
            }
        } else {
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon > -1 && firstColon == lastColon) {
                value = value.substring(0, firstColon);
            }
        }

        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }

        return value.trim().toLowerCase();
    }

    private HttpResponse createBlockedResponse(String host) {
        String body = "<html><head><title>Access Blocked</title></head>"
                + "<body><h1>Access Blocked</h1>"
                + "<p>Access to <b>" + host + "</b> has been blocked by your administrator.</p>"
                + "</body></html>";
        
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.FORBIDDEN,
                Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaders.Names.CONNECTION, "close");
        
        return response;
    }

    /**
     * Get user data for a specific channel (used for bandwidth throttling).
     */
    public UserData getUserForChannel(String channelId) {
        return authenticatedUsers.get(channelId);
    }
}
