# Security Policy

## Supported Versions

We actively maintain and provide security updates for the current major releases of Sielo:

| Version | Supported          |
| ------- | ------------------ |
| 10.x.x  | :white_check_mark: |
| < 10.0  | :x:                |

---

## Security Architecture

Sielo is designed with a privacy-first, zero-knowledge philosophy:

### 1. End-to-End Encrypted Listen Together
- **AES-256-GCM**: Collaborative room sessions utilize client-side authenticated encryption (AES-256-GCM).
- **Zero-Knowledge Relay**: Relay brokers and servers only pass encrypted payloads. Room chat messages, usernames, song IDs, and playback commands cannot be inspected or decrypted by intermediaries.
- **Dynamic Ephemeral Keys**: Session encryption keys are generated locally on the host's device and shared directly via QR code or secure out-of-band room invite.

### 2. Audio & Metadata Transmission
- **Strict HTTPS / TLS**: All network requests for search, lyrics, Cover Art Archive, and audio stream resolution are routed over encrypted TLS 1.3/1.2 channels.
- **Client-Side Resolution**: Music metadata and stream extraction occur strictly on the user's device. No user tracking profiles or persistent listening identifiers are sold or sent to third-party ad networks.

### 3. Local Data Storage
- **On-Device Storage**: Listening history, taste profiles, and preferences are stored exclusively on the device using Android's local Room database and encrypted SharedPreferences where applicable.
- **Clean Logout**: Logging out immediately clears user credentials, OAuth tokens, and session cache.

---

## Reporting a Vulnerability

We take the security and privacy of Sielo users seriously. If you discover a security vulnerability or potential exploit, please do **NOT** open a public issue on GitHub.

Instead, please report it responsibly:

1. **Email**: Send details of the vulnerability to [vineetchudasama@gmail.com](mailto:vineetchudasama@gmail.com) (or via GitHub Security Advisories).
2. **Details to Include**:
   - Description of the vulnerability and affected components.
   - Proof-of-concept (PoC) or reproduction steps.
   - Any potential impact on user data or playback sessions.
3. **Response Timeline**:
   - We will acknowledge receipt of your vulnerability report within **48 hours**.
   - We will provide a status update or fix within **7 business days**.
   - Once a patch is released, you will be credited in our release notes (unless you prefer to remain anonymous).

---

## Third-Party Services Disclaimer

Sielo interfaces with public APIs and open music databases (including YouTube Music InnerTube, JioSaavn, MusicBrainz, Cover Art Archive, and LRCLIB). Users are responsible for complying with the terms of service of any third-party services they interact with through the application.
