// GitHub Releasesから最新の.vsixリリース情報を取得・ダウンロードする純粋Nodeモジュール

import { createWriteStream, promises as fs } from "node:fs";
import https from "node:https";
import os from "node:os";
import path from "node:path";

export const DEFAULT_GITHUB_REPO = "Altairu/Antigravity-Deck";

export interface LatestVsixRelease {
  tagName: string;
  version: string;
  assetName: string;
  downloadUrl: string;
  htmlUrl?: string;
}

interface GitHubReleaseAsset {
  name?: string;
  browser_download_url?: string;
}

interface GitHubReleaseApiResponse {
  tag_name?: string;
  html_url?: string;
  draft?: boolean;
  prerelease?: boolean;
  assets?: GitHubReleaseAsset[];
}

// セマンティックバージョンを比較する (a < b なら負、a == b なら0、a > b なら正)
export function compareSemver(current: string, target: string): number {
  const parse = (raw: string): number[] =>
    raw
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
export function parseLatestVsixRelease(rawJson: string): LatestVsixRelease | null {
  let data: GitHubReleaseApiResponse;
  try {
    data = JSON.parse(rawJson) as GitHubReleaseApiResponse;
  } catch {
    return null;
  }

  if (data.draft || data.prerelease || !data.tag_name) {
    return null;
  }

  const assets = Array.isArray(data.assets) ? data.assets : [];
  const vsixAsset = assets.find(
    (asset) =>
      typeof asset.name === "string" &&
      asset.name.toLowerCase().endsWith(".vsix") &&
      typeof asset.browser_download_url === "string"
  );

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

function httpsGetText(url: string, redirectsRemaining = 5): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const req = https.get(
      url,
      {
        headers: {
          "User-Agent": "Antigravity-Deck-Updater",
          Accept: "application/vnd.github+json"
        }
      },
      (res) => {
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

        const chunks: Buffer[] = [];
        res.on("data", (chunk: Buffer) => chunks.push(chunk));
        res.on("end", () => {
          resolve({ status, body: Buffer.concat(chunks).toString("utf8") });
        });
      }
    );

    req.setTimeout(10000, () => {
      req.destroy(new Error("Request timed out"));
    });
    req.on("error", reject);
  });
}

// GitHub Releases APIから最新リリース情報を取得する
export async function fetchLatestVsixRelease(
  repoSlug = DEFAULT_GITHUB_REPO
): Promise<LatestVsixRelease | null> {
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

function downloadWithRedirect(
  url: string,
  destPath: string,
  redirectsRemaining = 5
): Promise<void> {
  return new Promise((resolve, reject) => {
    const req = https.get(
      url,
      {
        headers: {
          "User-Agent": "Antigravity-Deck-Updater",
          Accept: "application/octet-stream"
        }
      },
      (res) => {
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

        const fileStream = createWriteStream(destPath);
        res.pipe(fileStream);
        fileStream.on("finish", () => {
          fileStream.close(() => resolve());
        });
        fileStream.on("error", (err) => {
          fileStream.close();
          reject(err);
        });
      }
    );

    req.setTimeout(30000, () => {
      req.destroy(new Error("Download timed out"));
    });
    req.on("error", reject);
  });
}

// 一時ディレクトリへ.vsixファイルをダウンロードする
export async function downloadVsixToTemp(release: LatestVsixRelease): Promise<string> {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "antigravity-deck-update-"));
  const safeName = path.basename(release.assetName);
  const destPath = path.join(dir, safeName);
  await downloadWithRedirect(release.downloadUrl, destPath);
  return destPath;
}
