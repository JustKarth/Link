# Link — Your Files. Your Devices. One Connection.

**Discover nearby devices. Connect directly. Share files.**

Link is a JavaFX desktop application designed to make file sharing between devices on the same local network feel seamless. It combines peer discovery, secure-connection architecture, file-transfer management, and a modern radar-inspired interface in one desktop experience.

Instead of relying on a traditional centralized file-hosting workflow, Link is designed around direct peer-to-peer communication between devices.

---

## Screenshots

### Orbit Discovery — Nearby Devices

Discover nearby peers through Link's radar-inspired interface. View device availability, connection details, and the surrounding discovery space.

![Link Orbit Discovery screen showing a nearby peer](docs/images/nearby-devices.png)

### More Screenshots

Additional screenshots of the Transfers, Activity, Devices, and Settings screens can be added here as the interface develops.

---

## Key Features

- **Peer Discovery:** Discover devices on the local network using UDP-based discovery.
- **Direct Connections:** Designed for direct communication between peers without a central file-hosting server.
- **Nearby Device Interface:** Visualize discovered peers through a radar-inspired desktop interface.
- **Transfer Management:** Organize transfer activity and display transfer progress and states.
- **Incoming Transfer Workflow:** Support for accepting or rejecting incoming transfer requests.
- **Secure Communication Architecture:** TLS-based communication components for protected connections.
- **File Integrity:** SHA-256-based integrity verification components.
- **Device Management:** A dedicated interface for viewing and managing device-related information.
- **Modern Desktop UI:** A dark interface with cyan and violet accents, custom components, and a networking-inspired visual identity.
- **JavaFX Application:** A native desktop experience built with Java and JavaFX.

---

## Architecture

Link follows a layered architecture that separates the user interface, application coordination, peer discovery, networking, and file-system operations.

```text
                         LINK
                          |
             +------------+------------+
             |                         |
        JavaFX UI                 Application Layer
             |                         |
     +-------+-------+         Transfer Coordination
     |       |       |                  |
   Nearby Transfers Settings            |
     |                                  |
     +----------------+-----------------+
                      |
               Discovery Layer
                      |
                UDP Discovery
                      |
               Peer Information
                      |
               Networking Layer
                      |
              Connection Management
                      |
                TLS Sessions
                      |
              Transfer Processing
                      |
          +-----------+-----------+
          |                       |
    File Storage             Integrity Checks
                              SHA-256
```

### Core Components

| Component | Responsibility |
|---|---|
| JavaFX UI | Displays nearby devices, transfers, activity, and settings. |
| `LinkUIApplication` | Application entry point for the desktop interface. |
| `LinkViewModel` | Connects UI interactions with application-level operations. |
| `LinkEngine` | Provides the core application engine. |
| `TransferCoordinator` | Coordinates transfer-related operations between application components. |
| `UdpDiscoveryService` | Handles UDP-based peer discovery. |
| `ConnectionManager` | Manages peer connection operations. |
| `DefaultSecureSession` | Provides secure-session functionality. |
| `FileStorageService` | Handles file-storage operations. |
| `IntegrityService` | Provides file-integrity verification functionality. |

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Java | Core application and networking logic. |
| JavaFX | Desktop user interface. |
| Maven | Dependency management and project builds. |
| UDP | Local-network peer discovery. |
| TCP | Connection-oriented communication. |
| TLS | Encrypted communication architecture. |
| SHA-256 | File-integrity verification. |
| SQLite | Local persistence for supported application data. |
| JUnit | Automated testing. |
| SLF4J & Logback | Application logging. |

---

## How Link Works

### 1. Discover Nearby Devices

Link uses UDP-based discovery to identify peers on the local network. Discovered devices can be presented in the Nearby interface.

### 2. Establish a Connection

The application is designed to establish direct connections between peers. TCP provides connection-oriented communication, while TLS components are intended to protect communication.

### 3. Initiate a Transfer

A user can select files and initiate a transfer through the desktop interface. Incoming-transfer workflows provide a way to accept or reject requests.

### 4. Process Files and Verify Integrity

File-handling components manage storage operations, while SHA-256-based integrity checks help detect changes to transferred content.

### 5. Manage Transfer Activity

The interface separates nearby-device discovery from transfer management, activity, device information, and settings.

---

## Getting Started

### Prerequisites

Make sure you have the following installed:

- Java Development Kit (JDK) compatible with the project's Maven configuration
- Apache Maven
- Git

Verify your installation:

```bash
java -version
mvn -version
git --version
```

### Clone the Repository

```bash
git clone https://github.com/JustKarth/Link.git
cd Link
```

### Build the Project

```bash
mvn clean package
```

### Run the Application

```bash
mvn javafx:run
```

### Run Tests

```bash
mvn clean test
```

---

## Project Structure

The project separates desktop UI components from the underlying application, networking, discovery, and file-handling logic.

```text
Link/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── link/
│   │   │           ├── app/
│   │   │           ├── application/
│   │   │           ├── discovery/
│   │   │           ├── domain/
│   │   │           ├── filesystem/
│   │   │           ├── networking/
│   │   │           └── ui/
│   │   └── resources/
│   └── test/
├── docs/
│   └── images/
│       └── nearby-devices.png
└── ...
```

*Note: The tree illustrates the main architectural areas; the exact package and resource layout may differ in your checkout.*

---

## Design Philosophy

Link is built around three ideas:

### Direct

Devices should be able to communicate directly without depending on a centralized file-hosting workflow.

### Visual

Network discovery should be understandable at a glance. The radar-inspired interface turns the idea of nearby peers into a central part of the user experience.

### Seamless

Finding devices, choosing files, handling transfer requests, and monitoring activity should feel like parts of one coherent desktop application.

---

## Security Considerations

Security is an important part of Link's design.

- TLS components are intended to protect communication in transit.
- SHA-256 integrity checks help verify file content.
- Incoming-transfer approval provides a user-controlled decision point.
- Device trust and peer authentication must be implemented and validated carefully.

**Important:** Encryption alone does not prove the identity of the remote peer. Certificate validation, peer authentication, and trust establishment must be correctly configured for the connection to provide meaningful protection against impersonation. A matching file hash also does not, by itself, establish who sent the file.

---

## Future Improvements

Potential areas for further development include:

- Reliable end-to-end transfers across different devices.
- Robust peer authentication and trusted-device management.
- Pause, resume, and retry support connected to the actual transfer engine.
- Better handling of peer disconnections and network changes.
- Clearer error reporting and recovery.
- Expanded automated tests for discovery, secure connections, and file transfers.
- Additional screenshots and usage documentation.

---

## Contributing

Contributions and suggestions are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make your changes.
4. Run the test suite.
5. Submit a pull request describing the changes.

For substantial changes, consider discussing the proposed approach with the project maintainers first.

---

**Link — Connect. Share. Together.**