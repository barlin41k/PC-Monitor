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
        //const cpu_temp = await si.cpuTemperature();
        const battery = await si.battery();

        let totalDisk=0
        let usedDisk=0
        let freeDisk=0
        disks.map((element) => {
            totalDisk += element.size
            usedDisk += element.used
            freeDisk += element.available
        })

        // 1024^3 = 1073741824
        res.json({   
            cpu: {
                load: (cpu.currentLoad ?? 0).toFixed(1),
                //temp: cpu_temp.main
            },
            mem: {
                total: (memory.total / 1073741824).toFixed(2),
                used: ((memory.total - memory.available) / 1073741824).toFixed(2),
                free: (memory.available / 1073741824).toFixed(2)
            },
            disk: {
                total: (totalDisk / 1073741824).toFixed(2),
                used: (usedDisk / 1073741824).toFixed(2),
                free: (freeDisk / 1073741824).toFixed(2),
            },
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

function getLocalIP() {
    const interfaces = os.networkInterfaces();
    for (let name of Object.keys(interfaces)) {
        for (let iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    return 'localhost';
}

const PORT = 8080;
const HOST = '0.0.0.0';
app.listen(PORT, HOST, () => {
    const ip = getLocalIP();
    console.log(`Сервер запущен: http://${ip}:${PORT}/status`);
});
