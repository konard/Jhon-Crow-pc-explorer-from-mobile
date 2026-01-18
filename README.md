# PC Explorer from Mobile

An Android application for accessing PC files through USB connection.

## Overview

PC Explorer allows you to browse, copy, delete, and transfer files between your Android device and PC through a direct USB connection using a standard USB cable.

## Features

- **Automatic PC Detection** - Detects USB connection and requests necessary permissions
- **File Browser** - Navigate directories, view files with type-specific icons, sort by name/date/size
- **File Operations** - Copy, delete, rename files and create new folders
- **File Transfer** - Download files from PC to Android, upload files to PC
- **Transfer History** - Track transfer progress and view history
- **Dark/Light Theme** - Supports system theme and manual theme selection
- **Material Design 3** - Modern UI following Material Design guidelines

## Project Structure

```
pc-explorer-from-mobile/
├── app/                      # Main Android application module
├── core/                     # Core modules
│   ├── common/              # Utilities, extensions, logging
│   ├── data/                # Repositories, database, USB protocol
│   └── domain/              # Models, use cases, repository interfaces
├── features/                 # Feature modules
│   ├── connection/          # USB connection UI
│   ├── browser/             # File browser UI
│   ├── transfer/            # File transfer UI
│   └── settings/            # Settings UI
├── shared/                   # Shared UI components
├── pc-server/               # Python server for PC (companion app)
└── .github/workflows/       # CI/CD configuration
```

## Technology Stack

### Android Application

| Component | Technology |
|-----------|------------|
| Language | Kotlin 100% |
| Architecture | Clean Architecture + MVVM |
| Async | Kotlin Coroutines + Flow |
| DI | Dagger Hilt |
| UI | Jetpack Compose |
| Navigation | Navigation Component |
| Local Storage | Room Database |
| Logging | Timber |

### PC Server

| Component | Technology |
|-----------|------------|
| Language | Python 3.10+ |
| USB | pyusb |

## Requirements

### Android App
- Android 8.0 (API 26) or higher
- USB Host support
- No root required

### PC Server
- Python 3.10+
- pyusb library
- libusb (system library)

## Getting Started

### Building the Android App

1. Clone the repository:
   ```bash
   git clone https://github.com/Jhon-Crow/pc-explorer-from-mobile.git
   cd pc-explorer-from-mobile
   ```

2. Open in Android Studio (Hedgehog or later)

3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install on your Android device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Running the PC Server

1. Install dependencies:
   ```bash
   cd pc-server
   pip install -r requirements.txt
   ```

2. Run the server:
   ```bash
   python server.py
   ```

   For simulation mode (testing without USB):
   ```bash
   python server.py --simulate
   ```

### Testing with Simulation Mode

For development and testing without physical USB connection:

1. Start the server in simulation mode:
   ```bash
   cd pc-server
   python server.py --simulate
   ```

2. Set up ADB port forwarding:
   ```bash
   adb forward tcp:5555 tcp:5555
   ```

3. The Android app can then connect through TCP for testing

## USB Protocol

The application uses a custom binary protocol for USB communication:

### Packet Structure
```
| Magic (4) | Command (1) | Flags (1) | Length (4) | Payload (var) | Checksum (4) |
```

### Commands
- `0x01` - Handshake
- `0x02` - List Directory
- `0x03` - Get File Info
- `0x04` - Read File
- `0x05` - Write File
- `0x06` - Create Directory
- `0x07` - Delete
- `0x08` - Rename
- `0x09` - Search
- `0x0A` - Get Drives
- `0x0B` - Get Storage Info

## Permissions

The app requires the following permissions:

- `USB_HOST` - For USB device communication
- `READ/WRITE_EXTERNAL_STORAGE` - For file operations on Android
- `FOREGROUND_SERVICE` - For background file transfers
- `WAKE_LOCK` - To keep device awake during transfers

## Architecture

The project follows Clean Architecture principles:

```
┌─────────────────────────────────────────────┐
│                 Presentation                 │
│  (Compose UI, ViewModels, Navigation)       │
├─────────────────────────────────────────────┤
│                   Domain                     │
│  (Use Cases, Repository Interfaces, Models) │
├─────────────────────────────────────────────┤
│                    Data                      │
│  (Repository Impl, USB Protocol, Database)  │
└─────────────────────────────────────────────┘
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Material Design 3 for design guidelines
- Jetpack Compose for modern Android UI
- Android USB Host API for USB communication
