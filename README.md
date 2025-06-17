# 🖥️ PC Monitor
![GitHub repo size](https://img.shields.io/github/repo-size/barlin41k/PC-Monitor?style=for-the-badge)
![GitHub issues](https://img.shields.io/github/issues/barlin41k/PC-Monitor?style=for-the-badge)
![GitHub stars](https://img.shields.io/github/stars/barlin41k/PC-Monitor?style=for-the-badge)
![GitHub forks](https://img.shields.io/github/forks/barlin41k/PC-Monitor?style=for-the-badge)
![License](https://img.shields.io/github/license/barlin41k/PC-Monitor?style=for-the-badge)

![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Node.js](https://img.shields.io/badge/Node.js-339933?style=for-the-badge&logo=nodedotjs&logoColor=white)

## 📋 Описание

**PC Monitor** — это набор из Android-приложения и серверной части на Node.js, который позволяет отслеживать состояние компьютера, используя GET-запросы в локальной сети.

- **Android-приложение** написано на Kotlin. Оно подключается к серверу, получает данные GET-запросом с API и отображает информацию о состоянии ПК в удобном интерфейсе.
- **Сервер на Node.js** собирает данные о состоянии ПК и предоставляет их через HTTP GET для мобильного клиента.

---

## ⚒️ Функционал

<table>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/barlin41k/PC-Monitor/main/animation.gif" width="100%" alt="Превью">
    </td>
    <td width="50%" valign="top">
      <h3>🧠 Возможности:</h3>
      <ul>
        <li>Мониторинг состояния:
          <ul>
            <li>процессора</li>
            <li>оперативной памяти</li>
            <li>дисков</li>
            <li>батареи (для ноутбуков)</li>
            <li>операционной системы</li>
          </ul>
        </li>
        <li>Работа через HTTP-запрос</li>
        <li>Управление IP-адресом для подключения к серверу</li>
        <li><b>Пользоваться вне локальной сети нельзя</b></li>
      </ul>
    </td>
  </tr>
</table>

**➕ Превью версии 1.0.2**

---

## 📥 Установка и запуск

1. Скачать последний [релиз](https://github.com/barlin41k/PC-Monitor/releases)
2. Открыть архив и распаковать папку `status_server` в удобном месте
3. Перейти в распакованную папку и открыть в ней командную строку
4. Установить зависимости:

```bash
  npm install
  npm install systeminformation
```
5. Запустить сервер:
```bash
  node server.js
```
6. Установить мобильное приложение (можно найти в [последних релизах](https://github.com/barlin41k/PC-Monitor/releases))
  - *чтобы всё работало, требуется подключить компьютер и телефон к одной сети*
7. В мобильном приложении ввести IP-адрес, полученный вами в консоли
8. Пользоваться!

## 🔐 Безопасность и конфиденциальность

#### 🖱️ При вводе IP-адреса вашего компьютера в мобильном приложении:

- Мы используем локальный IP-адрес, а не публичный IP!
- IP-адрес не отправляется никуда кроме вашего локального устройства.
  - Приложение использует его только для связи с сервером, который запущен у вас в локальной сети.
- Данные не передаются в интернет и не хранятся на сторонних серверах.
  - Всё взаимодействие происходит только в пределах вашей локальной сети.
- Ваш IP-адрес не раскрывается и не сохраняется в облаках или на внешних сервисах.

Чтобы обеспечить безопасность, сервер принимает запросы только из вашей локальной сети, и доступ извне закрыт.

#### 📃 Гарантии

- Если вы не доверяете нашему ПО, в релизах вы сможете найти исходный код сервера и мобильного приложения и изучить его
- Наше ПО выполняется только в локальной сети, поэтому ваш IP-адрес не может раскрываться и храниться где-либо.

## ⌨️ Используемое
#### 👅 Языки
- [Node.js](https://nodejs.org/en)
  - модуль [systeminformation](https://systeminformation.io/)
- [Kotlin](https://kotlinlang.org/)
