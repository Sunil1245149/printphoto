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
app.post('/upload', upload.array('image'), async (req, res) => {
    console.log('--- New Upload Request ---');
    try {
        const { layout } = req.body;
        const files = req.files;
        console.log(`Layout: ${layout}, Files count: ${files?.length}`);
        if (!files || files.length === 0) {
            return res.status(400).json({ error: 'No images uploaded' });
        }

        const timestamp = Date.now();
        const outputPath = `outputs/print_${timestamp}.png`;

        console.log(`Received job: Layout ${layout}, Files: ${files.length}`);
        
        io.emit('job-received', { id: timestamp, status: 'Processing' });

        const sheetWidth = 1800; // 6 inch at 300 DPI
        const sheetHeight = 1200; // 4 inch at 300 DPI
        const pWidth = 413; // 3.5cm
        const pHeight = 531; // 4.5cm
        const borderSize = 2;
        const gapSize = 15; // White gap

        // Helper to process a single photo with border and gap
        const processPhoto = async (filePath) => {
            const photo = await sharp(filePath)
                .resize(pWidth, pHeight, { fit: 'cover' })
                // Black border
                .extend({
                    top: borderSize, bottom: borderSize, left: borderSize, right: borderSize,
                    background: { r: 0, g: 0, b: 0, alpha: 1 }
                })
                .toBuffer();

            // Create a dotted border SVG
            const w = pWidth + (borderSize + gapSize) * 2;
            const h = pHeight + (borderSize + gapSize) * 2;
            const svgBorder = `
                <svg width="${w}" height="${h}">
                    <rect x="2" y="2" width="${w-4}" height="${h-4}" 
                        fill="none" 
                        stroke="#CCCCCC" 
                        stroke-width="1" 
                        stroke-dasharray="5,5" />
                </svg>
            `;

            return await sharp({
                create: { width: w, height: h, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
            })
            .composite([
                { input: photo, top: gapSize, left: gapSize },
                { input: Buffer.from(svgBorder), top: 0, left: 0 }
            ])
            .toBuffer();
        };

        const processedPhotos = await Promise.all(files.map(f => processPhoto(f.path)));
        
        let compositeArr = [];
        let finalOutput;

        if (layout === "4") {
            // 4 photos in Portrait 4x6
            const photo = processedPhotos[0];
            const margin = 100;
            const fullW = pWidth + (borderSize + gapSize) * 2;
            const fullH = pHeight + (borderSize + gapSize) * 2;
            const gap = 100;

            compositeArr = [
                { input: photo, top: margin, left: margin },
                { input: photo, top: margin, left: margin + fullW + gap },
                { input: photo, top: margin + fullH + gap, left: margin },
                { input: photo, top: margin + fullH + gap, left: margin + fullW + gap }
            ];
            
            finalOutput = sharp({
                create: { width: 1200, height: 1800, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
            });
        } else if (layout === "2x4") {
            // 2 types of photos, 4 copies each in Landscape 4x6
            const photo1 = processedPhotos[0];
            const photo2 = processedPhotos[1] || processedPhotos[0]; // Fallback to same if only 1
            const fullW = pWidth + (borderSize + gapSize) * 2;
            const fullH = pHeight + (borderSize + gapSize) * 2;
            
            const marginX = (sheetWidth - (fullW * 4)) / 2;
            const marginY = (sheetHeight - (fullH * 2)) / 2;

            // Row 1: Photo 1
            for (let i = 0; i < 4; i++) {
                compositeArr.push({ input: photo1, top: marginY, left: marginX + i * fullW });
            }
            // Row 2: Photo 2
            for (let i = 0; i < 4; i++) {
                compositeArr.push({ input: photo2, top: marginY + fullH, left: marginX + i * fullW });
            }

            finalOutput = sharp({
                create: { width: 1800, height: 1200, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
            });
        } else {
            // 8 photos in Landscape 4x6 (Standard 8 layout)
            const photo = processedPhotos[0];
            const fullW = pWidth + (borderSize + gapSize) * 2;
            const fullH = pHeight + (borderSize + gapSize) * 2;
            
            const marginX = (sheetWidth - (fullW * 4)) / 2;
            const marginY = (sheetHeight - (fullH * 2)) / 2;

            for (let row = 0; row < 2; row++) {
                for (let col = 0; col < 4; col++) {
                    compositeArr.push({
                        input: photo,
                        top: marginY + row * fullH,
                        left: marginX + col * fullW
                    });
                }
            }

            finalOutput = sharp({
                create: { width: 1800, height: 1200, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
            });
        }

        // Add dotted lines for cutting (Simple implementation: drawing lines around each composite item)
        // For simplicity in this environment without complex drawing commands in sharp directly easily, 
        // the white gap already helps. To add "dotted" lines we can overlay a grid.
        
        await finalOutput
            .composite(compositeArr)
            .toFile(outputPath);

        // Cleanup uploads
        files.forEach(f => fs.remove(f.path).catch(console.error));

        const job = {
            id: timestamp,
            status: 'Completed',
            layout: layout,
            preview: `/outputs/print_${timestamp}.png`,
            time: new Date().toLocaleTimeString()
        };

        printQueue.push(job);
        history.unshift(job);
        if (history.length > 50) history.pop();

        triggerAutoPrint(outputPath);
        io.emit('job-completed', job);

        res.status(200).json({ success: true, jobId: timestamp });
    } catch (err) {
        console.error('Error processing upload:', err);
        res.status(500).json({ error: 'Processing failed', message: err.message });
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
