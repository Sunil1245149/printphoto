const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const multer = require('multer');
const sharp = require('sharp');
const path = require('path');
const fs = require('fs-extra');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: { origin: "*" }
});

app.use(cors());
app.use(express.json());
app.use(express.static('public'));
app.use('/outputs', express.static('outputs'));

const upload = multer({ dest: 'uploads/' });

// Ensure directories exist
fs.ensureDirSync('uploads');
fs.ensureDirSync('outputs');
fs.ensureDirSync('history');

const settingsFile = 'settings.json';
if (!fs.existsSync(settingsFile)) {
    fs.writeJsonSync(settingsFile, { voiceEnabled: true });
}

let printQueue = [];
let history = [];

// API: Upload from Android App
app.post('/upload', upload.single('image'), async (req, res) => {
    try {
        const { layout } = req.body;
        const inputPath = req.file.path;
        const timestamp = Date.now();
        const outputPath = `outputs/print_${timestamp}.png`;

        console.log(`Received job: Layout ${layout}`);
        
        // Notify Portal: Job Received
        io.emit('job-received', { id: timestamp, status: 'Processing' });

        // Process Image with Sharp
        // Passport Standard: 3.5cm x 4.5cm
        // 4x6 Sheet: 1200x1800 px at 300 DPI
        
        const photo = sharp(inputPath);
        const metadata = await photo.metadata();

        // 1. Resize to passport size (approx 413x531 px for 300 DPI)
        const passportPhoto = await photo
            .resize(413, 531, { fit: 'cover' })
            .extend({
                top: 2, bottom: 2, left: 2, right: 2,
                background: { r: 0, g: 0, b: 0, alpha: 1 }
            }) // Black border
            .toBuffer();

        const sheetWidth = 1800; // 6 inch at 300 DPI
        const sheetHeight = 1200; // 4 inch at 300 DPI

        let compositeArr = [];
        let finalOutput;

        if (layout === "4") {
            // 4 photos in Portrait 4x6
            // Positions for 4 photos
            const margin = 100;
            const gap = 150;
            compositeArr = [
                { input: passportPhoto, top: margin, left: margin },
                { input: passportPhoto, top: margin, left: margin + 413 + gap },
                { input: passportPhoto, top: margin + 531 + gap, left: margin },
                { input: passportPhoto, top: margin + 531 + gap, left: margin + 413 + gap }
            ];
            
            finalOutput = sharp({
                create: {
                    width: 1200, height: 1800, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 }
                }
            });
        } else {
            // 8 photos in Landscape 4x6
            const margin = 50;
            const gapX = 40;
            const gapY = 80;
            
            for (let row = 0; row < 2; row++) {
                for (let col = 0; col < 4; col++) {
                    compositeArr.push({
                        input: passportPhoto,
                        top: margin + row * (531 + gapY),
                        left: margin + col * (413 + gapX)
                    });
                }
            }

            finalOutput = sharp({
                create: {
                    width: 1800, height: 1200, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 }
                }
            });
        }

        await finalOutput
            .composite(compositeArr)
            .toFile(outputPath);

        const job = {
            id: timestamp,
            status: 'Completed',
            layout: layout,
            preview: `/outputs/print_${timestamp}.png`,
            original: req.file.filename,
            time: new Date().toLocaleTimeString()
        };

        printQueue.push(job);
        history.unshift(job);
        if (history.length > 50) history.pop();

        // Trigger Auto-Print (Mocking existing system call)
        triggerAutoPrint(outputPath);

        // Notify Portal: Completed
        io.emit('job-completed', job);

        res.status(200).json({ success: true, jobId: timestamp });
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: 'Processing failed' });
    }
});

function triggerAutoPrint(filePath) {
    console.log(`ALREADY IMPLEMENTED: Sending ${filePath} to printing system...`);
    // This is where your existing auto-print logic would be triggered.
}

app.get('/settings', (req, res) => {
    const settings = fs.readJsonSync(settingsFile);
    res.json(settings);
});

app.post('/settings', (req, res) => {
    fs.writeJsonSync(settingsFile, req.body);
    res.json({ success: true });
});

app.get('/history', (req, res) => {
    res.json(history);
});

io.on('connection', (socket) => {
    console.log('Merchant Portal connected');
    socket.emit('initial-data', { history, settings: fs.readJsonSync(settingsFile) });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
