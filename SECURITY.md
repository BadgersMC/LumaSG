# Security Guidelines

## Repository Security Checklist

This document outlines the security measures taken to ensure this repository is safe for public distribution.

### ✅ Completed Security Measures

- **No Hardcoded Credentials**: No passwords, API keys, or tokens in source code
- **No Sensitive URLs**: No localhost addresses or internal server references
- **Clean Build Scripts**: Deployment scripts contain no sensitive information
- **Proper .gitignore**: Comprehensive gitignore to prevent accidental commits of:
  - Database files (*.db, *.sqlite)
  - Environment files (.env*)
  - Credentials and keys (*.key, *.pem, etc.)
  - Maps and worlds (copyrighted content)
  - IDE and OS specific files
  - Build artifacts and temporary files

### 🔒 Protected Information

The following types of information are properly excluded from the repository:

- **Database Credentials**: No database connection strings or credentials
- **Server Configurations**: No server-specific configuration files
- **Maps/Worlds**: No copyrighted Minecraft maps included
- **Personal Data**: No player data or server logs
- **API Keys**: No external service API keys or tokens

### 📋 Pre-Commit Checklist

Before pushing code, ensure:

- [ ] No new hardcoded credentials added
- [ ] No sensitive file paths or URLs added
- [ ] All new files added to .gitignore if they contain sensitive data
- [ ] Build scripts remain clean and generic
- [ ] No personal or server-specific information in commits

### 🗺️ Map Usage Guidelines

This plugin does not include any maps. Users must:

1. Obtain maps from legitimate sources (e.g., [MCSG Archive](https://mcsgarchive.com/))
2. Ensure they have permission from original map creators
3. Respect all licensing and attribution requirements
4. Never redistribute copyrighted maps without permission

### 🚨 Reporting Security Issues

If you discover any security vulnerabilities or sensitive information in this repository:

1. **DO NOT** create a public issue
2. Contact the maintainers privately
3. Provide details about the security concern
4. Allow time for the issue to be addressed before public disclosure

### 📝 Code Review Guidelines

All code contributions should be reviewed for:

- Hardcoded sensitive information
- Proper error handling that doesn't leak sensitive data
- Secure coding practices
- Appropriate logging levels (no sensitive data in logs)

---

**Last Updated**: January 2025
**Reviewed By**: Repository Maintainers 