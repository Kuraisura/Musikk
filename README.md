# 🎵 Musikk

**AI Stem Studio — Music Isolation & Practice** · `Mobile`

AI-powered mobile app for isolating music stems from songs, with multi-track mixer, effects, and practice tools for musicians.

## Screenshots

### Brand introduction — the splash screen loads while the app initializes, displaying the equalizer logo and "AI STEM STUDIO" badge to establish what the app does before the user interacts.

![Brand introduction — the splash screen loads while the app initializes, displaying the equalizer logo and "AI STEM STUDIO" badge to establish what the app does before the user interacts.](screenshots/1.jpg)

### Onboarding — new users create an account or sign in via Google. The tagline "Unleash Your Music. Isolate. Practice. Master." communicates the full value proposition in four words.

![Onboarding — new users create an account or sign in via Google. The tagline "Unleash Your Music. Isolate. Practice. Master." communicates the full value proposition in four words.](screenshots/8.png)

### Song library — the repertoire is where users manage their uploaded songs. Each card shows processing status ("4 stems ready") and a direct "Load into Stems" button to jump into the mixer.

![Song library — the repertoire is where users manage their uploaded songs. Each card shows processing status ("4 stems ready") and a direct "Load into Stems" button to jump into the mixer.](screenshots/2.png)

### Mixer initialization — the stems player loads audio and displays multi-track waveforms with individual mixer channels for each separated stem (drums, bass, vocals, guitar). This is where the AI separation results become usable.

![Mixer initialization — the stems player loads audio and displays multi-track waveforms with individual mixer channels for each separated stem (drums, bass, vocals, guitar). This is where the AI separation results become usable.](screenshots/3.png)

### Active mixing — users adjust each stem's volume independently using the slider, or SOLO/MUTE buttons to isolate a single instrument. The red Vocals card is the focal point for singers practicing without the original vocal track.

![Active mixing — users adjust each stem's volume independently using the slider, or SOLO/MUTE buttons to isolate a single instrument. The red Vocals card is the focal point for singers practicing without the original vocal track.](screenshots/4.png)

### Audio effects panel — Speed control lets musicians slow down fast passages to learn them; Pitch shifting lets singers transpose a song to their vocal range without changing tempo. Essential practice tools.

![Audio effects panel — Speed control lets musicians slow down fast passages to learn them; Pitch shifting lets singers transpose a song to their vocal range without changing tempo. Essential practice tools.](screenshots/5.png)

### Subscription management — displays remaining AI processing time (20 mins free tier) and presents paid plans. Monthly Pass ($4.99) for casual users, Pro Musician ($11.99) for heavy users. The processing limit drives conversions.

![Subscription management — displays remaining AI processing time (20 mins free tier) and presents paid plans. Monthly Pass ($4.99) for casual users, Pro Musician ($11.99) for heavy users. The processing limit drives conversions.](screenshots/6.png)

### Account settings — manages profile, Firebase cloud storage, privacy preferences, and logout. The Audio Processing Quality selector trades speed vs. fidelity depending on the user's needs.

![Account settings — manages profile, Firebase cloud storage, privacy preferences, and logout. The Audio Processing Quality selector trades speed vs. fidelity depending on the user's needs.](screenshots/7.png)

## Features

- Upload songs and split into isolated stems (drums, bass, vocals, etc.)
- Multi-track waveform display with per-stem volume/mute/solo
- Audio effects: speed, pitch, and deep filter
- Repertoire library with format filters
- AI processing time tracking with subscription tiers
- Firebase authentication and cloud storage

## Tech Stack

- Flutter
- Dart
- Firebase
- Spleeter/AI Audio
- Google Sign-In

## About

A mobile app that uses AI source separation to isolate individual instrument stems from any song. Musicians can mute/solo tracks, adjust volume per stem, apply effects, and practice along with their instrument. Freemium model with monthly processing time limits.

---

Built by [Mark Crysler Baddo](https://github.com/Kuraisura) · [Portfolio](https://kuraisler.xyz)
