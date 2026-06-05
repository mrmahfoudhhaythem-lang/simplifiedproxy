# LittleProxy Admin Interface

This enhanced version of LittleProxy includes a web-based admin interface for managing users, bandwidth limits, and website restrictions.

## Features

✅ **User Authentication** - Proxy requires username/password authentication  
✅ **Web Admin Interface** - Modern, responsive web UI for user management  
✅ **Bandwidth Limiting** - Set per-user bandwidth limits  
✅ **Website Blocking** - Block specific domains per user (supports wildcards)  
✅ **CSV Storage** - User data stored in easy-to-edit CSV file  
✅ **Hot Reload** - Changes to CSV are automatically detected  

## Quick Start

### 1. Build the Project

```bash
mvn clean package -Dmaven.test.skip=true
```

### 2. Run the Managed Proxy

```bash
# Using Maven (development)
mvn exec:java -Dexec.mainClass="org.littleshoot.proxy.admin.ManagedProxyLauncher"

# Or using the compiled JAR
java -cp target/littleproxy-1.1.3-SNAPSHOT.jar org.littleshoot.proxy.admin.ManagedProxyLauncher
```

### 3. Access the Admin Panel

Open your web browser and navigate to:
```
http://localhost:9090/
```

**Default admin credentials:**
- Username: `admin`
- Password: `admin123`

### 4. Configure Your Browser

Set your browser to use the proxy:
- **Proxy Host:** `localhost`
- **Proxy Port:** `8080`
- **Authentication:** Required (use credentials from admin panel)

## Command Line Options

```bash
java -cp target/littleproxy.jar org.littleshoot.proxy.admin.ManagedProxyLauncher [options]

Options:
  --port <port>              Proxy port (default: 8080)
  --admin-port <port>        Admin interface port (default: 9090)
  --users-file <path>        Path to users CSV file (default: users.csv)
  --admin-password <pass>    Admin password (default: admin123)
  --help                     Display help
```

### Examples

```bash
# Start with custom ports
java -cp target/littleproxy.jar org.littleshoot.proxy.admin.ManagedProxyLauncher --port 8888 --admin-port 8889

# Start with custom users file
java -cp target/littleproxy.jar org.littleshoot.proxy.admin.ManagedProxyLauncher --users-file /path/to/users.csv

# Start with custom admin password
java -cp target/littleproxy.jar org.littleshoot.proxy.admin.ManagedProxyLauncher --admin-password mySecretPass
```

## Users CSV Format

The `users.csv` file stores all user data. It's automatically created on first run with sample users.

**Format:**
```csv
username,password,maxBandwidthBytesPerSecond,blacklistedDomains,enabled
admin,admin123,0,,true
john,pass123,1048576,facebook.com;*.youtube.com,true
jane,secure456,2097152,twitter.com;reddit.com,true
```

**Fields:**
- `username` - Unique username for proxy authentication
- `password` - Plain text password (consider using secure storage in production)
- `maxBandwidthBytesPerSecond` - Maximum bandwidth (0 = unlimited)
  - `1048576` = 1 MB/s
  - `524288` = 512 KB/s
  - `0` = No limit
- `blacklistedDomains` - Semicolon-separated list of blocked domains
  - `facebook.com` - Blocks exact domain
  - `*.youtube.com` - Blocks all YouTube subdomains
  - Leave empty for no restrictions
- `enabled` - `true` to enable user, `false` to disable

## Admin Panel Features

### User Management

1. **Add New User**
   - Click "Add New User" button
   - Fill in username, password, bandwidth limit, and blacklisted domains
   - Click "Save User"

2. **Edit User**
   - Click "Edit" button next to user
   - Modify any field
   - Click "Save User"

3. **Delete User**
   - Click "Delete" button next to user
   - Confirm deletion

4. **Reload Users**
   - Click "Reload Users" to refresh from CSV file
   - Useful if you manually edited the CSV

### Bandwidth Limits

Set per-user bandwidth in bytes per second:
- `0` = Unlimited
- `131072` = 128 KB/s
- `524288` = 512 KB/s
- `1048576` = 1 MB/s
- `10485760` = 10 MB/s

### Website Blocking

Block websites using domain patterns:

**Exact Match:**
```
facebook.com
```
Blocks only facebook.com

