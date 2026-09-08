# 🌊 LifeFlow — Mindful Life & Activity Tracker for Android

<div align="center">

![LifeFlow Banner](docs/assets/hero_banner.jpg)

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Room Database](https://img.shields.io/badge/Storage-Room_SQLite-4285F4?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Material 3](https://img.shields.io/badge/UI-Material_Design_3-7C4DFF?style=for-the-badge&logo=material-design&logoColor=white)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<p align="center">
  <b>A modern, privacy-focused, and intelligent automated time-tracking & habit management system for Android. Designed to help you track habits, automate routine switching, enforce app limits with an instant system blocker, analyze personal productivity with dynamic multi-line analytics, and achieve balanced living.</b>
</p>

[Key Features](#-key-features) • [Smart Tracking Engine](#-smart-automated-tracking--app-blocking-engine) • [Screenshots & Visuals](#-screenshots--features-overview) • [Architecture](#-architecture--tech-stack) • [Installation](#-installation--setup) • [Author & License](#-author--contributions)

</div>

---

## 📱 About The Project

**LifeFlow** is a comprehensive, offline-first personal activity and productivity tracker for Android. It eliminates manual tracking fatigue by introducing an **Intelligent Automated Tracking Engine** that seamlessly switches timers based on active foreground apps, hourly routine schedules, and default fallback activities.

Whether you're building study habits, curbing distracting screen time with hardware-level overlay blocking, or inspecting your long-term focus trends through high-precision Bézier spline graphs, LifeFlow delivers an intuitive, private, and customizable experience.

---

## ✨ Key Features

### 🧠 1. Smart Automated Tracking & App Lock Engine
- **Three-Tier Priority Architecture:**
  1. 🥇 **Foreground App Binding (Highest Priority):** Automatically switches tracking to the bound activity when launching specific apps (e.g., launching TikTok immediately switches to *Social Media*).
  2. 🥈 **Hourly Routine Scheduling:** Automatically switches to scheduled routines (e.g., *Sleep* from 10:00 PM to 7:00 AM, or *Deep Work* during work hours) when no prioritized app is running.
  3. 🥉 **Default Fallback Activity:** Automatically logs leftover unassigned time to a default catch-all bucket (e.g., *Make Time* / *Daily Routine*).
- **Instant System Overlay App Blocker (`AppBlockerManager`):**
  - Displays a high-priority system overlay window (`TYPE_APPLICATION_OVERLAY`) over restricted applications when daily time budgets are exhausted or strict lock is enabled.
  - Automatically redirects users back to the home launcher to prevent distraction and doom-scrolling.
- **Interactive Permission Status Banners:** Proactively alerts users on the dashboard to easily grant *Usage Access*, *Display Over Other Apps*, and *Notification* permissions with one tap.

---

### ⏱️ 2. Real-Time Tracking & Persistent Background Service
- **One-Tap Instant Tracking:** Start, switch, or pause any activity with immediate visual feedback.
- **Active Session Stopwatch:** Live glowing timer card displaying elapsed time, session progress, and target goals.
- **Persistent Foreground Notification Service (`TrackingService`):** Maintains accurate tracking even when the app is closed, battery optimized, or the screen is locked, with quick action controls in the notification tray.

---

### 📊 3. Dynamic Multi-Line Analytics & Deep Insights
- **Adaptive Canvas Spline Chart (`MultiLineStatsChartView`):** Custom high-performance 2D canvas drawing with smooth Bézier curve rendering.
- **100% Dynamic Scaling (X & Y Axes):**
  - **Dynamic Y-Axis:** Automatically scales from small 5–15 minute sessions up to 24-hour overviews so short sessions remain clear and scannable.
  - **Dynamic X-Axis:** Adapts to the current time of day for high-resolution morning/afternoon tracking.
- **Flexible Period Filtering:** Inspect data across **Day**, **Yesterday**, **Week**, **Month**, and **Year** with smooth navigation controls.
- **Interactive Legend Chips:** Tap any category chip to isolate and highlight its trend curve on the graph.
- **Touch-Sensitive Tooltips:** Touch any coordinate on the chart to inspect precise durations and timestamp breakdowns.

---

### 🎯 4. Smart Habit Goals & Milestone Engine
- **Three Strategic Goal Types:**
  - 📈 **Increase (Habit Building):** Stay motivated with milestone celebrations (25%, 50%, 75%, 100%).
  - ⚖️ **Normal (Routine Tracking):** Neutral monitoring for standard daily tasks.
  - 📉 **Decrease (Time Limit / Budget):** Set caps for distractions and get warning alerts or enforce instant app locks when limits are exceeded.
- **24-Hour Safety Validation:** Intelligent constraint engine preventing total daily goals from exceeding 24 hours.

---

### 🎨 5. Custom Activity Management
- **Rich Icon & Emoji Picker:** Choose from dozens of categorized vector icons and expressive emojis.
- **Vibrant Color Palette:** Assign custom hex colors for distinct visual recognition across charts and cards.
- **App & Group Binding:** Bind single apps or entire app groups directly to any activity.

---

### ✏️ 6. Manual Time Adjustments & Slices
- **Quick Adjust:** Instant +/- 15 min and 30 min quick-adjustment buttons.
- **Direct Log Editor:** Edit logged times for any historical period with strict period boundary checks.

---

### 🔒 7. Privacy-First Storage & JSON Backup / Restore
- **100% Offline & Private:** Powered by a local SQLite database through Android Room. No accounts required and no cloud tracking.
- **JSON Export & Import:** Export full database backups as `.json` files.
- **Dual Restore Modes:** Choose between **Merge with Existing Data** or **Complete Replacement**.

---

### 🌐 8. Full Bilingual & RTL Localization
- Complete native support for **Arabic (العربية)** and **English**.
- Automatic RTL layout mirroring, Arabic numeral handling, and localized time units (`س / د` and `h / m`).

---

## 📸 Screenshots & Features Overview

<div align="center">

### 🌟 1. Live Dashboard & Real-Time Tracking
*Intuitive dashboard with active stopwatch, categorized activities, permission banners, and quick start controls.*
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
│   Room Database (SQLite)     │ │      TrackingService       │
│  ActivityDao & SessionDao    │ │  & SmartTrackingManager    │
└──────────────────────────────┘ └────────────────────────────┘
```

### 🛠️ Core Technologies & Libraries

| Technology | Purpose |
|---|---|
| **Language** | Java (Android SDK 34 / Java 17) |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern |
| **Local Storage** | Android Jetpack Room (SQLite ORM) |
| **UI Components** | Material Design 3 (M3), ViewBinding, Custom Canvas Views |
| **Background Processing** | Foreground Service, Android Notifications API, WindowManager System Overlay |
| **Data Interchange** | GSON for JSON serialization, backup, and restore |
| **Localization** | Multi-locale string resources (Arabic RTL & English LTR) |

---

## 📂 Project Structure

```
LifeFlow/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/
│   │   │   ├── MainActivity.java                # Main navigation container & tab coordinator
│   │   │   ├── data/
│   │   │   │   ├── AppDatabase.java             # Room database configuration
│   │   │   │   ├── dao/                         # ActivityDao, SessionDao, DailyProgressDao
│   │   │   │   ├── entity/                      # Activity, ActivitySession, DailyProgress entities
│   │   │   │   └── TrackingRepository.java      # Business logic, aggregations & trends
│   │   │   ├── service/
│   │   │   │   └── TrackingService.java         # Foreground timer & continuous polling service
│   │   │   ├── ui/
│   │   │   │   ├── dashboard/                   # Live tracking dashboard & active timer
│   │   │   │   ├── blocker/                     # AppBlockerManager & AppBlockerActivity (Overlay Blocker)
│   │   │   │   ├── statistics/                  # Custom multi-line chart & breakdown
│   │   │   │   ├── progress/                    # Week/Month progress matrices & streaks
│   │   │   │   ├── activities/                  # Activity management & goal editor
│   │   │   │   └── settings/                    # Smart tracking, backups, and language switcher
│   │   │   └── util/
│   │   │       ├── SmartTrackingManager.java    # Automated priority tracking & permission resolver
│   │   │       ├── IconHelper.java              # Icon & emoji mappings
│   │   │       ├── LanguageManager.java         # Dynamic language & locale switching
│   │   │       └── SubscriptionManager.java     # Feature tier manager
│   │   ├── res/
│   │   │   ├── layout/                          # XML Material 3 layouts
│   │   │   ├── values/                          # English resources & themes
│   │   │   └── values-ar/                       # Arabic RTL resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── docs/
│   └── assets/                                  # Showcase images & banners
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
   - Select **Open an existing project** and choose the project root folder.
3. **Sync Gradle:**
   - Allow Android Studio to sync dependencies via Gradle.
4. **Run the App:**
   - Connect your Android device or start an emulator.
   - Press **Run ▶ (Shift + F10)**.

---

## 👨‍💻 Author & Contributions

Developed with ❤️ by **Khaled**.

If you find this project helpful, please give it a ⭐️ on GitHub!

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
