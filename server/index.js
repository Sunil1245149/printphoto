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
app.get('/', (req, res) => res.json({ status: 'online', message: 'Passport Print Server' }));
app.get('/ping', (req, res) => res.send('pong'));

app.post('/upload', (req, res, next) => {
    upload.any()(req, res, (err) => {
        if (err) {
            console.error('MULTER ERROR:', err);
            return res.status(400).json({ success: false, error: 'Upload failed', details: err.message });
        }
        next();
    });
}, async (req, res) => {
    console.log('--- New Upload Request ---');
    console.log('Timestamp:', new Date().toISOString());
    console.log('Headers:', JSON.stringify(req.headers, null, 2));
    
    try {
        let layout = '8';
        if (req.body && req.body.layout) {
            layout = req.body.layout;
            if (Array.isArray(layout)) layout = layout[0];
            if (typeof layout !== 'string') layout = layout.toString();
        }
        
        console.log('Body fields:', Object.keys(req.body || {}));
        console.log('Effective Layout:', layout);
        
        // Find all images regardless of fieldname for debugging
        const allFiles = req.files || [];
        console.log('Files count:', allFiles.length);
        allFiles.forEach(f => console.log(` - File: field=${f.fieldname}, name=${f.originalname}, size=${f.size}`));

        const files = allFiles.filter(f => f.fieldname === 'image' || f.fieldname === 'images' || f.fieldname === 'photo');
        
        if (files.length === 0) {
            console.log('No relevant files found. Falling back to all files if any.');
            if (allFiles.length > 0) {
                files.push(allFiles[0]);
            } else {
                return res.status(400).json({ success: false, error: 'No images uploaded' });
            }
        }

        const timestamp = Date.now();
        const outputPath = path.join('outputs', `print_${timestamp}.png`);
        
        io.emit('job-received', { id: timestamp, status: 'Processing' });

        const sheetWidth = 1800; // 6 inch at 300 DPI
        const sheetHeight = 1200; // 4 inch at 300 DPI
        const pWidth = 413; // 3.5cm
        const pHeight = 531; // 4.5cm
        const borderSize = 2;
        const gapSize = 10; 

        // Helper to process a single photo with border and gap
        const processPhoto = async (filePath) => {
            console.log(`Processing file: ${filePath}`);
            try {
                const photo = await sharp(filePath)
                    .resize(pWidth, pHeight, { fit: 'cover' })
                    // Black border
                    .extend({
                        top: borderSize, bottom: borderSize, left: borderSize, right: borderSize,
                        background: { r: 0, g: 0, b: 0, alpha: 1 }
                    })
                    .toBuffer();
                
                console.log(`Photo resized and bordered for ${filePath}`);

                const w = pWidth + (borderSize + gapSize) * 2;
                const h = pHeight + (borderSize + gapSize) * 2;
                
                // Create dotted border SVG
                const svgBorder = Buffer.from(`
                    <svg width="${w}" height="${h}">
                        <rect x="1" y="1" width="${w-2}" height="${h-2}" fill="none" stroke="#DDDDDD" stroke-width="1" stroke-dasharray="5,5" />
                    </svg>
                `);

                return await sharp({
                    create: { width: w, height: h, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
                })
                .composite([
                    { input: photo, top: gapSize, left: gapSize },
                    { input: svgBorder, top: 0, left: 0 }
                ])
                .toBuffer();
            } catch (e) {
                console.error('Error in processPhoto:', e);
                throw e;
            }
        };

        const processedPhotos = await Promise.all(files.map(f => processPhoto(f.path)));
        
        let compositeArr = [];
        let finalOutput;

        if (layout === "4") {
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
            const photo1 = processedPhotos[0];
            const photo2 = processedPhotos[1] || processedPhotos[0];
            const fullW = pWidth + (borderSize + gapSize) * 2;
            const fullH = pHeight + (borderSize + gapSize) * 2;
            
            const marginX = (sheetWidth - (fullW * 4)) / 2;
            const marginY = (sheetHeight - (fullH * 2)) / 2;

            for (let i = 0; i < 4; i++) {
                compositeArr.push({ input: photo1, top: marginY, left: marginX + i * fullW });
            }
            for (let i = 0; i < 4; i++) {
                compositeArr.push({ input: photo2, top: marginY + fullH, left: marginX + i * fullW });
            }

            finalOutput = sharp({
                create: { width: 1800, height: 1200, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
            });
        } else {
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

        await finalOutput
            .composite(compositeArr)
            .toFile(outputPath);

        console.log(`Job completed and saved to ${outputPath}`);

        // Cleanup uploads
        files.forEach(f => fs.remove(f.path).catch(console.error));

        const job = {
            id: timestamp,
            status: 'Completed',
            layout: layout || "8",
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
        console.error('Processing error:', err);
        res.status(500).json({ error: 'Processing failed', message: err.message });
    }
});

// Global error handler
app.use((err, req, res, next) => {
    console.error('GLOBAL ERROR:', err);
    res.status(500).json({ error: 'Internal Server Error', details: err.message });
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

const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
