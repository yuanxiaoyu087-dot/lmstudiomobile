# Руководство по просмотру детальных логов

## 📋 Обзор логирования

В проекте добавлено детальное логирование во все критические компоненты. Каждое действие приложения будет записано в Android Studio Logcat.

## 🔍 Компоненты с логированием

### 1. **InferenceManager** (`TAG = "InferenceManager"`)
Логирует:
- Инициализацию менеджера
- Проверку загруженности модели
- Загрузку и выгрузку моделей
- Обнаружение типа модели и применение chat template
- Все запросы на генерацию ответов
- Получение токенов

**Пример логов:**
```
I/InferenceManager: loadModel START: path=/storage/.../gemma-3-1b-it.Q2_K.gguf, nThreads=4, nGpuLayers=0, contextSize=8192
D/InferenceManager: buildPrompt: detecting template for model 'gemma-3-1b-it' from messages
I/InferenceManager: generateCompletion START: messageCount=1, state=READY
V/InferenceManager: generateCompletion token: 'Hello'
I/InferenceManager: generateCompletion COMPLETE
```

### 2. **LlamaCppEngine** (`TAG = "LlamaCppEngine"`)
Логирует:
- Загрузку native библиотеки
- Инициализацию JNI контекста
- Параметры загрузки модели
- Поддержку Vulkan GPU
- Процесс генерации токенов
- Максимум токенов (обрезка)
- Выгрузку модели
- Использование ресурсов

**Пример логов:**
```
I/LlamaCppEngine: Native library 'llama_jni' loaded successfully
I/LlamaCppEngine: loadModel: path=/path/to/model.gguf, threads=4, gpuLayers=0, contextSize=8192
D/LlamaCppEngine: loadModel: vulkanAvailable=true
I/LlamaCppEngine: generateResponse: START - prompt length=245
D/LlamaCppEngine: generateResponse: generating first token with prompt
W/LlamaCppEngine: generateResponse: reached maxTokens limit (500)
I/LlamaCppEngine: generateResponse: COMPLETE - totalTokens=487
```

### 3. **DownloadService** (`TAG = "DownloadService"`)
Логирует:
- Запуск сервиса загрузки
- URL модели
- Подключение к серверу
- Размер файла
- Путь сохранения
- Создание директорий
- Прогресс загрузки (каждые 500мс)
- Проверку отмены/паузы
- Сохранение в БД
- Ошибки и исключения

**Пример логов:**
```
I/DownloadService: start requested for modelId=TheBloke/Mistral-7B-Instruct-v0.1-GGUF
I/DownloadService: Starting download for: TheBloke/Mistral-7B-Instruct-v0.1-GGUF
I/DownloadService: downloadModel URL: https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.1-GGUF/resolve/main/mistral-7b-instruct-v0.1.Q4_K_M.gguf
I/DownloadService: Connection established, fileSize=4294967296 bytes
V/DownloadService: Progress: 25% (1024MB/4096MB)
D/DownloadService: Download PAUSED, waiting...
W/DownloadService: Download CANCELLED during transfer at 1048576 bytes
I/DownloadService: Saving model to database: mistral-7b-instruct-v0.1.Q4_K_M.gguf
I/DownloadService: downloadModel COMPLETE: TheBloke/Mistral-7B-Instruct-v0.1-GGUF
```

### 4. **DownloadManager** (`TAG = "DownloadManager"`)
Логирует:
- Инициализацию менеджера
- Обновления прогресса
- Завершение загрузок
- Ошибки при загрузке
- Отмену загрузок
- Паузу/возобновление
- Проверки состояния

**Пример логов:**
```
D/DownloadManager: DownloadManager initialized
V/DownloadManager: updateProgress: model_id=42%, paused=false
I/DownloadManager: setCompleted: TheBloke/Mistral-7B
I/DownloadManager: cancelDownload: gemma-3-1b-it
I/DownloadManager: pauseDownload: mistral-7b
D/DownloadManager: isCancelled: model_id=true
```

### 5. **ChatViewModel** (`TAG = "ChatViewModel"`)
Логирует:
- Инициализацию ViewModel
- Загрузку чатов
- Изменения состояния Inference
- Загрузку моделей
- Сохранение параметров модели
- Отправку сообщений
- Загрузку истории сообщений
- Получение токенов от модели
- Сохранение ответов в БД
- Переименование и удаление чатов

**Пример логов:**
```
D/ChatViewModel: ChatViewModel initialized
I/ChatViewModel: loadChat: chat_uuid_123
D/ChatViewModel: InferenceState changed to: READY
I/ChatViewModel: sendMessage called: length=42, modelLoaded=true
D/ChatViewModel: previousMessages count: 5
I/ChatViewModel: Starting inference generation
V/ChatViewModel: Received token: 'The'
I/ChatViewModel: Inference complete, saving response (length=256)
D/ChatViewModel: Saving assistant message to database
I/ChatViewModel: sendMessage COMPLETE
```

