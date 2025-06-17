const express = require('express');
const os = require('os');
const si = require('systeminformation');
const cors = require('cors');

const app = express();
app.use(cors());

let trueIp = [];

async function init() {
    function isPrivateIp(ip) {
        return /^10\./.test(ip) ||
            /^192\.168\./.test(ip) ||
            /^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(ip);
    }
    console.log("Поиск локального IP...")
    const data = await si.networkInterfaces();
    trueIp = data
        .filter(element => !element.virtual && (element.type === "wired" || element.type === "wireless"))
        .map(element => element.ip4)
        .filter(ip => isPrivateIp(ip));

    console.clear()
    if (trueIp.length > 0) {
        console.log(`Локальный IP хоста: ${trueIp[0]}`);
    } else {
        console.log('Локальный IP не найден — возможно, устройство не подключено к сети или нет активного IP-адреса.');
    }

    const PORT = 8080;
    const HOST = '0.0.0.0';
    app.listen(PORT, HOST, () => {
        console.log(`Сервер запущен: http://${trueIp[0] || 'localhost'}:${PORT}/status`);
    });
}

init();

app.get('/status', async (req, res) => {
    try {
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
                active: (memory.active / 1073741824).toFixed(2),

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
        console.error("Ошибка:", err);
        res.status(500).send('Error: ' + err.message);
    }
});
