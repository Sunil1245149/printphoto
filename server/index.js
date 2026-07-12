const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const multer = require('multer');
const sharp = require('sharp');
const Jimp = require('jimp');
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
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    next();
});
app.use(express.static('public'));
app.use('/outputs', express.static('outputs'));

const upload = multer({ dest: 'uploads/' });

// Disable sharp cache to save memory
sharp.cache(false);

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

// Support both /upload and /api/upload
const uploadHandler = [
    (req, res, next) => {
        upload.any()(req, res, (err) => {
            if (err) {
                console.error('MULTER ERROR:', err);
                return res.status(400).json({ success: false, error: 'Upload failed', details: err.message });
            }
            next();
        });
    },
    async (req, res) => {
        console.log('--- New Upload Request ---');
        sharp.cache(false);
        console.log('Timestamp:', new Date().toISOString());
        
        try {
            let layout = '8';
            if (req.body && req.body.layout) {
                layout = req.body.layout;
                if (Array.isArray(layout)) layout = layout[0];
                if (typeof layout !== 'string') layout = layout.toString();
            }
            
            console.log('Effective Layout:', layout);
            
            const allFiles = req.files || [];
            console.log('Files count:', allFiles.length);
            allFiles.forEach(f => console.log(` - File: ${f.originalname}, Size: ${f.size}, Mime: ${f.mimetype}`));
            
            // Accept any file field
            const files = allFiles;
            
            if (files.length === 0) {
                return res.status(400).json({ success: false, error: 'No images uploaded' });
            }

            const timestamp = Date.now();
            const outputPath = path.join('outputs', `print_${timestamp}.png`);
            
            io.emit('job-received', { id: timestamp, status: 'Processing' });

            const sheetWidth = 1800; // 6 inch at 300 DPI
            const sheetHeight = 1200; // 4 inch at 300 DPI
            const pWidth = 390; // Reduced for smaller size
            const pHeight = 505; // Reduced for smaller size
            const borderSize = 2;
            const gapSize = 10; 

            // Helper to process a single photo
            const processPhoto = async (file) => {
                const filePath = file.path;
                console.log(`Processing file: ${filePath} (${file.originalname}, ${file.mimetype}, ${file.size} bytes)`);
                
                if (!fs.existsSync(filePath)) {
                    throw new Error(`File not found on server: ${path.basename(filePath)}`);
                }

                if (file.size === 0) {
                    throw new Error(`File ${file.originalname} is empty`);
                }

                const buffer = await fs.readFile(filePath);
                
                if (buffer.length === 0) {
                    throw new Error('File buffer is empty');
                }

                // Log first few bytes for debugging
                console.log(`Buffer for ${file.originalname}: ${buffer.slice(0, 20).toString('hex')}`);

                // Check for HTML/XML
                const header = buffer.toString('utf8', 0, 100).toLowerCase();
                if (header.includes('<!doctype') || header.includes('<html') || header.includes('<?xml')) {
                    throw new Error(`File is HTML/XML (not an image). Possible server redirection or error page.`);
                }
                
                let photo;
                try {
                    // Try sharp first
                    photo = await sharp(buffer)
                        .rotate()
                        .resize(pWidth, pHeight, { 
                            fit: 'cover',
                            withoutEnlargement: false,
                            kernel: sharp.kernel.lanczos3
                        })
                        .sharpen({
                            sigma: 1.0,
                            m1: 2,
                            m2: 10
                        })
                        .extend({
                            top: borderSize, bottom: borderSize, left: borderSize, right: borderSize,
                            background: { r: 0, g: 0, b: 0, alpha: 1 }
                        })
                        .png()
                        .toBuffer();
                } catch (sharpErr) {
                    console.error(`Sharp failed for ${file.originalname}: ${sharpErr.message}`);
                    try {
                        // Jimp as second resort
                        const jimpImage = await Jimp.read(buffer);
                        const jimpBuffer = await jimpImage
                            .cover(pWidth, pHeight)
                            .getBufferAsync(Jimp.MIME_PNG);
                        
                        photo = await sharp(jimpBuffer)
                            .extend({
                                top: borderSize, bottom: borderSize, left: borderSize, right: borderSize,
                                background: { r: 0, g: 0, b: 0, alpha: 1 }
                            })
                            .png()
                            .toBuffer();
                    } catch (jimpErr) {
                        console.error(`Jimp also failed for ${file.originalname}: ${jimpErr.message}`);
                        const magic = buffer.slice(0, 10).toString('hex');
                        throw new Error(`Image format not supported or file is corrupt (${file.originalname}). Type: ${file.mimetype}, Magic: ${magic}. Try another photo.`);
                    }
                }
                
                const w = pWidth + (borderSize + gapSize) * 2;
                const h = pHeight + (borderSize + gapSize) * 2;
                
                const svgBorder = Buffer.from(`
                    <svg width="${w}" height="${h}">
                        <rect x="1" y="1" width="${w-2}" height="${h-2}" fill="none" stroke="#000000" stroke-width="1.5" stroke-dasharray="6,4" />
                    </svg>
                `);

                return await sharp({
                    create: { width: w, height: h, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
                })
                .composite([
                    { input: photo, top: gapSize, left: gapSize },
                    { input: svgBorder, top: 0, left: 0 }
                ])
                .png()
                .toBuffer();
            };

            const processedPhotos = [];
            for (const f of files) {
                try {
                    const p = await processPhoto(f);
                    processedPhotos.push(p);
                } catch (pe) {
                    console.error('Photo processing failed:', pe);
                    // Continue with other photos if any, or throw if none
                }
            }

            if (processedPhotos.length === 0) {
                throw new Error('All uploaded images failed to process. Check if they are valid images.');
            }
            
            let compositeArr = [];
            let finalOutput;
            // ... (rest of layout logic)

        if (layout === "4") {
            const photo = processedPhotos[0];
            const fullW = pWidth + (borderSize + gapSize) * 2;
            const fullH = pHeight + (borderSize + gapSize) * 2;
            
            // Cluster them in the center like others
            const totalW = (fullW * 2);
            const totalH = (fullH * 2);

            const marginX = Math.floor((sheetWidth - totalW) / 2);
            const marginY = Math.floor((sheetHeight - totalH) / 2);

            compositeArr = [
                { input: photo, top: marginY, left: marginX },
                { input: photo, top: marginY, left: marginX + fullW },
                { input: photo, top: marginY + fullH, left: marginX },
                { input: photo, top: marginY + fullH, left: marginX + fullW }
            ];
            
            finalOutput = sharp({
                create: { width: 1800, height: 1200, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
            });
        } else if (layout === "2x4") {
            const photo1 = processedPhotos[0];
            const photo2 = processedPhotos[1] || processedPhotos[0];
            const fullW = pWidth + (borderSize + gapSize) * 2;
            const fullH = pHeight + (borderSize + gapSize) * 2;
            
            // On 1800x1200 (6x4 Landscape)
            const interGapX = 0; 
            const interGapY = 0; 
            const totalW = (fullW * 4);
            const totalH = (fullH * 2);

            const marginX = Math.floor((sheetWidth - totalW) / 2);
            const marginY = Math.floor((sheetHeight - totalH) / 2);

            for (let i = 0; i < 4; i++) {
                compositeArr.push({ input: photo1, top: marginY, left: marginX + i * fullW });
            }
            for (let i = 0; i < 4; i++) {
                compositeArr.push({ input: photo2, top: marginY + fullH, left: marginX + i * fullW });
            }

            finalOutput = sharp({
                create: { width: 1800, height: 1200, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } }
            });
        } else if (layout === "Single") {
            const photoBuffer = await fs.readFile(files[0].path);
            const metadata = await sharp(photoBuffer).metadata();
            const isLandscape = (metadata.width || 0) > (metadata.height || 0);
            
            // 4x6 inches at 300 DPI is 1200x1800 or 1800x1200
            const targetW = isLandscape ? 1800 : 1200;
            const targetH = isLandscape ? 1200 : 1800;
            const margin = 40; // Approx 3.4mm margin

            finalOutput = sharp(photoBuffer)
                .rotate()
                .resize(targetW - (margin * 2), targetH - (margin * 2), { 
                    fit: 'cover', 
                    background: { r: 255, g: 255, b: 255, alpha: 1 },
                    kernel: sharp.kernel.lanczos3
                })
                .extend({
                    top: margin,
                    bottom: margin,
                    left: margin,
                    right: margin,
                    background: { r: 255, g: 255, b: 255, alpha: 1 }
                })
                .sharpen({
                    sigma: 1.2,
                    m1: 2,
                    m2: 15
                });
            
            compositeArr = []; 
        } else {
            const photo = processedPhotos[0];
            const fullW = pWidth + (borderSize + gapSize) * 2;
            const fullH = pHeight + (borderSize + gapSize) * 2;
            
            const interGapX = 0; 
            const interGapY = 0; 
            const totalW = (fullW * 4);
            const totalH = (fullH * 2);

            const marginX = Math.floor((sheetWidth - totalW) / 2);
            const marginY = Math.floor((sheetHeight - totalH) / 2);

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
            .png()
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

        io.emit('job-completed', job);

        res.status(200).json({ success: true, jobId: timestamp });
    } catch (err) {
        console.error('Processing error:', err);
        res.status(500).json({ error: 'Processing failed', message: err.message });
    }
}];

app.post('/upload', uploadHandler);
app.post('/api/upload', uploadHandler);

// Global error handler
app.use((err, req, res, next) => {
    console.error('GLOBAL ERROR:', err);
    res.status(500).json({ error: 'Internal Server Error', details: err.message });
});

app.get('/settings', (req, res) => {
    const settings = fs.readJsonSync(settingsFile);
    res.json(settings);
});

app.post('/settings', (req, res) => {
    fs.writeJsonSync(settingsFile, req.body);
    res.json({ success: true });
});

// Catch-all for 404s
app.use((req, res) => {
    console.warn(`[404 NOT FOUND] ${req.method} ${req.url}`);
    res.status(404).json({ error: 'Route not found', path: req.url, method: req.method });
});

app.get('/history', (req, res) => {
    res.json(history);
});

io.on('connection', (socket) => {
    console.log('Merchant Portal connected');
    socket.emit('initial-data', { history, settings: fs.readJsonSync(settingsFile) });
});

const PORT = process.env.PORT || 8080;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on port ${PORT}`);
});
