package org.littleshoot.proxy.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a proxy user with authentication credentials, bandwidth limits, and site restrictions.
 */
public class UserData {
    private String username;
    private String password;
    private long maxBandwidthBytesPerSecond; // 0 means unlimited
    private List<String> blacklistedDomains;
    private boolean enabled;

    public UserData() {
        this.blacklistedDomains = new ArrayList<>();
        this.enabled = true;
    }

    public UserData(String username, String password, long maxBandwidthBytesPerSecond, 
                    List<String> blacklistedDomains, boolean enabled) {
        this.username = username;
        this.password = password;
        this.maxBandwidthBytesPerSecond = maxBandwidthBytesPerSecond;
        this.blacklistedDomains = blacklistedDomains != null ? blacklistedDomains : new ArrayList<String>();
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getMaxBandwidthBytesPerSecond() {
        return maxBandwidthBytesPerSecond;
    }

    public void setMaxBandwidthBytesPerSecond(long maxBandwidthBytesPerSecond) {
        this.maxBandwidthBytesPerSecond = maxBandwidthBytesPerSecond;
    }

    public List<String> getBlacklistedDomains() {
        return blacklistedDomains;
    }

    public void setBlacklistedDomains(List<String> blacklistedDomains) {
        this.blacklistedDomains = blacklistedDomains;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Check if a domain is blacklisted for this user.
     * Supports wildcard matching (e.g., *.example.com).
     */
    public boolean isDomainBlacklisted(String domain) {
        if (domain == null || blacklistedDomains == null) {
            return false;
        }

        String lowerDomain = domain.toLowerCase();
        for (String blacklisted : blacklistedDomains) {
            String lowerBlacklisted = blacklisted.toLowerCase().trim();
            
            // Exact match
            if (lowerDomain.equals(lowerBlacklisted)) {
                return true;
            }
            
            // Wildcard match (*.example.com matches subdomain.example.com)
            if (lowerBlacklisted.startsWith("*.")) {
                String baseDomain = lowerBlacklisted.substring(2);
                if (lowerDomain.endsWith(baseDomain) || lowerDomain.equals(baseDomain)) {
                    return true;
                }
            }
            
            // Partial domain match (example.com matches www.example.com)
            if (lowerDomain.endsWith("." + lowerBlacklisted)) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public String toString() {
        return "UserData{" +
                "username='" + username + '\'' +
                ", maxBandwidth=" + maxBandwidthBytesPerSecond +
                ", blacklistedDomains=" + blacklistedDomains.size() +
                ", enabled=" + enabled +
                '}';
    }
}
