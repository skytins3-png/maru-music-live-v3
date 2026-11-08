# MARU PYTHON SCORE — one-app sheet music

**Python/Kivy + NumPy + Android platform audio decoders**, packaged by Buildozer.
This is a **standalone Android APK**, not a Colab notebook, browser page, or earlier MARU RADIO installer.

1. Install the APK (not the ZIP). No account or internet after installation.
2. Tap **음원 선택 / 악보 만들기** and choose an MP3, M4A, or uncompressed WAV file.
3. After analysis, see a preview of the **estimated single lead melody**.
4. Tap **악보 PDF 저장** and choose the save location; optional MusicXML and MIDI.

Designed for **one-phone usage**. The app asks only for user-selected files using Android's
scoped-storage document chooser; it does **not** scan personal Suno collections, connect
to BIGO, access MARU RADIO's 105-song database, download memorial songs, or need a server.

**Limits:** Max 200 MB, 3 minutes per job, default tempo 80 BPM and 4/4, no automatic
instrument/vocal separation. Dense Suno arrangements will have wrong notes; the PDF
is a draft melody reference, not a fully accurate multi-staff score. Actual Galaxy
installation and MP3 decoder behavior must be tested, even after CI APK build success.

Developers: from python_score_apk run `buildozer android debug`. The first build downloads
Android SDK/NDK and python-for-android dependencies. Use a **stable release signing key**
before distributing updates. Debug APK is a developer test package.