**Wildcard Subdomain:**
```
*.youtube.com
```
Blocks all YouTube subdomains (music.youtube.com, www.youtube.com, etc.)

**Multiple Domains:**
```
facebook.com;twitter.com;*.youtube.com;reddit.com
```
Separate with semicolons

## Client Configuration

### Browser Setup (Firefox)

1. Open Settings → Network Settings
2. Select "Manual proxy configuration"
3. HTTP Proxy: `localhost`, Port: `8080`
4. Check "Use this proxy server for all protocols"
5. Check "Prompt for authentication if password is saved"
6. When prompted, enter username and password from CSV

### Browser Setup (Chrome)

Chrome uses system proxy settings.

**Windows:**
1. Settings → System → Proxy
2. Manual proxy setup
3. HTTP Proxy: `localhost:8080`

**macOS:**
1. System Preferences → Network → Advanced → Proxies
2. Check "Web Proxy (HTTP)"
3. Server: `localhost`, Port: `8080`

### Using curl

```bash
curl -x http://username:password@localhost:8080 https://example.com
```

### Using wget

```bash
wget -e use_proxy=yes -e http_proxy=localhost:8080 --proxy-user=username --proxy-password=password https://example.com
```

## Security Notes

⚠️ **Important Security Considerations:**

1. **Passwords** - Stored in plain text in CSV. For production:
   - Use encrypted passwords (implement custom `CSVUserStore`)
   - Store CSV file with restricted permissions
   - Use HTTPS for admin interface (configure SSL)

2. **Admin Access** - Protect admin interface:
   - Change default password immediately
   - Use strong password
   - Restrict admin port to localhost only
   - Use firewall rules to limit access

3. **Network Security** - The proxy itself uses HTTP:
   - Consider implementing SSL/TLS
   - Use MITM manager for HTTPS inspection if needed
   - Deploy behind a firewall

## Programmatic Usage

You can also use these features programmatically:

```java
// Initialize user store
CSVUserStore userStore = new CSVUserStore("users.csv");

// Create authenticator and filters
UserStoreProxyAuthenticator authenticator = new UserStoreProxyAuthenticator(userStore);
UserFiltersSource filtersSource = new UserFiltersSource(userStore);

// Start proxy with authentication
HttpProxyServer server = DefaultHttpProxyServer.bootstrap()
    .withPort(8080)
    .withProxyAuthenticator(authenticator)
    .withFiltersSource(filtersSource)
    .start();

// Start admin interface
AdminWebServer adminServer = new AdminWebServer(userStore, 9090, "admin123");
adminServer.start();
```

## Troubleshooting

### Users not authenticating
- Check CSV file format is correct
- Ensure user is enabled (`true` in last column)
- Check credentials match exactly (case-sensitive)
- Look at logs for authentication failures

### Admin panel not accessible
- Verify admin port is not blocked by firewall
- Check if port 9090 is already in use
- Try accessing from `127.0.0.1:9090` instead of `localhost:9090`

### Website blocking not working
- Domain format must be exact (e.g., `facebook.com` not `www.facebook.com`)
- Use wildcards for subdomains: `*.facebook.com`
- Check logs to see which domains are being accessed
- Reload users after CSV changes

### Bandwidth limiting not working
- Currently bandwidth limiting is configured per-user in CSV
- Value is in bytes per second (not KB or MB)
- Value of 0 means unlimited

## File Structure

```
LittleProxy/
├── users.csv                                    # User data file (auto-created)
├── src/main/java/org/littleshoot/proxy/
│   └── admin/
│       ├── UserData.java                       # User model
│       ├── CSVUserStore.java                   # CSV storage manager
│       ├── UserStoreProxyAuthenticator.java    # Proxy authentication
│       ├── UserFiltersSource.java              # Traffic filtering
│       ├── AdminWebServer.java                 # Admin API server
│       └── ManagedProxyLauncher.java           # Main launcher
└── src/main/resources/
    └── admin-ui.html                           # Admin web interface
```

## Support

For issues or questions:
- Check the logs for error messages
- Review the CSV file for formatting issues
- Ensure all dependencies are installed
- Verify port numbers are not in use

## License

This enhancement maintains the same license as LittleProxy (Apache License 2.0).
