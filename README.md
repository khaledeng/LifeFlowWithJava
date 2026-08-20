# 🌊 LifeFlow — Mindful Life & Activity Tracker for Android

<div align="center">

![LifeFlow Banner](docs/assets/hero_banner.jpg)

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Room Database](https://img.shields.io/badge/Storage-Room_SQLite-4285F4?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Material 3](https://img.shields.io/badge/UI-Material_Design_3-7C4DFF?style=for-the-badge&logo=material-design&logoColor=white)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<p align="center">
  <b>A modern, privacy-focused, and intelligent time-tracking application built with Native Android (Java + Jetpack). Designed to help you track habits, manage daily routines, analyze personal productivity with dynamic multi-line analytics, and achieve balanced living.</b>
</p>

[Key Features](#-key-features) • [Screenshots & Visuals](#-screenshots--features-overview) • [Architecture](#-architecture--tech-stack) • [Installation](#-installation--setup) • [Author & License](#-author--contributions)

</div>

---

## 📱 About The Project

**LifeFlow** is a comprehensive personal activity and productivity tracker developed for Android. It eliminates chaotic time management by providing real-time tracking, background session monitoring via persistent foreground services, intelligent habit goals (Increase / Routine / Limit), and interactive multi-line charts with fully dynamic time scaling.

Whether you're building a study streak, balancing work hours, or curbing screen time, LifeFlow offers an intuitive, offline-first experience with zero ads and complete data ownership.

---

## ✨ Key Features

### ⏱️ 1. Real-Time Tracking & Persistent Background Service
- **One-Tap Instant Tracking:** Start, switch, or pause any activity with immediate visual feedback.
- **Active Session Stopwatch:** Glowing, live-updating timer card showing elapsed time and progress towards daily goals.
- **Foreground Notification Service:** Track sessions even when the app is closed or the screen is locked, with quick action buttons directly in the notification shade.

---

### 📊 2. Dynamic Multi-Line Analytics & Deep Insights
- **Adaptive Canvas Spline Chart (`MultiLineStatsChartView`):** Custom high-performance 2D canvas drawing with smooth Bézier curve rendering.
- **100% Dynamic Scaling (X & Y Axes):**
  - **Dynamic Y-Axis:** Automatically scales from small 5–15 minute sessions up to 24-hour overviews so that short sessions remain prominent and easily visible.
  - **Dynamic X-Axis:** Adapts to the current time of day for high-resolution morning/afternoon tracking.
- **Period Switcher:** Analyze data across **Day**, **Yesterday**, **Week**, **Month**, and **Year** with smooth navigation arrows.
- **Interactive Legend Chips:** Tap any category chip to isolate and highlight its trend curve on the graph.
- **Interactive Touch Tooltips:** Touch any point on the chart to inspect precise durations and timestamp breakdowns.

---

### 🎯 3. Smart Habit Goals & Milestone Engine
- **Three Strategic Goal Types:**
  - 📈 **Increase (Habit Building):** Stay motivated with milestone celebrations (25%, 50%, 75%, 100%).
  - ⚖️ **Normal (Routine Tracking):** Neutral monitoring for standard daily tasks.
  - 📉 **Decrease (Time Limit / Budget):** Set caps for distractions and get warning alerts when approaching limits.
- **24-Hour Safety Validation:** Intelligent constraint engine preventing total daily goals from exceeding 24 hours.

---

### 🎨 4. Custom Activity Management
- **Rich Icon & Emoji Picker:** Choose from dozens of categorized vector icons and expressive emojis.
- **Vibrant Color Palette:** Assign custom hex colors for distinct visual recognition across charts and cards.
- **Tier Management:** Modular architecture supporting both standard and unlocked Pro tiers.

---

### ✏️ 5. Manual Time Adjustments
- **Quick Adjust:** Instant +/- 15 min and 30 min quick-adjustment buttons.
- **Direct Log Editor:** Edit logged times for any historical period with strict period boundary checks.

---

### 🔒 6. Privacy-First Storage & JSON Backup / Restore
- **100% Offline & Private:** Powered by a local SQLite database through Android Room. No accounts required and no cloud tracking.
- **JSON Export & Import:** Export full database backups as `.json` files.
- **Dual Restore Modes:** Choose between **Merge with Existing Data** or **Complete Replacement**.

---

### 🌐 7. Full Bilingual & RTL Localization
- Complete native support for **Arabic (العربية)** and **English**.
- Automatic RTL layout mirroring, Arabic numeral handling, and localized time units (`س / د` and `h / m`).

---

## 📸 Screenshots & Features Overview

<div align="center">

### 🌟 1. Live Dashboard & Real-Time Tracking
*Intuitive dashboard with active stopwatch, categorized activities, and quick start controls.*
<br/>
<img src="docs/assets/feature_dashboard.jpg" alt="Dashboard Screen" width="85%"/>

<br/><br/>

### 📈 2. Interactive Multi-Line Trend Analytics
*High-precision dynamic spline chart with isolated line highlighting and period filters.*
<br/>
<img src="docs/assets/feature_analytics.jpg" alt="Analytics Chart Screen" width="85%"/>

<br/><br/>

### 🎯 3. Smart Habit Goals & Customization
*Tailored goal strategies (Increase, Normal, Decrease), custom color palettes, and icon pickers.*
<br/>
<img src="docs/assets/feature_goals.jpg" alt="Goal Settings Screen" width="85%"/>

</div>

---

## 🏗️ Architecture & Tech Stack

LifeFlow is built following modern Android architectural best practices (**MVVM + Clean Repository Pattern**) for maintainability, testability, and responsiveness.

```
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                     │
│  Activities / Fragments (ViewBinding) + Custom UI Canvas    │
│  (MultiLineStatsChartView, Bottom Sheets, Interactive Chips)│
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       ViewModel Layer                       │
│     State Observers & UI Logic Orchestration                │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      Repository Layer                       │
│    TrackingRepository (Thread-safe concurrency & Math Engine)│
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
               ▼                               ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│      Local Persistence       │ │     Background Service     │
│   Room Database (SQLite)     │ │ TrackingForegroundService  │
│  ActivityDao & SessionDao    │ │ (Live Notification Action) │
└──────────────────────────────┘ └────────────────────────────┘
```

### 🛠️ Core Technologies & Libraries

| Technology | Purpose |
|---|---|
| **Language** | Java (Android SDK 34 / Java 17) |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern |
| **Local Storage** | Android Jetpack Room (SQLite ORM) |
| **UI Components** | Material Design 3 (M3), ViewBinding, Custom Canvas Views |
| **Background Processing** | Foreground Service, Android Notifications API, Coroutines/Executors |
| **Data Interchange** | GSON for JSON serialization, backup, and restore |
| **Localization** | Multi-locale string resources (Arabic RTL & English LTR) |

---

## 📂 Project Structure

```
LifeFlow/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/
│   │   │   ├── MainActivity.java                # Main navigation container
│   │   │   ├── data/
│   │   │   │   ├── AppDatabase.java             # Room database configuration
│   │   │   │   ├── ActivityDao.java             # Activity CRUD operations
│   │   │   │   ├── SessionDao.java              # Time session queries & aggregations
│   │   │   │   ├── TrackingActivity.java        # Activity entity model
│   │   │   │   ├── TrackingSession.java         # Session entity model
│   │   │   │   └── TrackingRepository.java      # Business logic, aggregations & trends
│   │   │   ├── service/
│   │   │   │   └── TrackingForegroundService.java # Foreground timer notification
│   │   │   ├── ui/
│   │   │   │   ├── dashboard/                   # Live tracking dashboard
│   │   │   │   ├── statistics/                  # Custom multi-line chart & breakdown
│   │   │   │   │   ├── MultiLineStatsChartView.java # Custom canvas drawing engine
│   │   │   │   │   └── StatsFragment.java       # Statistics controller
│   │   │   │   ├── activities/                  # Activity management & goal editor
│   │   │   │   └── settings/                    # Backups, tiers, and language switcher
│   │   │   └── util/
│   │   │       ├── ColorHelper.java             # Color palettes & contrast calculations
│   │   │       ├── IconHelper.java              # Icon/emoji mappings
│   │   │       └── LocaleHelper.java            # Dynamic language switching
│   │   ├── res/
│   │   │   ├── layout/                          # XML Material 3 layouts
│   │   │   ├── values/                          # English resources & themes
│   │   │   └── values-ar/                       # Arabic RTL resources
│   │   └── AndroidManifest.xml
├── docs/
│   └── assets/                                  # Showcase images & banners
├── build.gradle.kts
└── README.md
```

---

## 🚀 Installation & Setup

### Prerequisites
- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android SDK** API 26 (Android 8.0) minimum, API 34 target
- **JDK** 17

### Steps to Run
1. **Clone the repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/LifeFlow-Android.git
   cd LifeFlow-Android
   ```
2. **Open in Android Studio:**
   - Launch Android Studio.
   - Select **Open an existing project** and select the cloned root folder.
3. **Sync Gradle:**
   - Allow Android Studio to sync dependencies via Gradle.
4. **Run the App:**
   - Connect your Android device or start an emulator.
   - Press **Run ▶ (Shift + F10)**.

---

## 👨‍💻 Author & Contributions

Developed with ❤️ by **Khaled**.

If you like this project, please give it a ⭐️ on GitHub!

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
