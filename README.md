# Zapret for Android

Android-порт идеи [zapret-discord-youtube](https://github.com/flowseal/zapret-discord-youtube)
для обхода DPI-блокировок Discord, YouTube и других сервисов **без root**.

Приложение поднимает локальный SOCKS5-прокси [byedpi (ciadpi)](https://github.com/hufrea/byedpi),
который применяет приёмы обхода DPI (disorder, split, fake, oob, tlsrec) к исходящим
TCP-соединениям, и заворачивает в него трафик через `VpnService` при помощи
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (tun2socks).

## Как это работает

```
приложение (YouTube, Discord, …)
        │
        ▼
VpnService (TUN)               ← режим «VPN»: весь трафик устройства
        │
        ▼
hev-socks5-tunnel (tun2socks)
        │  TCP/UDP → SOCKS5
        ▼
ciadpi (byedpi) 127.0.0.1:1080 ← обход DPI: disorder / fake / split / oob / tlsrec
        │
        ▼
   интернет
```

Ключевая деталь: собственный пакет исключён из собственного VPN
(`addDisallowedApplication`), поэтому исходящие сокеты ciadpi идут напрямую в сеть
и не заворачиваются обратно в туннель. Root не нужен: NFQUEUE/zapret/kernel-модули
не используются, весь обход работает в userspace.

## Режимы

| Режим | Описание |
|---|---|
| **VPN (все приложения)** | Туннель `VpnService` заворачивает весь трафик устройства в ciadpi. |
| **Только прокси** | Запускается один ciadpi на `127.0.0.1:1080`; удобно комбинировать с другими VPN-клиентами (например, AdGuard), которые умеют SOCKS5-прокси. |

## Стратегии обхода

| Стратегия | Аргументы ciadpi | Комментарий |
|---|---|---|
| Авто: disorder → tlsrec | `--disorder 1 --auto=torst --timeout 3 --tlsrec 1+s` | Рекомендуется по умолчанию |
| Disorder | `--disorder 1` | Классика, оптимально для Linux-ядра (Android) |
| Split + disorder | `--split 1+s --disorder 3+s` | Разбиение по SNI/Host |
| Fake (TTL) | `--fake -1 --ttl 8` | TTL фейка подбирается, чтобы не дошёл до сервера |
| Fake + disorder | `--disorder 1 --fake -1` | Работает и там, где TTL-трюк не помогает |
| Fake + MD5 signature | `--fake -1 --md5sig` | Поддерживается не всеми устройствами |
| OOB | `--oob 3+s` | Байт вне потока внутри SNI |
| Авто: tlsrec только при блокировке | `--auto=torst --timeout 3 --tlsrec 3+s` | Ничего не делает, пока нет блокировки |
| Без обхода | — | Обычный SOCKS5-прокси для проверки |

В поле «Доп. аргументы ciadpi» можно добавить любые опции
(полный список: `ciadpi --help` или README byedpi).

## Возможности

- Обход DPI без root (VPN-режим и режим «только прокси»)
- Пресеты стратегий в духе zapret + произвольные аргументы ciadpi
- Переключатели UDP (`-U`) и IPv6 (`-X`), настройка DNS для VPN
- Автозапуск при загрузке устройства
- Плитка быстрых настроек (Quick Settings Tile)
- Автоматический перезапуск прокси при смене настроек на лету
- Русская и английская локализация

## Сборка

Требования:

- Android Studio Ladybug или новее (или только командная строка)
- JDK 17
- Android SDK: Platform 35, Build-Tools 35
- NDK `27.0.12077973` и CMake `3.22.1` (ставятся из SDK Manager)

Сборка:

```
gradlew.bat assembleDebug        # Windows
./gradlew assembleDebug          # Linux/macOS
```

Готовый APK: `app/build/outputs/apk/debug/app-debug.apk`

Либо откройте папку проекта в Android Studio и нажмите Run.

### Что внутри

- `byedpi` (ciadpi) собран из исходников в `app/src/main/cpp/byedpi` как `libbyedpi.so`
  для всех ABI (arm64-v8a, armeabi-v7a, x86_64, x86);
- JNI-мост `app/src/main/cpp/byedpi_jni.c`: перед каждым запуском сбрасывает
  глобальные параметры ciadpi и вызывает его `main()` на отдельном потоке;
  остановка — `shutdown()` слушающего сокета;
- `hev-socks5-tunnel` подключён готовым AAR `app/libs/hev-socks5-tunnel.aar`
  (версия 2.19.0). AAR декларирует minSdk 29, но его нативная библиотека
  использует только символы, доступные с API 21, поэтому в манифесте
  требование переопределено (`tools:overrideLibrary`), а приложение
  поддерживает Android 7.0+ (API 24);
- VPN-сервис исключает собственный пакет из туннеля и устанавливает TUN
  (`10.111.222.1/32`, DNS по умолчанию `1.1.1.1`).

## Использование

1. Установите APK, откройте приложение.
2. Выберите стратегию (начните с «Авто: disorder → tlsrec»).
3. Нажмите «Запустить» и разрешите создание VPN-подключения.
4. Проверьте YouTube/Discord. Если не работает — переберите стратегии:
   обычно помогают «Split + disorder» или «Fake + disorder».

Советы:

- «Fake + MD5 signature» зависит от ядра устройства: если дискорд/ютуб работают
  нестабильно, вернитесь на «Fake + disorder».
- Если провайдер блокирует и UDP (игры, QUIC), оставьте «Проксировать UDP»
  включённым; в редких случаях помогает выключить.
- Режим «Только прокси» удобен вместе с AdGuard: включите переключатель
  «Proxy only», запустите приложение и укажите в AdGuard SOCKS5-прокси
  `127.0.0.1:1080`.

## Ограничения

- Работает без root, значит не может фильтровать на уровне ядра: обход DPI
  выполняется только для TCP/TLS-трафика, проходящего через прокси.
- Некоторые приложения ходят по UDP/QUIC напрямую — им обход не поможет,
  пока трафик не пойдёт через SOCKS5 (hev умеет UDP на проброс).
- Одновременно может быть активен только один сторонний VPN — если вы
  используете другой VPN-клиент, применяйте режим «Только прокси».

## Благодарности и лицензия

- [byedpi](https://github.com/hufrea/byedpi) — Hugo (MIT), движок обхода DPI `ciadpi`
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — heiher (MIT), tun2socks
- [ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid) — dovecoteescapee, референс интеграции
- [zapret](https://github.com/bol-van/zapret) и [zapret-discord-youtube](https://github.com/flowseal/zapret-discord-youtube) — источники идей и стратегий

Проект распространяется по лицензии MIT (см. `LICENSE`).
