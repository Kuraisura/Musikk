# Musikk

An Android music-practice app for separating audio, balancing stems, and rehearsing difficult passages.

**Android** · **In Development** · 2026

[Portfolio](https://kuraisler.xyz)

## Purpose

Musikk helps musicians practice with parts of a recording instead of only the full mix. It combines a song library, separation workflow, multi-track playback, and practice controls so users can isolate a part, reduce competing instruments, or repeat a passage.

## Intended users

Singers, instrumentalists, and hobby musicians practicing with recordings they are entitled to use.

## Main capabilities

- Upload songs and organize a repertoire with processing status.
- Request stem separation through a configured processing backend.
- Adjust stem volume, mute, and solo controls in a shared mixer.
- Use playback speed, pitch, and A/B loop controls for practice.

## How it was built

### Android application

Kotlin and Jetpack Compose provide the library, mixer, practice, and profile screens.

### Audio processing

A Python FastAPI backend runs Demucs separation jobs. Android services manage processing requests, while Media3 ExoPlayer supports synchronized stem playback.

### Cloud library

Firebase Authentication and Firestore manage accounts and song metadata. Cloudflare R2 holds source audio and generated files, accessed through the app storage service.

## Technology stack

| Responsibility | Technologies |
| --- | --- |
| Android interface | Kotlin, Jetpack Compose, Navigation Compose |
| Playback & networking | Media3 ExoPlayer, OkHttp, Retrofit |
| Separation backend | Python, FastAPI, Demucs |
| Accounts & storage | Firebase Auth, Cloud Firestore, Cloudflare R2 |

## Project scope and current state

A personal project in development. Separation requires a working backend; available independent stems depend on the model. Some extended preset labels map to existing outputs, demo fallback can reuse source audio, and subscription screens do not establish a completed payment integration.

## Source guide

- [`app/src/main/java/com/kurai/musikk/ui/`](app/src/main/java/com/kurai/musikk/ui/)
- [`app/src/main/java/com/kurai/musikk/data/`](app/src/main/java/com/kurai/musikk/data/)
- [`main.py`](main.py)

## Development setup

Open the Android project in Android Studio. Configure Firebase and the processing and storage endpoints expected by the app. main.py is the FastAPI backend entry point; local processing requires Python, an ASGI server, upload support, Demucs, FFmpeg, and compatible model/runtime dependencies. Validate the backend before testing separation. The repository does not provide a single verified command that provisions every service.

## Screenshots

<table>
<tr>
<td align="center"><img src="screenshots/1.jpg" alt="Brand introduction" width="260"><br>Brand introduction</td>
<td align="center"><img src="screenshots/8.png" alt="Onboarding" width="260"><br>Onboarding</td>
</tr>
</table>

---

Project by [Mark Crysler Baddo](https://github.com/Kuraisura) · [Portfolio](https://kuraisler.xyz)
