package org.littleshoot.proxy.admin;

import org.littleshoot.proxy.ProxyAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProxyAuthenticator that validates users against the CSV user store.
 */
public class UserStoreProxyAuthenticator implements ProxyAuthenticator {
    private static final Logger LOG = LoggerFactory.getLogger(UserStoreProxyAuthenticator.class);
    
    private final CSVUserStore userStore;

    public UserStoreProxyAuthenticator(CSVUserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public boolean authenticate(String username, String password) {
        boolean authenticated = userStore.authenticate(username, password);
        
        if (authenticated) {
            LOG.info("User authenticated: {}", username);
        } else {
            LOG.warn("Authentication failed for user: {}", username);
        }
        
        return authenticated;
    }

    @Override
    public String getRealm() {
        return "LittleProxy";
    }
}
