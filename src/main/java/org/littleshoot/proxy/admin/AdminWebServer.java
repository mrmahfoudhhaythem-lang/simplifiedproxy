package org.littleshoot.proxy.admin;

import com.google.common.io.BaseEncoding;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in web server for the admin interface.
 * Handles API requests and serves the HTML/JS admin panel.
 */
public class AdminWebServer {
    private static final Logger LOG = LoggerFactory.getLogger(AdminWebServer.class);
    private static final int MAX_API_REQUEST_SIZE_BYTES = 1024 * 1024;
    
    private final CSVUserStore userStore;
    private final int port;
    private final String adminPassword;
    private HttpProxyServer webServer;

    public AdminWebServer(CSVUserStore userStore, int port, String adminPassword) {
        this.userStore = userStore;
        this.port = port;
        this.adminPassword = adminPassword;
    }

    public void start() {
        LOG.info("Starting admin web server on port {}", port);
        
        webServer = DefaultHttpProxyServer.bootstrap()
                .withPort(port)
                .withAllowLocalOnly(false)
                .withAllowRequestToOriginServer(true)
                .withFiltersSource(new AdminFiltersSource())
                .start();
        
        LOG.info("Admin interface available at http://localhost:{}/", port);
    }

    public void stop() {
        if (webServer != null) {
            webServer.stop();
        }
    }

    private class AdminFiltersSource extends HttpFiltersSourceAdapter {
        @Override
        public int getMaximumRequestBufferSizeInBytes() {
            return MAX_API_REQUEST_SIZE_BYTES;
        }
        @Override
        public HttpFilters filterRequest(HttpRequest originalRequest, io.netty.channel.ChannelHandlerContext ctx) {
            return new HttpFiltersAdapter(originalRequest) {
                @Override
                public HttpResponse clientToProxyRequest(HttpObject httpObject) {
                    if (httpObject instanceof HttpRequest) {
                        HttpRequest request = (HttpRequest) httpObject;
                        String uri = request.getUri();
                        
                        // Check authentication for API endpoints
                        if (uri.startsWith("/api/")) {
                            if (!isAuthenticated(request)) {
                                return createUnauthorizedResponse();
                            }
                        }
                        
                        // Route to appropriate handler
                        if (uri.equals("/") || uri.equals("/index.html")) {
                            return serveStaticResource("/admin-ui.html");
                        } else if (uri.startsWith("/api/users")) {
                            return handleUsersApi(request);
                        } else if (uri.startsWith("/api/reload")) {
                            return handleReloadApi();
                        }
                    }
                    
                    return createNotFoundResponse();
                }
            };
        }
    }

    private boolean isAuthenticated(HttpRequest request) {
        String authHeader = request.headers().get("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring("Basic ".length());
                String credentials = new String(BaseEncoding.base64().decode(base64Credentials), StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2 && parts[0].equals("admin") && parts[1].equals(adminPassword)) {
                    return true;
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse Authorization header", e);
            }
        }
        return false;
    }

