import express from 'express';
import cors from 'cors';
import morgan from 'morgan';
import path from 'node:path';
import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import fetch from 'node-fetch';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 4000;
const API_PREFIX = '/api';
const DATA_DIR = path.join(__dirname, 'data');

app.use(cors());
app.use(morgan('dev'));
app.use(express.json());

// --- PROXY /api/img TO https://squadcalc.app/api/img... ---
app.use('/api/img', async (req, res) => {
    try {
        // originalUrl is like /api/img/xxx?y=1
        const targetUrl = `https://squadcalc.app${req.originalUrl}`;
        console.log('Proxy /api/img ->', targetUrl);

        const fetchOptions = {
            method: req.method,
            headers: {
                ...req.headers,
                host: 'squadcalc.app'
            }
        };

        // For GET/HEAD there is no body
        if (!['GET', 'HEAD'].includes(req.method) && req.body) {
            // We only know how to forward JSON body safely here
            if (Object.keys(req.body).length > 0) {
                fetchOptions.body = JSON.stringify(req.body);
                fetchOptions.headers['content-type'] = 'application/json';
            }
        }

        const response = await fetch(targetUrl, fetchOptions);

        res.status(response.status);
        // copy headers
        response.headers.forEach((value, key) => {
            res.setHeader(key, value);
        });

        const buffer = await response.arrayBuffer();
        res.send(Buffer.from(buffer));
    } catch (err) {
        console.error('Error proxying /api/img:', err);
        res.status(500).json({ error: 'Error proxying /api/img request' });
    }
});

// Utility to load JSON from /data
async function readJson(relativePath) {
    const fullPath = path.join(DATA_DIR, relativePath);
    const content = await fs.readFile(fullPath, 'utf8');
    return JSON.parse(content);
}

// /api/get/layers?map=AlBasrah  (ALWAYS LOCAL)
app.get(`${API_PREFIX}/get/layers`, async (req, res) => {
    const map = req.query.map;
    if (!map) {
        return res.status(400).json({ error: "Missing 'map' query parameter" });
    }

    try {
        const data = await readJson(`get/layers/${map}.json`);
        res.json(data);
    } catch (err) {
        if (err.code === 'ENOENT') {
            return res.status(404).json({
                error: `No mock data for map '${map}'`
            });
        }
        console.error(err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// /api/get/layer?name=AlBasrah_Invasion_v1
// SD_* -> LOCAL, others -> PROXY TO https://squadcalc.app/api/get/layer
app.get(`${API_PREFIX}/get/layer`, async (req, res) => {
    const name = req.query.name;
    console.log('GET /get/layer, name =', name);

    if (!name) {
        return res.status(400).json({ error: "Missing 'name' query parameter" });
    }

    // If name starts with SD_, use local mock JSON
    if (String(name).startsWith('SD_')) {
        try {
            const data = await readJson(`get/layer/${name}.json`);
            return res.json(data);
        } catch (err) {
            if (err.code === 'ENOENT') {
                return res.status(404).json({
                    error: `No mock data for layer '${name}'`
                });
            }
            console.error(err);
            return res.status(500).json({ error: 'Internal server error' });
        }
    }

    // Otherwise, proxy to remote API
    try {
        const url = new URL('https://squadcalc.app/api/get/layer');

        // forward all query params
        Object.entries(req.query).forEach(([key, value]) => {
            if (Array.isArray(value)) {
                value.forEach(v => url.searchParams.append(key, v));
            } else if (value != null) {
                url.searchParams.set(key, value);
            }
        });

        console.log('Proxy /api/get/layer ->', url.toString());

        const response = await fetch(url.toString(), {
            method: 'GET',
            headers: {
                accept: 'application/json'
            }
        });

        const text = await response.text();
        res.status(response.status);

        try {
            const json = JSON.parse(text);
            res.json(json);
        } catch {
            // Fallback if it wasn't valid JSON for some reason
            res.send(text);
        }
    } catch (err) {
        console.error('Error proxying /api/get/layer:', err);
        res.status(500).json({ error: 'Error proxying /api/get/layer request' });
    }
});

// Optional: list available top-level resources
// GET /api  ->  ["markers", "heatmaps", "sessions", ...]
app.get(API_PREFIX, async (_req, res) => {
    try {
        const files = await fs.readdir(DATA_DIR, { withFileTypes: true });
        const resources = files
            .filter((f) => f.isDirectory() || f.name.endsWith('.json'))
            .map((f) => (f.isDirectory() ? f.name : f.name.replace(/\.json$/, '')));
        res.json({ resources });
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: 'Failed to list resources' });
    }
});

// GET /api/:resource -> data/:resource.json
app.get(`${API_PREFIX}/:resource`, async (req, res) => {
    const { resource } = req.params;

    try {
        const data = await readJson(`${resource}.json`);
        res.json(data);
    } catch (err) {
        if (err.code === 'ENOENT') {
            return res.status(404).json({ error: `Resource ${resource} not found` });
        }
        console.error(err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// GET /api/:resource/:id -> data/:resource/:id.json
// Example: /api/sessions/abc123 -> data/sessions/abc123.json
app.get(`${API_PREFIX}/:resource/:id`, async (req, res) => {
    const { resource, id } = req.params;

    try {
        const data = await readJson(path.join(resource, `${id}.json`));
        res.json(data);
    } catch (err) {
        if (err.code === 'ENOENT') {
            return res.status(404).json({
                error: `Resource ${resource}/${id} not found`
            });
        }
        console.error(err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// Very simple echo handler for POST/PUT/DELETE so frontend calls don't crash
app.all(`${API_PREFIX}/*`, (req, res) => {
    res.json({
        mock: true,
        method: req.method,
        path: req.path,
        body: req.body || null
    });
});

app.listen(PORT, () => {
    console.log(`Mock API listening on http://localhost:${PORT}${API_PREFIX}`);
});
