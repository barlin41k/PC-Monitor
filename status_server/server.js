const express = require('express');
const os = require('os');
const si = require('systeminformation');
const cors = require('cors');

const app = express();
app.use(cors());

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
                free: (memory.available / 1073741824).toFixed(2)
            },
            disk: disks.map(disk => ({
                fs: disk.fs,
                size: (disk.size / 1073741824).toFixed(2),
                used: (disk.used / 1073741824).toFixed(2),
                free: (disk.available / 1073741824).toFixed(2)
            })),
            battery: {
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

const PORT = 8080;
const HOST = '0.0.0.0';
app.listen(PORT, HOST, () => {
    console.log(`Сервер запущен: http://localhost:${PORT}/status`);
});
