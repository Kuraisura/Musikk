# 🎵 Musikk

**AI Stem Studio — Music Isolation & Practice** · `Mobile`

AI-powered mobile app for isolating music stems from songs, with multi-track mixer, effects, and practice tools for musicians.

## Why This Project

Musicians who want to learn a song the way professionals do need the individual stems — vocals isolated, drums isolated, guitar isolated — but official multitrack files rarely exist for the songs people actually cover, and desktop stem-splitting tools are expensive, heavyweight, and tethered to a studio machine. A student guitarist rehearsing in a bedroom, a singer transposing a track to their range, or a band learning a setlist on phones between classes needs isolation and practice controls where their music already lives: on their Android device.

Musikk was built by Mark Crysler Baddo as a solo mobile engineering project that treats AI source separation as a practice instrument, not a novelty. Songs upload from the phone, a Demucs/BS-RoFormer-class separation pipeline splits them into up to ten stems via cloud inference, and a DAW-style mixer with per-stem faders, effects, and loop tools turns the result into a rehearsal rig — wrapped in a freemium model (20 free AI-processing minutes) that keeps casual users in and power users on paid passes.

## How It Works

Paolo is a 21-year-old self-taught guitarist in a college cover band, three weeks out from a gig. He installs Musikk, signs in with Google through Firebase, and lands on his empty Repertoire. He uploads a WAV of the song his band is struggling with and picks the "Band · 5-stem" preset from the process dialog — vocals, drums, bass, guitar, and other. The processing pipeline marks the song PROCESSING in Firestore, uploads the original to Cloudflare R2, posts the bytes to the Demucs inference endpoint, then uploads each returned stem back to R2 and records the presigned URLs and stem keys on the song document. Minutes later the card reads "5 stems ready."

Paolo taps "Load into Stems." The mixer prepares five synced players with per-channel waveforms. He solos the guitar stem to hear the part clearly, mutes vocals to play lead himself, and rides the faders until the balance suits his practice. A fast passage in the chorus is still too quick, so he opens the effects panel and drops playback speed without changing pitch; later, when his vocalist rehearses, he shifts pitch to fit her range instead of the original key. On the Practice tab he sets A/B loop points around the solo and lets it cycle at the song's BPM until his fingers learn it. Twenty free processing minutes cover this track; when the band's setlist grows, the Monthly Pass or Pro Musician tier unlocks more separation time.

## Modules

### Onboarding & Google Sign-In

The entry flow — splash screen while the app initializes, an onboarding screen that creates or resumes a Firebase account via Google, and a notification-permission step so processing-completion alerts can be delivered. The tagline "Unleash Your Music. Isolate. Practice. Master." states the value proposition before the user touches a control.

**Scenario:** Paolo opens the app for the first time, signs in with his Google account in one tap, grants notifications, and reaches the Stems tab with his identity synced across reinstalls.

### Repertoire Library

The user's song cabinet: uploads come through the system file picker with duration and format metadata read from the file, a Firestore-backed list tracks each song's processing status ("4 stems ready"), and search plus format filter chips keep large libraries navigable. A process dialog lets the user choose a stem preset before separation starts, and re-uploads of the same track cancel any in-flight job for that song.

**Scenario:** Paolo uploads three new covers before rehearsal, filters his library to "WAV" to find them instantly, and taps the song that finished processing — its card already shows the stem count and a direct "Load into Stems" action.

### Stem Separation Pipeline

The AI core: a preset-driven job that marks a song PROCESSING in Firestore, downloads the original from R2, posts it to the Demucs-family inference endpoint (HTDemucs and BS-RoFormer variants depending on preset), parses the returned stems, uploads each one to `users/<uid>/<songId>/` in R2, and persists stem URLs, keys, preset id, and finished status — reporting staged progress along the way (upload, inference, stem upload). The stem catalogue models up to ten keys (vocals, drums, bass, guitar, piano, strings, synth, percussion, backing vocals, other) with presets ranging from "Vocals only" to "Studio · 10-stem," and bundled ONNX model slots (voc_inst, htdemucs, bs_roformer) support local-engine separation.

**Scenario:** Paolo selects "Band · 5-stem" and confirms. The app walks the song through upload, inference, and stem upload with visible progress; if the inference endpoint hiccups, the job re-queues so he can retry instead of losing the upload.

### Stems Mixer

A DAW-style multi-track player: `StemAudioManager` prepares one synchronized player per stem with shared transport and progress, each TrackStrip renders a waveform plus fader, mute, and solo controls, and a full-mix fallback keeps playback working when only the original file is available. Solo isolates one instrument; mute drops one out; the red Vocals channel is the focal point for singers practicing without the lead vocal.

**Scenario:** During rehearsal Paolo solos the drums to count the groove, then unmutes everything and pulls the vocals fader to zero so his band's singer can run her part against the instrumental bed — all from one screen on his phone.

### Effects & Practice Tools

Real-time playback manipulation for learning: the effects manager applies playback-speed change (slow down fast passages without editing the file), pitch shifting (transpose to a vocalist's range without changing tempo), and a deep EQ-based filter, alongside the mixer's mute/solo. The Practice tab adds A/B loop points, a BPM readout from the song's metadata, and transport controls built for cycling difficult sections.

**Scenario:** The band's solo is sixteenth-note runs at full tempo. Paolo sets the loop around the solo, drops speed to 75%, and cycles it with the metronomic BPM display until the passage is clean — then restores full speed and plays it up with the rest of the track.

### Cloud Storage (R2)

Private object storage on Cloudflare R2 with SigV4 presigned GET and PUT URLs generated on-device: originals and stems never sit at public URLs, and time-limited signatures (seven-day reads) keep the library accessible only to its owner.

**Scenario:** Paolo's separated stems live under his user folder in R2; the mixer fetches them through presigned links that expire on their own, so a leaked URL goes stale instead of exposing his library.

### Profile & Usage Credits

Account management backed by Firebase Auth and Firestore: the profile screen shows remaining free AI-processing credits (20 on the free tier with monthly reset), the audio-processing quality selector (e.g., Ultra BS-RoFormer) trading speed against fidelity, privacy preferences, subscription tier (Monthly Pass $4.99, Pro Musician $11.99), and logout.

**Scenario:** After separating four songs Paolo checks his profile, sees his remaining free minutes and reset date, and decides whether to ride out the free tier or upgrade before the next batch of covers — the processing limit making the value of a pass concrete.

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

## About

A mobile app that uses AI source separation to isolate individual instrument stems from any song. Musicians can mute/solo tracks, adjust volume per stem, apply effects, and practice along with their instrument. Freemium model with monthly processing time limits.

---

Built by [Mark Crysler Baddo](https://github.com/Kuraisura) · [Portfolio](https://kuraisler.xyz)