## 🔧 Как получить логи

### Способ 1: Через Android Studio

1. Откройте проект в Android Studio
2. Запустите приложение на эмуляторе или устройстве
3. Откройте **Logcat** (View → Tool Windows → Logcat или Alt+6)
4. Выберите нужный фильтр:
   - Все логи: оставьте поле пустым
   - Конкретный компонент (например, `InferenceManager`):
     ```
     InferenceManager
     ```
   - Несколько компонентов одновременно:
     ```
     InferenceManager|LlamaCppEngine|DownloadService
     ```

### Способ 2: Через командную строку

```bash
# Все логи
adb logcat

# Только наши компоненты (фильтр по TAG)
adb logcat "*:S" InferenceManager:V LlamaCppEngine:V DownloadService:V DownloadManager:V ChatViewModel:V

# Логи в файл
adb logcat > logcat.txt

# Логи с временем
adb logcat -v threadtime
```

### Способ 3: Экспорт логов из приложения

```bash
# Сохранить буфер в файл
adb shell "logcat -d > /sdcard/logcat_backup.txt"

# Получить файл на компьютер
adb pull /sdcard/logcat_backup.txt
```

## 📊 Уровни логирования

- **V (Verbose)** - Очень подробные данные (токены, прогресс каждые 500мс)
- **D (Debug)** - Отладочная информация (инициализация, состояния)
- **I (Info)** - Важная информация (начало/конец операций)
- **W (Warning)** - Предупреждения (отмена, лимиты)
- **E (Error)** - Ошибки и исключения

## 🎯 Фильтрование логов

### Только ошибки
```
adb logcat "*:S" "*:E"
```

### Только Info и выше
```
adb logcat "*:S" InferenceManager:I LlamaCppEngine:I DownloadService:I
```

### С временными метками
```
adb logcat -v time
# или
adb logcat -v threadtime
```

## 📝 Типичные сценарии для логирования

### 1. Загрузка модели и ошибка
Посмотрите логи с `InferenceManager` и `LlamaCppEngine`:
- Как загружается модель?
- Какие параметры используются?
- Какая ошибка возникает?

### 2. Загрузка модели с сервера
Посмотрите логи с `DownloadService`:
- Правильный ли URL?
- Как подключается к серверу?
- Какой размер файла?
- На каком проценте зависает?

### 3. Генерация ответа и краш
Посмотрите логи с `LlamaCppEngine`:
- Сколько токенов было сгенерировано?
- Достигли ли максимума (500 токенов)?
- Какие параметры использованы (потоки, GPU)?

### 4. Проблема с чатом
Посмотрите логи с `ChatViewModel`:
- Правильное ли число сообщений в контексте?
- Какой шаблон чата используется?
- Как сохраняется история?

## 💾 Отправка логов для анализа

Когда хотите отправить мне логи для анализа:

1. **Очистьте логи:**
   ```bash
   adb logcat -c
   ```

2. **Воспроизведите проблему** (загрузка, чат, ошибка и т.д.)

3. **Экспортируйте логи:**
   ```bash
   adb logcat -d > logcat_$(date +%Y%m%d_%H%M%S).txt
   ```

4. **Отправьте файл мне** с описанием что вы делали

## 🔍 Парсинг логов для анализа

Можно создать простой скрипт для анализа:

```bash
# Только наши теги в хронологическом порядке
adb logcat -v time -b all | grep -E "InferenceManager|LlamaCppEngine|DownloadService|DownloadManager|ChatViewModel"

# Только ошибки
adb logcat -v time -b all | grep -i "error\|failed\|exception"

# Временные интервалы
adb logcat -v time | grep "12:34"
```

## ✅ Что логируется в каждой операции

| Операция | Компоненты | Уровень | Какой информацией полезен |
|----------|-----------|--------|--------------------------|
| Загрузка модели | InferenceManager, LlamaCppEngine | INFO | Путь, параметры, contextPtr |
| Генерация | InferenceManager, LlamaCppEngine, ChatViewModel | INFO/DEBUG/VERBOSE | Кол-во токенов, контекст |
| Загрузка файла | DownloadService, DownloadManager | INFO/DEBUG/VERBOSE | URL, прогресс, размер |
| Сохранение чата | ChatViewModel | DEBUG | ID сообщений, timestamps |
| Ошибки | Все компоненты | ERROR | Stack trace, контекст |

---

**Теперь все действия приложения записываются в logcat. Отправляйте мне скопированные логи в случае ошибок!**
