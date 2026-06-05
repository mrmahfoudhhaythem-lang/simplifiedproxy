package org.littleshoot.proxy.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user data storage in CSV format.
 * CSV Format: username,password,maxBandwidthBytesPerSecond,blacklistedDomains,enabled
 * Blacklisted domains are separated by semicolons.
 */
public class CSVUserStore {
    private static final Logger LOG = LoggerFactory.getLogger(CSVUserStore.class);
    private static final String DELIMITER = ",";
    private static final String DOMAIN_SEPARATOR = ";";
    
    private final File csvFile;
    private final Map<String, UserData> users;
    private long lastModified;

    public CSVUserStore(String filePath) {
        this.csvFile = new File(filePath);
        this.users = new ConcurrentHashMap<>();
        this.lastModified = 0;
        
        // Create file with header if it doesn't exist
        if (!csvFile.exists()) {
            createDefaultFile();
        }
        
        loadUsers();
    }

    private void createDefaultFile() {
        try {
            File parent = csvFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {
                writer.println("# LittleProxy User Configuration");
                writer.println("# Format: username,password,maxBandwidthBytesPerSecond,blacklistedDomains,enabled");
                writer.println("# Blacklisted domains are separated by semicolons (e.g., facebook.com;*.youtube.com)");
                writer.println("# Bandwidth: 0 = unlimited, otherwise bytes per second (e.g., 1048576 = 1MB/s)");
                writer.println("username,password,maxBandwidthBytesPerSecond,blacklistedDomains,enabled");
                writer.println("admin,admin123,0,,true");
                writer.println("user1,pass123,1048576,facebook.com;*.youtube.com,true");
            }
            LOG.info("Created default user file: {}", csvFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to create default user file", e);
        }
    }

    /**
     * Load users from CSV file. Automatically reloads if file has been modified.
     */
    public synchronized void loadUsers() {
        if (!csvFile.exists()) {
            LOG.warn("User file does not exist: {}", csvFile.getAbsolutePath());
            return;
        }

        long currentModified = csvFile.lastModified();
        if (currentModified == lastModified && !users.isEmpty()) {
            return; // No changes
        }

        Map<String, UserData> newUsers = new ConcurrentHashMap<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Skip header
                if (line.startsWith("username,")) {
                    continue;
                }
                
                try {
                    UserData user = parseUserLine(line);
                    if (user != null) {
                        newUsers.put(user.getUsername(), user);
                    }
                } catch (Exception e) {
                    LOG.error("Error parsing line {}: {}", lineNumber, line, e);
                }
            }
            
            users.clear();
            users.putAll(newUsers);
            lastModified = currentModified;
            
            LOG.info("Loaded {} users from {}", users.size(), csvFile.getAbsolutePath());
            
        } catch (IOException e) {
            LOG.error("Failed to load users from file", e);
        }
    }

    private UserData parseUserLine(String line) {
        String[] parts = line.split(DELIMITER, -1);
        if (parts.length < 5) {
            LOG.warn("Invalid user line (expected 5 fields): {}", line);
            return null;
        }

        String username = parts[0].trim();
        String password = parts[1].trim();
        long bandwidth = 0;
        
        try {
            bandwidth = Long.parseLong(parts[2].trim());
        } catch (NumberFormatException e) {
            LOG.warn("Invalid bandwidth for user {}: {}", username, parts[2]);
        }

        List<String> blacklistedDomains = new ArrayList<>();
        if (!parts[3].trim().isEmpty()) {
            String[] domains = parts[3].split(DOMAIN_SEPARATOR);
            for (String domain : domains) {
                String trimmed = domain.trim();
                if (!trimmed.isEmpty()) {
                    blacklistedDomains.add(trimmed);
                }
            }
        }

        boolean enabled = Boolean.parseBoolean(parts[4].trim());

        return new UserData(username, password, bandwidth, blacklistedDomains, enabled);
    }

    /**
     * Save all users to CSV file.
     */
    public synchronized void saveUsers() {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {
            
            writer.println("# LittleProxy User Configuration");
            writer.println("# Format: username,password,maxBandwidthBytesPerSecond,blacklistedDomains,enabled");
            writer.println("# Blacklisted domains are separated by semicolons");
            writer.println("username,password,maxBandwidthBytesPerSecond,blacklistedDomains,enabled");
            
            for (UserData user : users.values()) {
                writer.println(formatUserLine(user));
            }
            
            lastModified = csvFile.lastModified();
            LOG.info("Saved {} users to {}", users.size(), csvFile.getAbsolutePath());
            
        } catch (IOException e) {
            LOG.error("Failed to save users to file", e);
        }
    }

    private String formatUserLine(UserData user) {
        StringBuilder sb = new StringBuilder();
        sb.append(user.getUsername()).append(DELIMITER);
        sb.append(user.getPassword()).append(DELIMITER);
        sb.append(user.getMaxBandwidthBytesPerSecond()).append(DELIMITER);
        
        // Join blacklisted domains
        List<String> domains = user.getBlacklistedDomains();
        if (domains != null && !domains.isEmpty()) {
            for (int i = 0; i < domains.size(); i++) {
                if (i > 0) sb.append(DOMAIN_SEPARATOR);
                sb.append(domains.get(i));
            }
        }
        
        sb.append(DELIMITER);
        sb.append(user.isEnabled());
        
        return sb.toString();
    }

    public UserData getUser(String username) {
        loadUsers(); // Auto-reload if file changed
        return users.get(username);
    }

    public Collection<UserData> getAllUsers() {
        loadUsers(); // Auto-reload if file changed
        return new ArrayList<>(users.values());
    }

    public void addUser(UserData user) {
        users.put(user.getUsername(), user);
        saveUsers();
    }

    public void updateUser(UserData user) {
        users.put(user.getUsername(), user);
        saveUsers();
    }

    public void deleteUser(String username) {
        users.remove(username);
        saveUsers();
    }

    public boolean authenticate(String username, String password) {
        UserData user = getUser(username);
        return user != null && user.isEnabled() && user.getPassword().equals(password);
    }
}
