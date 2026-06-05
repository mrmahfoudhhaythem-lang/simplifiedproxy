# Quick Start Guide - LittleProxy Admin Interface

## Step 1: Build the Project

Open PowerShell in the LittleProxy directory and run:

```powershell
cd C:\Users\examp\Projects\LittleProxy
mvn clean package -Dmaven.test.skip=true
```

This will compile the project and create the JAR file.

## Step 2: Run the Proxy with Admin Interface

After building, run:

```powershell
java -cp target/littleproxy-1.1.3-SNAPSHOT.jar org.littleshoot.proxy.admin.ManagedProxyLauncher
```

You should see output like:
```
==========================================
   LittleProxy with User Management
==========================================
Proxy Port: 8080
Admin Port: 9090
Users File: users.csv
==========================================
...
System Ready!
==========================================
Proxy URL: http://localhost:8080
Admin Panel: http://localhost:9090
Admin Login: admin / admin123
==========================================
```

## Step 3: Access the Admin Panel

1. Open your web browser (Chrome, Firefox, Edge, etc.)
2. Navigate to: **http://localhost:9090/**
3. When prompted for login:
   - **Username:** `admin`
   - **Password:** `admin123`

You'll see a modern admin interface where you can:
- View all proxy users
- Add new users
- Edit existing users
- Set bandwidth limits
- Configure blocked websites
- Delete users

## Step 4: Configure a Browser to Use the Proxy

### Option A: Firefox (Easiest for Testing)

1. Open Firefox Settings (≡ menu → Settings)
2. Scroll down to **Network Settings** → Click **Settings...**
3. Select **Manual proxy configuration**
4. Enter:
   - **HTTP Proxy:** `localhost`
   - **Port:** `8080`
5. Check **"Also use this proxy for HTTPS"**
6. Click **OK**

When you browse any website, Firefox will prompt for credentials:
- Use one of the usernames from the admin panel (default: `admin` or `user1`)
- Enter the corresponding password

### Option B: Chrome (Uses System Proxy)

Chrome uses Windows system proxy settings.

## Step 5: Test It Out

1. **In Admin Panel (http://localhost:9090/):**
   - The default `users.csv` file includes two test users:
     - `admin` / `admin123` - unlimited bandwidth, no restrictions
     - `user1` / `user1` - 1 B/s bandwidth, blocks *.youtube.com

2. **Add a new user:**
   - Click "➕ Add New User"
   - Enter username (e.g., `testuser`)
   - Enter password (e.g., `test123`)
   - Set bandwidth (e.g., `524288` = 512 KB/s, or `0` = unlimited)
   - Add blocked sites (e.g., `twitter.com;reddit.com`)
   - Click "💾 Save User"

3. **In your browser:**
   - Configure Firefox to use the proxy (see Step 4)
   - Try visiting a website
   - Enter the credentials (`testuser` / `test123`)
   - Try visiting a blocked site (like twitter.com) - you should see "Access Blocked" message

## Default Users (in users.csv)

The repository includes a default `users.csv` file with:

| Username | Password  | Bandwidth | Blocked Sites |
|----------|-----------|-----------|---------------|
| admin    | admin123  | Unlimited | None |
| user1    | user1     | 1 B/s     | *.youtube.com |

## Common Commands

### Start with custom ports:
```powershell
java -cp target/littleproxy-1.1.3-SNAPSHOT.jar org.littleshoot.proxy.admin.ManagedProxyLauncher --port 8888 --admin-port 8889
```

### Change admin password:
```powershell
java -cp target/littleproxy-1.1.3-SNAPSHOT.jar org.littleshoot.proxy.admin.ManagedProxyLauncher --admin-password MySecurePassword
```

### Use custom users file:
```powershell
java -cp target/littleproxy-1.1.3-SNAPSHOT.jar org.littleshoot.proxy.admin.ManagedProxyLauncher --users-file C:\path\to\myusers.csv
```

## Stopping the Proxy

Press **Ctrl+C** in the PowerShell window where the proxy is running.

## Understanding the users.csv File

The file is located at: `C:\Users\examp\Projects\LittleProxy\users.csv`

You can edit it directly with Notepad or Excel:
```csv
username,password,maxBandwidthBytesPerSecond,blacklistedDomains,enabled
admin,admin123,0,,true
user1,user1,1,*.youtube.com,true
```

**Bandwidth values:**
- `0` = Unlimited
- `131072` = 128 KB/s
- `524288` = 512 KB/s  
- `1048576` = 1 MB/s
- `10485760` = 10 MB/s

**Blocked domains:**
- `facebook.com` - blocks facebook.com
- `*.youtube.com` - blocks all YouTube subdomains
- Multiple domains: `facebook.com;twitter.com;reddit.com`

## Troubleshooting

### "Port already in use" error
Another program is using port 8080 or 9090. Use different ports:
```powershell
java -cp target/littleproxy-1.1.3-SNAPSHOT.jar org.littleshoot.proxy.admin.ManagedProxyLauncher --port 8081 --admin-port 9091
```

### Can't access admin panel
- Make sure the proxy is running (check PowerShell window)
- Try: http://127.0.0.1:9090/ instead of localhost
- Check Windows Firewall isn't blocking the port

### Browser not prompting for credentials
- Make sure proxy settings are correctly configured in browser
- Try a different browser (Firefox is easiest for testing)
- Check that the user exists in the admin panel

### Changes not taking effect
- Click "🔄 Reload Users" in the admin panel
- Or restart the proxy

## Next Steps

- **Add more users** through the admin panel
- **Set bandwidth limits** to control usage
- **Block websites** by adding domains to user restrictions
- **Monitor logs** in the PowerShell window to see traffic
- **Edit users.csv** directly for bulk changes (then reload in admin panel)

## Support

Check the logs in the PowerShell window for error messages. Most issues can be resolved by:
1. Verifying port numbers aren't in use
2. Checking users.csv format
3. Ensuring proxy settings in browser are correct
