# LumaSG Discord Integration Security Best Practices

## Overview

Security is paramount when integrating Discord with your Minecraft server. This guide covers essential security practices to protect your bot token, server data, and user privacy while maintaining functionality.

## Bot Token Security

### Critical Security Rules

**NEVER:**
- Share your bot token publicly
- Commit tokens to version control (Git)
- Include tokens in screenshots or documentation
- Log tokens in console output or files
- Send tokens in Discord messages or emails
- Store tokens in plain text on shared systems

**ALWAYS:**
- Keep tokens in secure configuration files
- Use environment variables for production
- Restrict file permissions appropriately
- Rotate tokens regularly
- Monitor for token exposure

### Secure Token Storage

#### Development Environment
```yaml
# config.yml - Acceptable for development
discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
```

**File Permissions:**
```bash
# Restrict access to configuration file
chmod 600 config.yml
chown minecraft:minecraft config.yml
```

#### Production Environment
```yaml
# config.yml - Use environment variable
discord:
  bot-token: "${DISCORD_BOT_TOKEN}"
```

**Environment Variable Setup:**
```bash
# In server startup script or systemd service
export DISCORD_BOT_TOKEN="YOUR_BOT_TOKEN_HERE"
```

#### Docker Environment
```dockerfile
# Dockerfile
ENV DISCORD_BOT_TOKEN=""

# docker-compose.yml
services:
  minecraft:
    environment:
      - DISCORD_BOT_TOKEN=${DISCORD_BOT_TOKEN}
```

### Token Rotation

**When to Rotate:**
- Suspected token exposure
- Team member changes
- Security incident
- Regular maintenance (quarterly)
- Before major deployments

**Rotation Process:**
1. Generate new token in Discord Developer Portal
2. Update configuration with new token
3. Test functionality thoroughly
4. Revoke old token
5. Update documentation

### Token Exposure Response

**If Token is Exposed:**
1. **Immediate Action:**
   - Reset token in Discord Developer Portal
   - Update configuration immediately
   - Restart server/bot
   - Monitor for unauthorized usage

2. **Investigation:**
   - Check where token was exposed
   - Review access logs
   - Identify potential impact
   - Document incident

3. **Prevention:**
   - Implement better storage practices
   - Review security procedures
   - Train team members
   - Update security policies

## Permission Management

### Principle of Least Privilege

Grant only the minimum permissions required for functionality:

#### Bot Permissions
```yaml
# Required permissions only
Bot Permissions:
  - View Channels (1024)
  - Send Messages (2048)
  - Use Slash Commands (2147483648)
  - Embed Links (16384)
  - Read Message History (65536)

# Avoid these unless necessary:
  - Administrator (8)
  - Manage Server (32)
  - Manage Channels (16)
  - Manage Messages (8192)
```

#### Role-Based Access Control
```yaml
discord:
  permissions:
    # Minimal admin access
    admin-roles: ["Owner"]
    
    # Specific role mappings
    role-permissions:
      "Owner": "lumasg.admin"
      "Admin": "lumasg.moderate"
      "Moderator": "lumasg.moderate"
      # Don't use wildcards like "lumasg.*"
```

### Channel Security

#### Channel Permissions Strategy
```yaml
discord:
  channels:
    # Public announcements - read-only for users
    game-announcements: "123456789012345678"
    
    # Statistics - allow command usage
    statistics: "234567890123456789"
    
    # Admin only - restricted access
    admin: "345678901234567890"
```

**Recommended Channel Setup:**

**Game Announcements Channel:**
- @everyone: View Channel, Read Message History
- Bot: View Channel, Send Messages, Embed Links
- No user message permissions

**Statistics Channel:**
- @everyone: View Channel, Read Message History, Use Application Commands
- Bot: View Channel, Send Messages, Embed Links, Use Application Commands
- Allow slash command usage

