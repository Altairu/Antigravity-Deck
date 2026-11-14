"use strict";
// GitHub Releasesから最新の.vsixリリース情報を取得・ダウンロードする純粋Nodeモジュール
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DEFAULT_GITHUB_REPO = void 0;
exports.compareSemver = compareSemver;
exports.parseLatestVsixRelease = parseLatestVsixRelease;
exports.fetchLatestVsixRelease = fetchLatestVsixRelease;
exports.downloadVsixToTemp = downloadVsixToTemp;
const node_fs_1 = require("node:fs");
const node_https_1 = __importDefault(require("node:https"));
const node_os_1 = __importDefault(require("node:os"));
const node_path_1 = __importDefault(require("node:path"));
exports.DEFAULT_GITHUB_REPO = "Altairu/Antigravity-Deck";
// セマンティックバージョンを比較する (a < b なら負、a == b なら0、a > b なら正)
function compareSemver(current, target) {
    const parse = (raw) => raw
        .trim()
        .replace(/^v/i, "")
        .split("-")[0]
        .split(".")
        .map((part) => {
        const num = Number.parseInt(part, 10);
        return Number.isFinite(num) ? num : 0;
    });
    const a = parse(current);
    const b = parse(target);
    const maxLen = Math.max(a.length, b.length, 3);
    for (let i = 0; i < maxLen; i += 1) {
        const av = a[i] ?? 0;
        const bv = b[i] ?? 0;
        if (av !== bv) {
            return av - bv;
        }
    }
    return 0;
}
// GitHub Releases APIのJSONレスポンスから.vsixアセット情報を抽出する
function parseLatestVsixRelease(rawJson) {
    let data;
    try {
        data = JSON.parse(rawJson);
    }
    catch {
        return null;
    }
    if (data.draft || data.prerelease || !data.tag_name) {
        return null;
    }
    const assets = Array.isArray(data.assets) ? data.assets : [];
    const vsixAsset = assets.find((asset) => typeof asset.name === "string" &&
        asset.name.toLowerCase().endsWith(".vsix") &&
        typeof asset.browser_download_url === "string");
    if (!vsixAsset || !vsixAsset.name || !vsixAsset.browser_download_url) {
        return null;
    }
    return {
        tagName: data.tag_name,
        version: data.tag_name.replace(/^v/i, ""),
        assetName: vsixAsset.name,
        downloadUrl: vsixAsset.browser_download_url,
        htmlUrl: data.html_url
    };
}
function httpsGetText(url, redirectsRemaining = 5) {
    return new Promise((resolve, reject) => {
        const req = node_https_1.default.get(url, {
            headers: {
                "User-Agent": "Antigravity-Deck-Updater",
                Accept: "application/vnd.github+json"
            }
        }, (res) => {
            const status = res.statusCode ?? 0;
            const location = res.headers.location;
            if ((status === 301 || status === 302 || status === 307 || status === 308) && location) {
                res.resume();
                if (redirectsRemaining <= 0) {
                    reject(new Error("Too many redirects"));
                    return;
                }
                httpsGetText(location, redirectsRemaining - 1).then(resolve, reject);
                return;
            }
            const chunks = [];
            res.on("data", (chunk) => chunks.push(chunk));
            res.on("end", () => {
                resolve({ status, body: Buffer.concat(chunks).toString("utf8") });
            });
        });
        req.setTimeout(10000, () => {
            req.destroy(new Error("Request timed out"));
        });
        req.on("error", reject);
    });
}
// GitHub Releases APIから最新リリース情報を取得する
async function fetchLatestVsixRelease(repoSlug = exports.DEFAULT_GITHUB_REPO) {
    const apiUrl = `https://api.github.com/repos/${repoSlug}/releases/latest`;
    const response = await httpsGetText(apiUrl);
    if (response.status === 404) {
        return null;
    }
    if (response.status < 200 || response.status >= 300) {
        throw new Error(`GitHub API responded with status ${response.status}`);
    }
    return parseLatestVsixRelease(response.body);
}
function downloadWithRedirect(url, destPath, redirectsRemaining = 5) {
    return new Promise((resolve, reject) => {
        const req = node_https_1.default.get(url, {
            headers: {
                "User-Agent": "Antigravity-Deck-Updater",
                Accept: "application/octet-stream"
            }
        }, (res) => {
            const status = res.statusCode ?? 0;
            const location = res.headers.location;
            if ((status === 301 || status === 302 || status === 307 || status === 308) && location) {
                res.resume();
                if (redirectsRemaining <= 0) {
                    reject(new Error("Too many redirects"));
                    return;
                }
                downloadWithRedirect(location, destPath, redirectsRemaining - 1).then(resolve, reject);
                return;
            }
            if (status < 200 || status >= 300) {
                res.resume();
                reject(new Error(`Download failed with status ${status}`));
                return;
            }
            const fileStream = (0, node_fs_1.createWriteStream)(destPath);
            res.pipe(fileStream);
            fileStream.on("finish", () => {
                fileStream.close(() => resolve());
            });
            fileStream.on("error", (err) => {
                fileStream.close();
                reject(err);
            });
        });
        req.setTimeout(30000, () => {
            req.destroy(new Error("Download timed out"));
        });
        req.on("error", reject);
    });
}
// 一時ディレクトリへ.vsixファイルをダウンロードする
async function downloadVsixToTemp(release) {
    const dir = await node_fs_1.promises.mkdtemp(node_path_1.default.join(node_os_1.default.tmpdir(), "antigravity-deck-update-"));
    const safeName = node_path_1.default.basename(release.assetName);
    const destPath = node_path_1.default.join(dir, safeName);
    await downloadWithRedirect(release.downloadUrl, destPath);
    return destPath;
}
//# sourceMappingURL=github-release.js.map