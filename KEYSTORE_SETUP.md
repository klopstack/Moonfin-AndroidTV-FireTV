# Release Keystore Setup

This document describes how to set up release keystore configuration for signing Moonfin APKs.

## Overview

To sign release builds, you need:
1. A release keystore file (`.keystore` or `.jks`)
2. A `keystore.properties` file with signing credentials

Both files are excluded from git via `.gitignore` for security.

## Creating a Release Keystore

Generate a new keystore with the `keytool` command:

```bash
# Generate keystore with strong random password
KEYSTORE_PASSWORD=$(openssl rand -base64 32 | tr -d '\n')

keytool -genkeypair -v \
  -keystore moonfin-release.keystore \
  -alias moonfin \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEYSTORE_PASSWORD" \
  -dname "CN=Moonfin, OU=Development, O=Moonfin, L=Your City, ST=Your State, C=US"

echo "Store this password securely: $KEYSTORE_PASSWORD"
```

**Important:** Save the password immediately - you cannot recover it later!

## Configuring `keystore.properties`

Create `keystore.properties` in the project root:

```properties
storeFile=moonfin-release.keystore
storePassword=YOUR_STORE_PASSWORD_HERE
keyAlias=moonfin
keyPassword=YOUR_KEY_PASSWORD_HERE
```

**⚠️ IMPORTANT:** This file is excluded from git via `.gitignore`. Keep it backed up securely!

## Getting Certificate Fingerprints

For app store submissions, you may need certificate fingerprints:

```bash
keytool -list -v -keystore moonfin-release.keystore -storepass "YOUR_PASSWORD"
```

Look for the SHA1 and SHA256 fingerprints in the output.

## Building Signed Releases

### Local Builds
```bash
./gradlew assembleRelease
```

The signed APK will be in: `app/build/outputs/apk/release/`

### GitHub Actions Workflow Setup

To enable consistent signing in CI/CD, add these secrets to your GitHub repository:

### Required Secrets

Go to: **Repository Settings → Secrets and variables → Actions → New repository secret**

#### Debug Keystore (for consistent debug builds)

1. **DEBUG_KEYSTORE_BASE64:** Base64-encoded debug keystore file
   ```bash
   base64 -w 0 ~/.android/debug.keystore
   ```

#### Release Keystore (for production builds)

2. **KEYSTORE_BASE64:** Base64-encoded release keystore file
   ```bash
   base64 -w 0 moonfin-release.keystore
   ```

3. **KEYSTORE_PASSWORD:** Your keystore store password

4. **KEY_ALIAS:** Your key alias (e.g., `moonfin`)

5. **KEY_PASSWORD:** Your key password

## Backup Instructions

### Create Backup
```bash
# Create a secure backup of both files
tar -czf moonfin-keystore-backup-$(date +%Y%m%d).tar.gz \
  moonfin-release.keystore \
  keystore.properties

# Encrypt the backup (optional but recommended)
gpg -c moonfin-keystore-backup-$(date +%Y%m%d).tar.gz
```

### Restore from Backup
```bash
# Decrypt if encrypted
gpg -d moonfin-keystore-backup-YYYYMMDD.tar.gz.gpg > moonfin-keystore-backup-YYYYMMDD.tar.gz

# Extract to project root
tar -xzf moonfin-keystore-backup-YYYYMMDD.tar.gz
```

## Security Notes

1. **Never commit** `keystore.properties` or `*.keystore` files to git
2. **Store backups** in multiple secure locations (password manager, encrypted storage, etc.)
3. **Losing the keystore** means you cannot update published apps - only release new ones
4. The keystore password is cryptographically random and cannot be recovered if lost
5. Consider using a password manager to store the credentials securely

## Verifying the Keystore

To verify the keystore is working:
```bash
keytool -list -v -keystore moonfin-release.keystore -storepass "71b1DaV5Vu3Sdn+MON6Rhi7wnRN5aEtsEuqRRGxe57c="
```

## Build Configurations

The project is configured to:
- **Debug builds:** Always use default Android debug signing (consistent across environments)
- **Release builds:** Use `moonfin-release.keystore` when `keystore.properties` exists

This ensures debug builds from GitHub Actions can be installed alongside locally-built debug builds without signature conflicts.