**Admin Channel:**
- @everyone: No permissions
- Admin Role: View Channel, Read Message History, Use Application Commands
- Bot: View Channel, Send Messages, Embed Links, Use Application Commands

### Command Security

#### Admin Command Protection
```yaml
discord:
  permissions:
    # Restrict admin commands to specific roles
    admin-roles: ["ServerOwner"]  # Very restrictive
    
    # Don't use broad role assignments
    # admin-roles: ["@everyone"]  # NEVER DO THIS
```

#### Command Auditing
```yaml
# Enable logging for admin commands
debug:
  enabled: true  # Log all command usage

# Monitor these commands especially:
# - /force-start
# - /stop-game  
# - /admin-stats
```

## Data Privacy

### Player Information Protection

#### Minimize Data Exposure
```yaml
discord:
  announcements:
    # Consider privacy implications
    include-player-list: false      # Don't expose player names
    use-player-avatars: false       # Don't fetch player skins
    max-players-in-embed: 10        # Limit exposed information
```

#### Statistics Privacy
```yaml
discord:
  features:
    # Consider disabling public stats if privacy is a concern
    enable-stat-commands: false
    
    # Or restrict to specific channels
    statistics-channel-only: true
```

### GDPR Compliance

**Data Collection:**
- Document what player data is sent to Discord
- Implement data retention policies
- Provide data deletion mechanisms
- Obtain consent where required

**Player Rights:**
- Right to access their data
- Right to data portability
- Right to deletion
- Right to rectification

## Network Security

### HTTPS/TLS

**Always Use HTTPS:**
- Discord API uses HTTPS by default
- Webhook URLs must use HTTPS
- Never use HTTP for sensitive data

```yaml
discord:
  webhooks:
    # CORRECT - HTTPS webhook URL
    game-announcements: "https://discord.com/api/webhooks/123/abc"
    
    # WRONG - HTTP webhook URL (won't work anyway)
    # game-announcements: "http://discord.com/api/webhooks/123/abc"
```

### Firewall Configuration

**Outbound Rules:**
```bash
# Allow Discord API access
iptables -A OUTPUT -d discord.com -p tcp --dport 443 -j ACCEPT
iptables -A OUTPUT -d discordapp.com -p tcp --dport 443 -j ACCEPT

# Block unnecessary outbound traffic
iptables -A OUTPUT -p tcp --dport 80 -j DROP  # Block HTTP if not needed
```

**Monitoring:**
```bash
# Monitor Discord connections
netstat -an | grep :443 | grep discord
ss -tuln | grep :443
```

### Rate Limiting Protection

```yaml
discord:
  connection:
    # Implement reasonable limits
    reconnect-attempts: 5
    reconnect-delay-seconds: 30
    timeout-seconds: 30
```

**Application-Level Rate Limiting:**
- Implement message queuing
- Batch similar messages
- Use exponential backoff
- Monitor rate limit headers

## Webhook Security

### Webhook URL Protection

**Treat Webhook URLs as Secrets:**
- Store securely like bot tokens
- Don't log webhook URLs
- Use environment variables
- Rotate regularly

```yaml
discord:
  webhooks:
    # Use environment variables
    game-announcements: "${DISCORD_WEBHOOK_ANNOUNCEMENTS}"
    statistics: "${DISCORD_WEBHOOK_STATS}"
```

### Webhook Validation

**Verify Webhook Integrity:**
```java
// Implement webhook signature validation if available
// Monitor webhook response codes
// Implement fallback mechanisms
```

### Webhook Rotation

**Regular Rotation:**
1. Create new webhook in Discord
2. Update configuration
3. Test functionality
4. Delete old webhook
5. Update documentation

## Monitoring and Auditing

### Security Monitoring

#### Log Analysis
```yaml
debug:
  enabled: true  # Enable for security monitoring
```

**Monitor for:**
- Failed authentication attempts
- Unusual command usage patterns
- Rate limit violations
- Connection anomalies
- Permission escalation attempts

