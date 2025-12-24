# Setup Guide

## Prerequisites

1. **Android Studio**: Hedgehog (2023.1.1) or later
2. **JDK**: 17 or later
3. **Android SDK**: 
   - Minimum SDK: 24 (Android 7.0)
   - Target SDK: 34 (Android 14)
   - Compile SDK: 34
4. **NDK**: Required for native code compilation
5. **CMake**: 3.22.1 or later

## Initial Setup

### 1. Clone and Open Project

```bash
git clone <repository-url>
cd lm_studio_mobile
```

Open the project in Android Studio.

### 2. Install Required SDK Components

In Android Studio:
- Go to **Tools > SDK Manager**
- Install:
  - Android SDK Platform 34
  - Android SDK Build-Tools
  - CMake
  - NDK (Side by side)

### 3. Set Up llama.cpp (Required for LLM Functionality)

The native code requires llama.cpp. You have two options:

#### Option A: Add as Submodule (Recommended)

```bash
cd app/src/main/cpp
git submodule add https://github.com/ggerganov/llama.cpp.git llama.cpp
```

#### Option B: Manual Download

1. Download llama.cpp from https://github.com/ggerganov/llama.cpp
2. Extract to `app/src/main/cpp/llama.cpp`
3. Update `app/src/main/cpp/CMakeLists.txt` to uncomment llama.cpp integration

### 4. Update CMakeLists.txt

Edit `app/src/main/cpp/CMakeLists.txt` and uncomment:

```cmake
add_subdirectory(llama.cpp)
```

And update the target_link_libraries:

```cmake
target_link_libraries(llama_jni
    llama
    ${log-lib}
    ${android-lib}
    ${Vulkan_LIBRARIES}
    GLESv3
)
```

### 5. Sync and Build

1. Click **File > Sync Project with Gradle Files**
2. Wait for Gradle sync to complete
3. Build the project: **Build > Make Project**

## Running the App

### On Emulator

1. Create an Android Virtual Device (AVD) with API 24+
2. Run: **Run > Run 'app'**

### On Physical Device

1. Enable **Developer Options** and **USB Debugging**
2. Connect device via USB
3. Run: **Run > Run 'app'**

## Project Structure Overview

```
lm_studio_mobile/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/lmstudio/mobile/
│   │   │   │   ├── data/          # Data layer
│   │   │   │   ├── domain/        # Domain models
│   │   │   │   ├── llm/          # LLM engine
│   │   │   │   ├── server/       # Local API server
│   │   │   │   ├── ui/           # UI components
│   │   │   │   └── di/           # Dependency injection
│   │   │   ├── cpp/              # Native C++ code
│   │   │   └── res/              # Resources
│   │   └── test/                 # Unit tests
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Key Dependencies

- **Jetpack Compose**: Modern UI toolkit
- **Room**: Local database
- **Hilt**: Dependency injection
- **Retrofit**: HTTP client
- **NanoHTTPD**: Embedded HTTP server
- **llama.cpp**: LLM inference engine (native)

## Troubleshooting

### Build Errors

1. **NDK not found**: Install NDK from SDK Manager
2. **CMake errors**: Ensure CMake 3.22.1+ is installed
3. **llama.cpp not found**: Follow step 3 above to add llama.cpp

### Runtime Issues

1. **App crashes on model load**: Ensure model file exists and is valid GGUF format
2. **Native library not found**: Check that NDK build completed successfully
3. **Permission denied**: Grant storage permissions in app settings

## Next Steps

1. **Add Models**: Place GGUF model files in the app's external storage directory
2. **Test Inference**: Load a model and test chat functionality
3. **Configure Server**: Set up local API server port in settings

## Development Notes

- The native JNI bindings are placeholders - full llama.cpp integration required
- GPU acceleration (Vulkan) requires device support
- Model downloads from Hugging Face require internet connection

