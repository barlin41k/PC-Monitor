# 🖥️ PC Monitor
![GitHub repo size](https://img.shields.io/github/repo-size/barlin41k/PC-Monitor?style=for-the-badge)
![GitHub issues](https://img.shields.io/github/issues/barlin41k/PC-Monitor?style=for-the-badge)
![GitHub stars](https://img.shields.io/github/stars/barlin41k/PC-Monitor?style=for-the-badge)
![GitHub forks](https://img.shields.io/github/forks/barlin41k/PC-Monitor?style=for-the-badge)
![License](https://img.shields.io/github/license/barlin41k/PC-Monitor?style=for-the-badge)

![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js-339933?style=for-the-badge&logo=nodedotjs&logoColor=white)

## 📋 Описание

**PC Monitor** — это набор из Android-приложения и серверной части на Node.js, который позволяет отслеживать состояние компьютера, используя локальную сеть.

- **Android-приложение** написано на Kotlin. Оно подключается к серверу, получает данные GET-запросом с API и отображает информацию о состоянии ПК в удобном интерфейсе.
- **Сервер на Node.js** собирает данные о состоянии ПК и предоставляет их через локальное API, которое использует мобильный клиент.

---

## ⚒️ Функционал

<table>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/barlin41k/PC-Monitor/main/gifs/animation_2.0.2.gif" width="100%" alt="Превью">
    </td>
    <td width="50%" valign="top">
      <h3>🧠 Возможности:</h3>
      <ul>
        <li><b>Мониторинг состояния:</b>
          <ul>
            <li>процессора</li>
            <li>оперативной памяти
            <ul><li>файла подкачки</li></ul>
            </li>
            <li>дисков</li>
            <li>батареи (для ноутбуков)</li>
            <li>операционной системы</li>
          </ul>
        </li>
        <li><b>Работа через GET-запросы в локальной сети</b></li>
      </ul>
    </td>
  </tr>
</table>

**➕ Превью версии 2.0.2**

### ❓ Ответы на возможные вопросы
- **PC Monitor использует ограниченный набор данных мониторинга, так как библиотека на сервере не всегда способна получить очень подробную информацию о состоянии системы.**
- **В нашем проекте реализован максимально кроссплатформенный мониторинг, чтобы обеспечить совместимость со всеми основными операционными системами и избежать проблем с доступом к информации**

---

## 📥 Установка и запуск

1. Перейти в директорию, где будет репозиторий
2. Клонировать репозиторий и перейти в папку:
```bash
  git clone https://github.com/barlin41k/PC-Monitor.git
  cd PC-Monitor
```
3. Скачать [Node.js](https://nodejs.org/en) и зависимости:
```bash
  npm install
  npm install systeminformation
  npm install @iarna/toml
```
5. Запустить сервер:
```bash
  node server/server.js
```
6. Установить мобильное приложение (можно найти в [последних релизах](https://github.com/barlin41k/PC-Monitor/releases)), гарантии его безопасности [см. тут](https://github.com/barlin41k/PC-Monitor?tab=readme-ov-file#-%D0%B3%D0%B0%D1%80%D0%B0%D0%BD%D1%82%D0%B8%D0%B8)
- *чтобы всё работало, требуется подключить компьютер и телефон к одной сети*
7. В мобильном приложении ввести IP-адрес, полученный вами в консоли
8. Просмотреть конфигурации для лучшего опыта -> `server/info.toml`
9. Пользоваться!

## 🔐 Безопасность и конфиденциальность

#### 🖱️ При вводе IP-адреса вашего компьютера в мобильном приложении:

- Мы используем локальный IP-адрес, а не публичный!
- В любом случае, IP-адрес не отправляется никуда кроме вашего устройства.
  - Приложение использует IP-адрес только для связи с сервером, который запущен у вас в локальной сети.
- Данные не передаются в интернет и не хранятся на сторонних серверах.
  - Всё взаимодействие происходит только в пределах вашей локальной сети.

#### 📃 Гарантии

- Если вы не доверяете нашему ПО, вы можете найти исходный код сервера и мобильного приложения и изучить его
- Для гарантии безопасности мобильного приложения, запускайте Google Play проверку во время его установки
- Наше ПО выполняется только в локальной сети, использует только локальный IP-адрес, который в любом случае не раскрывается и не хранится где-либо помимо вашего устройства

## ⌨️ Используемое в проекте
#### 🔧 Языки
- [Node.js](https://nodejs.org/en)
  - модуль [systeminformation](https://www.npmjs.com/package/systeminformation)
  - модуль [toml](https://www.npmjs.com/package/@iarna/toml)
- [Kotlin](https://kotlinlang.org/)
#### ⌨️ IDE
- [Android Studio](https://developer.android.com/studio?hl=en)
- [VSCode](https://code.visualstudio.com/)