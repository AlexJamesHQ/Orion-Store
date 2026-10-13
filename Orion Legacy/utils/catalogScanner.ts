import { AppItem } from '../types';

export interface MissionResult {
  appId: string;
  appName: string;
  issues: string[];
}

/**
 * Checks a URL using a fast HTTP HEAD request.
 * Returns true if the link is alive (200-399).
 * Returns false if dead (404, 500, timeout).
 */
async function isUrlAlive(url: string): Promise<boolean> {
  if (!url || !url.startsWith('http')) return false;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 5000); // 5 second timeout

  try {
    const response = await fetch(url, {
      method: 'HEAD', // We only want the headers, not the 2MB image!
      cache: 'no-store',
      signal: controller.signal,
      // mode: 'no-cors' // We don't use no-cors because we need to see the status code
    });

    clearTimeout(timeoutId);
    // 200s and 300s (redirects) are considered alive
    return response.ok || (response.status >= 300 && response.status < 400);
  } catch (error: any) {
    clearTimeout(timeoutId);
    
    // In a browser environment, CORS blocks might cause a TypeError "Failed to fetch".
    // In a real native Capacitor app, native HTTP bypasses CORS. 
    // For this test, if it fails, we assume it's dead or blocked.
    console.warn(`[Scanner] URL check failed for ${url}:`, error.message);
    return false;
  }
}

/**
 * Scans a single app for missing text and dead links.
 */
async function scanSingleApp(app: AppItem): Promise<MissionResult | null> {
  const issues: string[] = [];

  // --- 1. Text Checks (Instant) ---
  if (!app.author || app.author.trim() === '' || app.author.toLowerCase() === 'unknown') {
    issues.push('Missing Developer Name');
  }
  if (!app.description || app.description.trim().length < 20) {
    issues.push('Description missing or too short');
  }
  if (!app.screenshots || app.screenshots.length === 0) {
    issues.push('No screenshots provided');
  }

  // --- 2. Network Checks (HTTP HEAD) ---
  if (app.icon) {
    const alive = await isUrlAlive(app.icon);
    if (!alive) issues.push(`Dead Icon URL: ${app.icon}`);
  } else {
    issues.push('Missing Icon URL');
  }

  if (app.downloadUrl) {
    const alive = await isUrlAlive(app.downloadUrl);
    if (!alive) issues.push(`Dead Download URL: ${app.downloadUrl}`);
  } else {
    issues.push('Missing Download URL');
  }

  // Check the first screenshot as a sample to save time, or check all of them.
  // For the test, let's just check the first one.
  if (app.screenshots && app.screenshots.length > 0) {
    const firstScreenshot = app.screenshots[0] as string;
    const alive = await isUrlAlive(firstScreenshot);
    if (!alive) issues.push(`Dead Screenshot URL: ${firstScreenshot}`);
  }

  if (issues.length > 0) {
    return { appId: app.id, appName: app.name, issues };
  }
  return null;
}

/**
 * The Main Scanner Engine (Batched Concurrency)
 */
export async function runCatalogDiagnostic(
  apps: AppItem[],
  onProgress: (scanned: number, total: number) => void
): Promise<MissionResult[]> {
  const results: MissionResult[] = [];
  const BATCH_SIZE = 10; // Check 10 apps at the exact same time
  
  let scannedCount = 0;

  for (let i = 0; i < apps.length; i += BATCH_SIZE) {
    const batch = apps.slice(i, i + BATCH_SIZE);
    
    // Fire off all 10 app checks simultaneously
    const batchPromises = batch.map(app => scanSingleApp(app));
    
    // Wait for the batch of 10 to finish
    const batchResults = await Promise.all(batchPromises);
    
    // Collect the ones that had issues
    for (const res of batchResults) {
      if (res) results.push(res);
    }

    scannedCount += batch.length;
    onProgress(scannedCount, apps.length);
  }

  return results;
}