    private HttpResponse handleUsersApi(HttpRequest request) {
        String uri = request.getUri();
        HttpMethod method = request.getMethod();
        
        try {
            if (method == HttpMethod.GET && uri.equals("/api/users")) {
                // List all users
                return createJsonResponse(getUsersJson());
            } else if (method == HttpMethod.POST && uri.equals("/api/users")) {
                // Create new user
                String body = extractRequiredBody(request);
                UserData user = parseUserJson(body);
                validateUserForCreate(user);
                userStore.addUser(user);
                return createJsonResponse(createApiStatusJson(true, "User created"));
            } else if (method == HttpMethod.PUT && uri.startsWith("/api/users/")) {
                // Update user
                String username = extractUsername(uri);
                String body = extractRequiredBody(request);
                UserData user = parseUserJson(body);
                validateUserForUpdate(username, user);
                userStore.updateUser(user);
                return createJsonResponse(createApiStatusJson(true, "User updated"));
            } else if (method == HttpMethod.DELETE && uri.startsWith("/api/users/")) {
                // Delete user
                String username = extractUsername(uri);
                if (username == null || username.trim().isEmpty()) {
                    throw new IllegalArgumentException("Username is required");
                }
                userStore.deleteUser(username);
                return createJsonResponse(createApiStatusJson(true, "User deleted"));
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid API request for {} {}", method, uri, e);
            return createJsonResponse(HttpResponseStatus.BAD_REQUEST, createApiStatusJson(false, e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error handling API request", e);
            return createJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createApiStatusJson(false, "Internal server error"));
        }
        
        return createNotFoundResponse();
    }

    private HttpResponse handleReloadApi() {
        userStore.loadUsers();
        return createJsonResponse(createApiStatusJson(true, "Users reloaded"));
    }

    private String getUsersJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"users\":[");
        
        List<UserData> users = (List<UserData>) userStore.getAllUsers();
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) json.append(",");
            UserData user = users.get(i);
            json.append("{");
            json.append("\"username\":\"").append(escapeJson(user.getUsername())).append("\",");
            json.append("\"password\":\"").append(escapeJson(user.getPassword())).append("\",");
            json.append("\"maxBandwidth\":").append(user.getMaxBandwidthBytesPerSecond()).append(",");
            json.append("\"blacklistedDomains\":[");
            List<String> domains = user.getBlacklistedDomains();
            for (int j = 0; j < domains.size(); j++) {
                if (j > 0) json.append(",");
                json.append("\"").append(escapeJson(domains.get(j))).append("\"");
            }
            json.append("],");
            json.append("\"enabled\":").append(user.isEnabled());
            json.append("}");
        }
        
        json.append("]}");
        return json.toString();
    }

    private UserData parseUserJson(String json) {
        // Simple JSON parsing (in production, use a proper JSON library)
        UserData user = new UserData();
        
        user.setUsername(extractJsonString(json, "username"));
        user.setPassword(extractJsonString(json, "password"));
        user.setMaxBandwidthBytesPerSecond(extractJsonLong(json, "maxBandwidth"));
        user.setEnabled(extractJsonBoolean(json, "enabled"));
        
        String domainsStr = extractJsonString(json, "blacklistedDomains");
        if (domainsStr != null && !domainsStr.isEmpty()) {
            List<String> domains = new ArrayList<>();
            for (String domain : domainsStr.split(";")) {
                String trimmed = domain.trim();
                if (!trimmed.isEmpty()) {
                    domains.add(trimmed);
                }
            }
            user.setBlacklistedDomains(domains);
        }
        
        return user;
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private long extractJsonLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return 0;
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean extractJsonBoolean(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return true;
        start += search.length();
        return json.substring(start).startsWith("true");
    }

    private String extractBody(HttpRequest request) {
        if (request instanceof FullHttpRequest) {
            FullHttpRequest fullRequest = (FullHttpRequest) request;
            return fullRequest.content().toString(StandardCharsets.UTF_8);
        }
        return "";
    }

    private String extractRequiredBody(HttpRequest request) {
        String body = extractBody(request);
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Request body is required");
        }
        return body;
    }

    private void validateUserForCreate(UserData user) {
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        if (username.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (userStore.getUser(username) != null) {
            throw new IllegalArgumentException("User already exists");
        }
        user.setUsername(username);
    }

    private void validateUserForUpdate(String usernameFromPath, UserData user) {
        if (usernameFromPath == null || usernameFromPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        user.setUsername(usernameFromPath.trim());
    }

    private String extractUsername(String uri) {
        // Extract username from URI like /api/users/john
        String[] parts = uri.split("/");
        return parts[parts.length - 1];
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private HttpResponse serveStaticResource(String resourcePath) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is == null) {
                return createNotFoundResponse();
            }
            
            byte[] content = readAllBytes(is);
            String contentType = "text/html; charset=UTF-8";
            
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(content)
            );
            
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, content.length);
            
            return response;
        } catch (IOException e) {
            LOG.error("Failed to serve static resource: {}", resourcePath, e);
            return createNotFoundResponse();
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((bytesRead = is.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

    private String createApiStatusJson(boolean success, String message) {
        return "{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\"}";
    }

    private HttpResponse createJsonResponse(String json) {
        return createJsonResponse(HttpResponseStatus.OK, json);
    }

    private HttpResponse createJsonResponse(HttpResponseStatus status, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
        response.headers().set("Access-Control-Allow-Origin", "*");
        
        return response;
    }

    private HttpResponse createUnauthorizedResponse() {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.UNAUTHORIZED
        );
        
        response.headers().set("WWW-Authenticate", "Basic realm=\"LittleProxy Admin\"");
        
        return response;
    }

    private HttpResponse createNotFoundResponse() {
        String body = "<html><body><h1>404 Not Found</h1></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_FOUND,
                Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
        
        return response;
    }
}
