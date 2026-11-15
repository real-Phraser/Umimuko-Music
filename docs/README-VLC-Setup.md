## ⚙️ VLC Setup for Windows Developers

Umimuko Music embeds VLC internally for audio playback on Windows. VLC binaries are **not included** in the git repository due to size.

When packaging or creating distributables on Windows (`createDistributable`, `packageExe`, `packageMsi`), the build script requires a compatible VLC 3.x installation.

---

### Supported Architectures

- **Windows x64** (`x86_64` / `amd64`)
- **Windows ARM64** (`aarch64` / `arm64`)

---

### 1. Automatic Download (Default)

The build script will automatically attempt to download and cache the appropriate standalone VLC 3.x distribution for your machine architecture into `.gradle/vlc/`:

- **x64 Windows**: Downloads VLC 3.0.21 (`win64.zip`)
- **ARM64 Windows**: Downloads VLC 3.0.22 (`winarm64.zip`)

If you have internet access during the build, no manual setup is required.

---

### 2. Manual Setup (Offline / Custom VLC)

If building offline or using a pre-installed VLC, download the matching version for your architecture:

#### For 64-bit x64 Windows:
1. Download **VLC 3.x (64-bit Windows)** from [videolan.org](https://www.videolan.org/vlc) or the [VideoLAN win64 archive](https://download.videolan.org/pub/videolan/vlc/).
2. Install or extract it.

#### For Windows ARM64 (Surface Pro X, Snapdragon X Elite, etc.):
1. Download **VLC 3.x (Windows ARM64)** from the official VideoLAN release mirror:
   - [VideoLAN VLC 3.0.22 winarm64](https://download.videolan.org/pub/videolan/vlc/3.0.22/winarm64/) (e.g. `vlc-3.0.22-winarm64.zip` or `.7z`)
   - Or the latest release directory: [VideoLAN Last winarm64](https://download.videolan.org/pub/videolan/vlc/last/winarm64/)
2. Extract or install the ARM64 binaries.

---

### Providing VLC to the Build

You can provide the VLC files using any of the following methods (in order of priority):

1. **Gradle property:**
   ```bash
   ./gradlew createDistributable -PvlcDir="C:\Path\To\VLC"
   ```
2. **Environment variable:**
   ```bash
   set UMIMUKO_VLC_DIR=C:\Path\To\VLC
   ```
3. **Local project directory:**
   Copy the entire VLC folder into the repository root as `vlc/`:
   ```
   vlc/
   ├── libvlc.dll
   ├── libvlccore.dll
   └── plugins/
       ├── audio_output/
       ├── codec/
       └── ...
   ```
4. **Default system installation:**
   Install VLC into `C:\Program Files\VideoLAN\VLC`.

---

### Architecture Validation

During the build, the `validateBundledVlc` task verifies `libvlc.dll` to ensure binary compatibility:
- Validates the PE header machine type:
  - **x64**: `IMAGE_FILE_MACHINE_AMD64` (`0x8664`)
  - **ARM64**: `IMAGE_FILE_MACHINE_ARM64` (`0xAA64`)
- Ensures required libraries (`libvlc.dll`, `libvlccore.dll`) and `plugins/` directory exist.

If an incompatible architecture is detected (e.g. using an x64 VLC installation on an ARM64 Windows machine), the build will fail with an explanatory message.
