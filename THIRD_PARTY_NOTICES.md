# 가져온 자료의 출처와 라이선스

이 저장소와 앱에는 다른 곳에서 만들어 공개한 모델, 아이콘, 라이브러리가 들어 있다. 이 문서는 무엇을 어디서 가져왔고 어떤 조건으로 쓰는지 적는다.

- 이 문서는 가져온 자료의 출처를 적은 것이다. 이 저장소에 있는 우리 코드의 라이선스를 정하는 문서가 아니다.
- Apache License 2.0 원문: [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt). https://www.apache.org/licenses/LICENSE-2.0.txt 에서 2026-11-08에 받은 파일 그대로다(md5 3b83ef96387f14655fc854ddc3c6bd57).
- 아래 원본 저장소(tensorflow/models, google-ai-edge/mediapipe, google/material-design-icons, gradle/gradle)에는 NOTICE 파일이 없다(2026-11-08 확인). 그래서 따로 옮겨 적을 NOTICE 내용은 없다.

## 1. 이 저장소에 들어 있는 것

| 파일 | 무엇 | 원본 | 라이선스 | 바꾼 것 |
|---|---|---|---|---|
| `app/src/main/assets/yamnet.tflite` | YAMNet 소리 분류 모델. 아기 울음과 개 소리를 찾는 데 쓴다 | Google. 모델 코드는 [tensorflow/models](https://github.com/tensorflow/models)의 `research/audioset/yamnet`이다. 파일은 MediaPipe 배포본(float32)을 받았다: https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite | Apache 2.0. tensorflow/models의 LICENSE는 "Copyright 2022 Google LLC. All rights reserved."로 시작하고, `/research` 폴더의 파일이 Apache License 2.0이라고 적는다 | 없음 |
| `app/src/main/assets/face_landmarker.task` | MediaPipe Face Landmarker 모델 묶음(BlazeFace short-range, FaceMesh-V2, Blendshape V2). 얼굴, 눈 감김, 고개 돌림을 본다 | Google MediaPipe. 파일: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task | Apache 2.0. 세 모델 카드 모두 "LICENSED UNDER Apache License, Version 2.0"이라고 적는다 | 없음 |
| 아래 표의 아이콘 파일 15개(`app/src/main/res/drawable/`) | Material Icons. 앱 로고, 앱 아이콘, 상태 아이콘, 버튼 아이콘 | Google, [google/material-design-icons](https://github.com/google/material-design-icons) | Apache 2.0 | 도형(pathData)은 원본 그대로다. `tint`를 빼고 색을 직접 정했다. 앱 아이콘 앞면(`ic_launcher_foreground.xml`)은 108dp 칸 가운데에 맞춰 2.6배 키웠다. 파일마다 맨 위 주석에 원본 이름과 바꾼 것을 적었다 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | Gradle Wrapper. 빌드 도구를 받아 실행한다 | [Gradle](https://github.com/gradle/gradle) | Apache 2.0. 파일 머리의 저작권·라이선스 표시를 그대로 두었다 | 없음 |

**이 앱을 위해 직접 그린 그림.** `app/src/main/res/drawable/`의 `art_*.xml`(계절 그림과 작은 차)과 `ic_set_*.xml`(설정과 주행 중 수면의 그림 아이콘)은 외부 자료를 쓰지 않고 직접 그렸다.

**아이콘 파일과 원본.** 원본 경로는 google/material-design-icons의 `android/` 폴더 아래다.

| 이 저장소의 파일 | 원본 파일 |
|---|---|
| `ic_logo.xml`, `ic_launcher_foreground.xml` | `places/child_care/materialiconsoutlined/black/res/drawable/outline_child_care_24.xml` |
| `ic_mic.xml` | `av/mic/materialicons/black/res/drawable/baseline_mic_24.xml` |
| `ic_arrow_back.xml` | `navigation/arrow_back/materialiconsoutlined/black/res/drawable/outline_arrow_back_24.xml` |
| `ic_arrow_forward.xml` | `navigation/arrow_forward/materialiconsoutlined/black/res/drawable/outline_arrow_forward_24.xml` |
| `ic_replay.xml` | `av/replay/materialiconsoutlined/black/res/drawable/outline_replay_24.xml` |
| `ic_settings.xml` | `action/settings/materialicons/black/res/drawable/baseline_settings_24.xml` |
| `ic_near_me.xml` | `maps/near_me/materialicons/black/res/drawable/baseline_near_me_24.xml` |
| `ic_state_search.xml` | `action/search/materialiconsoutlined/black/res/drawable/outline_search_24.xml` |
| `ic_state_sentiment_satisfied.xml` | `social/sentiment_satisfied/materialiconsoutlined/black/res/drawable/outline_sentiment_satisfied_24.xml` |
| `ic_state_visibility.xml` | `action/visibility/materialiconsoutlined/black/res/drawable/outline_visibility_24.xml` |
| `ic_state_visibility_off.xml` | `action/visibility_off/materialiconsoutlined/black/res/drawable/outline_visibility_off_24.xml` |
| `ic_state_bedtime.xml` | `image/bedtime/materialiconsoutlined/black/res/drawable/outline_bedtime_24.xml` |
| `ic_state_sentiment_very_dissatisfied.xml` | `social/sentiment_very_dissatisfied/materialiconsoutlined/black/res/drawable/outline_sentiment_very_dissatisfied_24.xml` |
| `ic_state_hearing.xml` | `av/hearing/materialiconsoutlined/black/res/drawable/outline_hearing_24.xml` |

`ic_launcher_background.xml`(앱 아이콘의 금색 바탕)은 이 앱에서 만든 파일이다.

## 2. 빌드할 때 받아 앱에 들어가는 것(이 저장소에는 없음)

| 이름 | 버전 | 만든 곳 | 라이선스 |
|---|---|---|---|
| MediaPipe Tasks Vision, MediaPipe Tasks Audio | 1.1.1 | Google, [google-ai-edge/mediapipe](https://github.com/google-ai-edge/mediapipe) | Apache 2.0 |
| AndroidX Core KTX | 1.10.1 | Google(Android Jetpack) | Apache 2.0 |
| AndroidX AppCompat | 1.6.1 | Google(Android Jetpack) | Apache 2.0 |
| Material Components for Android | 1.10.0 | Google, [material-components/material-components-android](https://github.com/material-components/material-components-android) | Apache 2.0 |
| Kotlin 표준 라이브러리 | 빌드 도구가 정하는 버전 | JetBrains | Apache 2.0 |
| Pleos Connect SDK: Vehicle, TextToSpeech, NaviHelper | 2.3.2, 3.3.1, 3.0.5 | Pleos Playground(공모전에서 제공한 SDK) | Pleos Playground 약관. 오픈소스가 아니다 |

위 라이브러리가 함께 끌어오는 하위 라이브러리는 여기에 적지 않았다.

## 3. 시연 자료 (`app/src/main/assets/demo/`)

실제 카메라를 연결하기 전까지 시연에 쓰는 공개 사진과 소리다. 앱과 이 저장소에 함께 들어 있고, 파일별 표는 같은 폴더의 `SOURCES.csv`에 있다.

| 파일 | 원본 | 만든 이 | 라이선스 | 바꾼 것 |
|---|---|---|---|---|
| `c0362.jpg` | [Fotelik.samochodowy.Szymon - 001](https://commons.wikimedia.org/wiki/File:Fotelik.samochodowy.Szymon_-_001.JPG) (Wikimedia Commons) | Joymaster | 퍼블릭 도메인 | 500px로 줄임 |
| `c0329.jpg` | [Cadeirinha](https://commons.wikimedia.org/wiki/File:Cadeirinha_(22751835186).jpg) (Wikimedia Commons) | Senado Federal | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/) | 500px로 줄임 |
| `c0401.jpg` | [SAKIKO takes a nap](https://commons.wikimedia.org/wiki/File:SAKIKO_takes_a_nap_(47391925661).jpg) (Wikimedia Commons) | MIKI Yoshihito from Sapporo City, Hokkaido, JAPAN | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/) | 500px로 줄임 |
| `k0024.jpg` | [2013.VA.PocahontasSP.ColleenDennis](https://commons.wikimedia.org/wiki/File:2013.VA.PocahontasSP.ColleenDennis_(8338388915).jpg) (Wikimedia Commons) | vastateparksstaff | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/) | 500px로 줄임 |
| `carnoise.wav` | DEMAND TCAR ch01, [doi:10.5281/zenodo.1227121](https://doi.org/10.5281/zenodo.1227121) | J. Thiemann, N. Ito, E. Vincent | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) (Zenodo 표기는 CC BY 4.0, 문서 본문 표기는 CC BY-SA 3.0. 더 엄격한 쪽을 따름) | 앞 60초만 잘라 씀 |
| `cry_1.wav`, `cry_2.wav` | ESC-50 `1-22694-A-20`, `1-22694-B-20` ([Freesound 22694](http://www.freesound.org/people/Erdie/sounds/22694/)) + DEMAND TCAR | Erdie / J. Thiemann, N. Ito, E. Vincent | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | 아래 참고 |
| `cry_3.wav` | ESC-50 `1-60997-A-20` ([Freesound 60997](http://www.freesound.org/people/poissonmort/sounds/60997/)) + DEMAND TCAR | poissonmort / J. Thiemann, N. Ito, E. Vincent | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | 아래 참고 |
| `cry_4.wav` | ESC-50 `2-107351-B-20` ([Freesound 107351](http://www.freesound.org/people/ermfilm/sounds/107351/)) + DEMAND TCAR | ermfilm / J. Thiemann, N. Ito, E. Vincent | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | 아래 참고 |
| `cry_5.wav` | ESC-50 `2-80482-A-20` ([Freesound 80482](http://www.freesound.org/people/stevelalonde/sounds/80482/)) + DEMAND TCAR | stevelalonde / J. Thiemann, N. Ito, E. Vincent | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | 아래 참고 |
| `cry_6.wav` | ESC-50 `2-50666-A-20` ([Freesound 50666](http://www.freesound.org/people/BoilingSand/sounds/50666/)) + DEMAND TCAR | BoilingSand / J. Thiemann, N. Ito, E. Vincent | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | 아래 참고 |

- **`cry_*.wav`에서 바꾼 것:** 울음소리에 차 안 울림을 계산해 입히고(pyroomacoustics, MIT) DEMAND TCAR 차 소음을 섞었다. 울음이 소음보다 약 1.8배 크다. 울음 원본은 Freesound 클립 CC BY이고 ESC-50 안의 ESC-10 분류(CC BY 3.0)다. 차 소음의 조건에 맞춰 섞은 결과물은 CC BY-SA 3.0으로 둔다.
- ESC-50: K. J. Piczak, "ESC: Dataset for Environmental Sound Classification", 2015.
- `hidden.png`, `scenario.csv`, `sample_trips.tsv`는 이 앱을 위해 만든 파일이다. `sample_trips.tsv`의 기록은 실제로 다녀온 기록이 아니다.
- `readme/float_on_navi.png`(README의 그림): Pleos Connect 에뮬레이터 v2.1.0에서 이 앱을 실행한 화면을 2026-11-10에 찍은 것이다. 내비, 왼쪽 차 상태 칸, 아래 막대는 에뮬레이터에 들어 있는 화면이고, 오른쪽 위 작은 창의 아이 사진은 위 표의 `c0362.jpg`(퍼블릭 도메인)다.

## 4. 넣지 않은 것
- **글꼴 Asta Sans:** 차에 깔린 글꼴을 이름으로 불러 쓴다. 글꼴 파일은 이 저장소와 앱에 없다. 글꼴의 조건은 SIL Open Font License 1.1이다.
- **정확도 시험 자료:** 정확도를 잴 때 쓴 자료(ESC-50 전체, Donate-a-cry, 사진 묶음 등)는 이 저장소와 앱에 없다. 출처는 개발 기록에 파일별로 남겼다.

## 5. 확인한 날
- 모델과 아이콘의 조건 원문: 2026-11-03~2026-11-06
- 원본 저장소의 NOTICE 파일 유무, Apache 2.0 원문, 아이콘 도형 비교: 2026-11-08
- 시연 자료의 조건: 2026-11-03에 원문 확인, 사진 4장의 라이선스와 만든 이는 2026-11-09에 Wikimedia Commons에서 다시 확인, 파일별 표는 2026-11-09에 정리
