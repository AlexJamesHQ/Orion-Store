# Orion Store 1.5.5 — Material You Dynamic Theming & Icon Precision 🎨

Welcome to **Orion Store 1.5.5**! This release brings native Android Material You (Monet) dynamic wallpaper theming and pixel-perfect monochrome launcher icon precision for Android 13+.

---

## ✨ What's New

* **Native Material You Dynamic Theming:** The entire Orion Store app now integrates authentic Android Material You (Monet) dynamic colors. Powered by a dedicated native plugin on Android 12+ (API 31+), the interface extracts your device's wallpaper color tokens to dynamically tint interactive components, active tabs, buttons, toggles, badges, and accents.
* **Pure Black & Contrast Preservation:** Material You dynamically styles accents without washing out dark backgrounds to grey. Your chosen dark and pure OLED black (`#000000`) surfaces remain pitch black with high-contrast vibrant accents.
* **Settings Toggle:** Easily enable or disable Material You with a dedicated switch in **Settings → Visuals and Theme**.
* **Exact-Scale Monochrome Adaptive Icon:** Re-engineered the Android 13+ themed monochrome adaptive launcher icon to exact 1:1 scale and geometry matching the original colored app icon, eliminating oversized shape discrepancies on Android launchers.
* **Streamlined Header Theme Switcher:** The top header theme button cleanly cycles the 3 core base themes (Light ☀️, Dusk ⛅, and Night 🌙).

---

## 🚀 Technical Highlights

* **Dedicated `MaterialYouPlugin`:** Native Android plugin safely queries Monet color tokens (`system_accent1_...`, `system_accent2_...`, `system_accent3_...`, `system_neutral...`) across various OEM ROMs (Pixel, Samsung One UI, Xiaomi HyperOS, ColorOS) with seamless fallback support.
* **Instant Dynamic Syncing:** Automatically adapts dynamic accent luminance when switching between Light mode and Dark/Night mode.
