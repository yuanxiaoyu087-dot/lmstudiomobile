# LM Studio Mobile (Android)

A fully native Android application for running Large Language Models locally on mobile devices.

## Features

- **Local LLM Inference**: Run LLMs directly on your Android device using llama.cpp
- **Model Management**: Import, load, and manage GGUF/GGML models
- **Chat Interface**: Clean, modern chat UI with streaming responses
- **Model Downloads**: Browse and download models from Hugging Face Hub
- **Local API Server**: RESTful API server compatible with LM Studio API specification
- **Resource Monitoring**: Real-time CPU, RAM, and GPU usage monitoring
- **Folder Management**: Organize chats into folders
- **Dark/Light Theme**: Dynamic theming support

## Architecture

The app follows Clean Architecture principles with:

- **UI Layer**: Jetpack Compose with Material3
- **Domain Layer**: Business logic and use cases
- **Data Layer**: Room database, repositories, and remote APIs
- **LLM Engine**: llama.cpp via JNI with Vulkan/OpenGL acceleration

## Technology Stack

- **Language**: Kotlin (95%), Java, C++
- **UI**: Jetpack Compose, Material3
- **Database**: Room (SQLite)
- **Dependency Injection**: Hilt
- **Networking**: Retrofit, OkHttp
- **LLM Runtime**: llama.cpp (via JNI)
- **Acceleration**: Vulkan (preferred), OpenGL ES (fallback)
- **Local Server**: NanoHTTPD

## Setup Instructions

### Prerequisites

1. Android Studio Hedgehog or later
2. Android SDK 24+ (Android 7.0+)
3. NDK (for native code compilation)
4. CMake 3.22.1+

### Building

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build the project

### Native Code Setup

The app uses llama.cpp for LLM inference. To integrate:

1. Add llama.cpp as a submodule or download it:
   ```bash
   git submodule add https://github.com/ggerganov/llama.cpp.git app/src/main/cpp/llama.cpp
   ```

2. Update `app/src/main/cpp/CMakeLists.txt` to uncomment llama.cpp integration

3. Build the native library

### Model Formats Supported

- **GGUF** (primary): Recommended format for mobile devices
- **GGML** (legacy): Older format, still supported
- **PyTorch Mobile**: Future support planned

## Project Structure

```
app/src/main/java/com/lmstudio/mobile/
├── data/              # Data layer
│   ├── local/         # Room database, preferences, storage
│   ├── remote/        # API clients and DTOs
│   └── repository/    # Repository implementations
├── domain/            # Domain layer
│   ├── model/         # Domain models
│   └── usecase/       # Use cases (to be implemented)
├── llm/               # LLM engine layer
│   ├── engine/        # LLM engine interfaces and implementations
│   ├── inference/     # Inference management
│   └── acceleration/  # GPU acceleration
├── server/            # Local API server
├── ui/               # UI layer
│   ├── screens/       # Screen composables
│   ├── components/    # Reusable components
│   └── theme/         # Material3 theme
├── service/          # Background services
└── util/             # Utilities
```

## Permissions

The app requires the following permissions:

- **INTERNET**: For downloading models and running local server
- **READ/WRITE_EXTERNAL_STORAGE**: For model file access
- **WAKE_LOCK**: To keep CPU running during inference
- **FOREGROUND_SERVICE**: For background model inference

## Development Status

This is an initial implementation. Key areas still requiring work:

1. **Native Integration**: Complete llama.cpp JNI bindings
2. **Model Downloads**: Full Hugging Face integration
3. **Streaming**: Server-Sent Events for API responses
4. **Performance**: GPU acceleration optimization
5. **Testing**: Unit and integration tests

## Contributing

This is a comprehensive project structure. Contributions welcome!

## License

[Add your license here]

## Acknowledgments

- llama.cpp by ggerganov
- LM Studio for API specification inspiration