#### Automated Monitoring
```bash
# Monitor for token exposure in logs
grep -r "MTIzNDU2Nzg5" /var/log/ && echo "POTENTIAL TOKEN EXPOSURE"

# Monitor failed connections
grep "Failed to connect" minecraft.log | tail -10

# Monitor admin command usage
grep "admin-stats\|force-start\|stop-game" minecraft.log
```

### Audit Logging

#### Command Auditing
```java
// Log all admin commands with user information
logger.info("Admin command executed: {} by user: {} ({})", 
    command, user.getName(), user.getId());
```

#### Access Logging
```yaml
# Log permission checks
[INFO] User @username#1234 (123456789) executed /admin-stats
[INFO] Permission check: admin-roles contains "Admin" - GRANTED
[INFO] Command completed successfully
```

## Incident Response

### Security Incident Types

1. **Token Compromise**
2. **Unauthorized Access**
3. **Data Breach**
4. **Service Disruption**
5. **Permission Escalation**

### Response Procedures

#### Immediate Response
1. **Assess Impact**
   - What was compromised?
   - How many users affected?
   - What data was exposed?

2. **Contain Incident**
   - Rotate compromised tokens
   - Revoke unauthorized access
   - Disable affected features

3. **Investigate**
   - Review logs
   - Identify attack vector
   - Document timeline

#### Recovery
1. **Restore Service**
   - Implement fixes
   - Test functionality
   - Monitor for issues

2. **Prevent Recurrence**
   - Update security procedures
   - Implement additional controls
   - Train team members

### Incident Documentation

**Required Information:**
- Incident timeline
- Impact assessment
- Response actions taken
- Root cause analysis
- Prevention measures

## Security Checklist

### Pre-Deployment Security Review

- [ ] Bot token stored securely
- [ ] File permissions configured correctly
- [ ] Environment variables used for production
- [ ] Minimal bot permissions granted
- [ ] Role-based access control implemented
- [ ] Channel permissions configured properly
- [ ] Admin commands restricted appropriately
- [ ] Webhook URLs secured
- [ ] Logging and monitoring enabled
- [ ] Incident response procedures documented

### Regular Security Maintenance

**Monthly:**
- [ ] Review access logs
- [ ] Check for security updates
- [ ] Validate permission configurations
- [ ] Test backup procedures

**Quarterly:**
- [ ] Rotate bot tokens
- [ ] Review and update permissions
- [ ] Conduct security assessment
- [ ] Update security documentation

**Annually:**
- [ ] Comprehensive security audit
- [ ] Penetration testing
- [ ] Security training for team
- [ ] Update incident response procedures

## Security Tools and Resources

### Monitoring Tools
- **Log Analysis**: ELK Stack, Splunk, Graylog
- **Network Monitoring**: Wireshark, tcpdump
- **System Monitoring**: Nagios, Zabbix, Prometheus

### Security Scanners
- **Vulnerability Scanners**: Nessus, OpenVAS
- **Code Analysis**: SonarQube, Checkmarx
- **Configuration Auditing**: Lynis, CIS-CAT

### Discord Security Resources
- [Discord Developer Documentation](https://discord.com/developers/docs)
- [Discord Security Best Practices](https://discord.com/safety)
- [JDA Security Guidelines](https://jda.wiki/)

## Compliance Considerations

### Data Protection Regulations

**GDPR (EU):**
- Lawful basis for processing
- Data minimization
- Purpose limitation
- Storage limitation
- Data subject rights

**CCPA (California):**
- Consumer rights
- Data transparency
- Opt-out mechanisms
- Data security requirements

### Industry Standards

**ISO 27001:**
- Information security management
- Risk assessment
- Security controls
- Continuous improvement

**NIST Cybersecurity Framework:**
- Identify
- Protect
- Detect
- Respond
- Recover

---

*This security guide provides essential practices for securing your Discord integration. Regular review and updates are recommended as security threats evolve.*