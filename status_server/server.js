const express = require('express');
const os = require('os');
const si = require('systeminformation');
const cors = require('cors');
const fs = require('fs').promises;
const path = require('path');
const toml = require('@iarna/toml');

const infoPath = path.resolve(__dirname, 'info.toml');

const app = express();
app.use(cors());

let trueIp = [];
let config = {};
let getCount = 0;
const start_time = Date.now()
let server;
const defaultTOML = `
# Всё из этого файла получается только при запуске server.js
# Если вы изменили что-то во время его работы, перезапустите его
[logs]

do_get_log = true # Делать ли логи GET запросов? (true - да, false - нет, по умолчанию true)
get_frequency = 120 # Частота логов GET запросов в секундах (минимум 5, 120 - по умолчанию)
`;
function getTime() {
    return new Date().toTimeString().split(' ')[0];
}
function makeLog(str, error=false) {
    if (!error) {
        console.log(`[${getTime()}]: ${str}`)
    } else {
        console.error(`[${getTime()}]: ${str}`)
    }
}

async function init() {
    function isPrivateIp(ip) {
        return /^10\./.test(ip) ||
            /^192\.168\./.test(ip) ||
            /^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(ip);
    }

    config = await loadTOML();
    let frequency = config.logs?.get_frequency;
    if (frequency < 5) {
        makeLog("Минимальная задержка логов GET: 5с")
        await fs.writeFile(infoPath, defaultTOML);
        console.log(`[${getTime()}]: Файл info.toml пересоздан`)
        config = await loadTOML();
        frequency = config.logs?.get_frequency;
    }
    if (frequency && config.logs.do_get_log) {
        setInterval(() => {
            makeLog(`За ${frequency}с было ${getCount} GET-запросов`)
            getCount = 0;
        }, frequency*1000)
    }

    makeLog(`Поиск локального IP...`)
    const start_finder = Date.now();
    const data = await si.networkInterfaces();
    trueIp = data
        .filter(element => !element.virtual && (element.type === "wired" || element.type === "wireless"))
        .map(element => element.ip4)
        .filter(ip => isPrivateIp(ip));

    const duration_finder = ((Date.now() - start_finder) / 1000).toFixed(2);
    console.log(`[${getTime()}]: Поиск осуществлялся ${duration_finder}с`);

    if (trueIp.length > 0) {
        makeLog(`Локальный IP хоста: ${trueIp[0]}`)
    } else {
        makeLog(`Локальный IP не найден — возможно, устройство не подключено к сети или нет активного IP-адреса.`)
    }

    const PORT = 8080;
    const HOST = '0.0.0.0';
    server = app.listen(PORT, HOST, () => {
        makeLog(`Сервер запущен: http://${trueIp[0] || 'localhost'}:${PORT}/status`)
    });
}
async function loadTOML() {
    try {
        const data = await fs.readFile(infoPath, 'utf-8');
        const parsed = toml.parse(data);
        console.log(`--- Для конфигурации см. info.toml ---`)
        if (parsed.logs && typeof parsed.logs.get_frequency === 'number') {
            makeLog(`Получены данные из info.toml`)
            return parsed;
        } else {
            makeLog(`Файл info.toml повреждён`)
            await fs.writeFile(infoPath, defaultTOML);
            makeLog(`Файл info.toml пересоздан`)
            return toml.parse(defaultTOML);
        }
    } catch (err) {
        if (err.code === 'ENOENT') {
            makeLog(`Файл info.toml не найден`)
            await fs.writeFile(infoPath, defaultTOML);
            return toml.parse(defaultTOML);
        } else {
            throw err;
        }
    }
}

init();

app.get('/status', async (req, res) => {
    try {
        getCount++;

        const cpu = await si.currentLoad();
        const memory = await si.mem();
        const disks = await si.fsSize();
        const battery = await si.battery();

        res.json({
            cpu: {
                load: (cpu.currentLoad ?? 0).toFixed(1)
            },
            mem: {
                total: (memory.total / 1073741824).toFixed(2),
                used: ((memory.total - memory.available) / 1073741824).toFixed(2),
                free: (memory.available / 1073741824).toFixed(2),

                swap_total: (memory.swaptotal / 1073741824).toFixed(2),
                swap_used: (memory.swapused / 1073741824).toFixed(2),
                swap_free: (memory.swapfree / 1073741824).toFixed(2)
            },
            disk: disks.map(disk => ({
                fs: disk.fs,
                size: (disk.size / 1073741824).toFixed(2),
                used: (disk.used / 1073741824).toFixed(2),
                free: (disk.available / 1073741824).toFixed(2)
            })),
            battery: {
                has: battery.hasBattery,
                is: battery.isCharging,
                percent: battery.percent,
                remaining: battery.timeRemaining
            },
            os: {
                up: os.uptime().toFixed(0)
            }
        });
    } catch (err) {
        makeLog(`Ошибка: ${err}`)
        res.status(500).send('Ошибка: ' + err.message);
    }
});

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

function shutdown() {
    const end_time = Date.now();
    const work_time_ms = end_time - start_time;
    const total_seconds = Math.floor(work_time_ms / 1000);
    const work_hours = Math.floor(total_seconds / 3600);
    const remaining_seconds = total_seconds % 3600;
    const work_minutes = Math.floor(remaining_seconds / 60);
    const work_seconds = remaining_seconds % 60;

    makeLog(`Общее время работы: ${work_hours}ч ${work_minutes}мин ${work_seconds}с`)
    makeLog(`Завершение работы сервера...`)
    server.close(() => {
        makeLog(`Сервер остановлен`)
        process.exit(0);
    });
    setTimeout(() => {
        makeLog(`Принудительное завершение!`)
        process.exit(1);
    }, 5000);
}
