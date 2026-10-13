import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import { AppItem } from '../types';
import { useScrollLock } from '../hooks/useScrollLock';
import { runCatalogDiagnostic, MissionResult } from '../utils/catalogScanner';

interface ScannerTestUIProps {
  apps: AppItem[];
  onClose: () => void;
}

export const ScannerTestUI: React.FC<ScannerTestUIProps> = ({ apps, onClose }) => {
  useScrollLock(true); // Prevent background scrolling

  const [isScanning, setIsScanning] = useState(false);
  const [progress, setProgress] = useState(0);
  const [total, setTotal] = useState(apps.length);
  const [results, setResults] = useState<MissionResult[]>([]);
  const [scanComplete, setScanComplete] = useState(false);

  const handleStartScan = async () => {
    setIsScanning(true);
    setProgress(0);
    setScanComplete(false);
    setResults([]);

    // Run the engine
    const foundIssues = await runCatalogDiagnostic(apps, (scanned, totalApps) => {
      setProgress(scanned);
      setTotal(totalApps);
    });

    setResults(foundIssues);
    setIsScanning(false);
    setScanComplete(true);
  };

  const percentage = total === 0 ? 0 : Math.round((progress / total) * 100);

  return createPortal(
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <div className="relative flex h-[80vh] w-full max-w-2xl flex-col rounded-3xl bg-surface-light shadow-2xl dark:bg-surface-dark">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-theme-border p-6">
          <h2 className="text-xl font-black text-theme-text">Mission Scanner (Admin)</h2>
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full bg-theme-element text-theme-sub hover:bg-theme-hover hover:text-theme-text"
          >
            <i className="fas fa-times"></i>
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {!isScanning && !scanComplete && (
            <div className="flex flex-col items-center justify-center text-center pt-10">
              <i className="fas fa-radar text-6xl text-primary/50 mb-6"></i>
              <h3 className="text-2xl font-bold text-theme-text mb-2">Ready to Scan</h3>
              <p className="text-theme-sub mb-8 max-w-sm">
                This will run a diagnostic on all {apps.length} apps in your catalog, checking for missing text and sending HTTP HEAD requests to verify URLs.
              </p>
              <button
                onClick={handleStartScan}
                className="rounded-full bg-primary px-8 py-3 font-bold text-white shadow-lg shadow-primary/30 transition-transform hover:scale-105 active:scale-95"
              >
                Run Diagnostic Now
              </button>
            </div>
          )}

          {isScanning && (
            <div className="flex flex-col items-center justify-center pt-20">
              <i className="fas fa-circle-notch fa-spin text-5xl text-primary mb-6"></i>
              <h3 className="text-xl font-bold text-theme-text mb-2">Scanning Catalog...</h3>
              <div className="w-full max-w-md bg-theme-element rounded-full h-4 mb-2 overflow-hidden relative">
                <div 
                  className="bg-primary h-full transition-all duration-300"
                  style={{ width: `${percentage}%` }}
                ></div>
              </div>
              <p className="text-theme-sub font-mono">{progress} / {total} apps checked</p>
            </div>
          )}

          {scanComplete && (
            <div className="flex flex-col">
              <div className="mb-6 flex items-center justify-between bg-primary/10 p-4 rounded-2xl border border-primary/20">
                <div>
                  <h3 className="text-lg font-bold text-primary">Scan Complete</h3>
                  <p className="text-sm text-theme-sub">Found {results.length} apps with issues out of {total}.</p>
                </div>
                <button
                  onClick={handleStartScan}
                  className="rounded-full bg-theme-element px-4 py-2 text-sm font-bold text-theme-text hover:bg-theme-hover"
                >
                  Scan Again
                </button>
              </div>

              {results.length === 0 ? (
                <div className="text-center py-10">
                  <i className="fas fa-check-circle text-5xl text-green-500 mb-4"></i>
                  <p className="text-theme-text font-bold">Everything looks perfect!</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {results.map((res) => (
                    <div key={res.appId} className="bg-theme-element rounded-xl p-4 border border-theme-border">
                      <div className="flex items-center gap-2 mb-2">
                        <span className="font-bold text-theme-text">{res.appName}</span>
                        <span className="text-xs text-theme-sub font-mono bg-black/10 dark:bg-white/10 px-2 py-0.5 rounded-md">{res.appId}</span>
                      </div>
                      <ul className="list-disc pl-5 space-y-1">
                        {res.issues.map((issue, idx) => (
                          <li key={idx} className="text-sm text-red-500 dark:text-red-400 font-medium">
                            {issue}
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
};
