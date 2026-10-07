import java.util.*;

public class ScanController {
    private final List<Scan> scans = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isScanningActive = false;
    private volatile Thread workerThread = null;
    private volatile Scan currentScan = null;
    private volatile boolean stopCurrentScanRequested = false;
    private volatile boolean appRunning = true;

    public boolean isRunning() {
        return appRunning;
    }

    public boolean handleCommand(String command) {
        if (command == null) {
            return appRunning;
        }
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return appRunning;
        }

        if (trimmed.toLowerCase().startsWith("add:")) {
            handleAdd(trimmed.substring(4));
        } else if (trimmed.equalsIgnoreCase("view")) {
            handleView();
        } else if (trimmed.equalsIgnoreCase("start")) {
            handleStart();
        } else if (trimmed.equalsIgnoreCase("stop")) {
            handleStop();
        } else if (trimmed.toLowerCase().startsWith("remove:")) {
            handleRemove(trimmed.substring(7));
        } else if (trimmed.equalsIgnoreCase("exit")) {
            handleExit();
            return false;
        } else {
            System.out.println("Unknown command: " + command);
        }
        return appRunning;
    }

    private void handleAdd(String args) {
        String[] parts = args.split(",");
        if (parts.length < 4) {
            System.out.println("Invalid format for add command. Expected add:<id>, <name>, <duration>, <pause>");
            return;
        }
        String id = parts[0].trim();
        String name = parts[1].trim();
        int duration;
        try {
            duration = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid duration specified: " + parts[2]);
            return;
        }
        String pauseStr = parts[3].trim();
        boolean pause = pauseStr.equalsIgnoreCase("Yes") || pauseStr.equalsIgnoreCase("true");

        Scan scan = new Scan(id, name, duration, pause);
        scans.add(scan);
        System.out.println("Added: Scan:" + id + ", " + name + ", " + duration + ", " + (pause ? "Yes" : "No"));
    }

    private void handleView() {
        synchronized (scans) {
            if (scans.isEmpty()) {
                System.out.println("Current Queue --> (empty)");
                return;
            }
            for (Scan s : scans) {
                System.out.println("Current Queue --> Scan:" + s.getId() + ", " + s.getName() + ", " + s.getDurationSeconds() + ", " + (s.isPauseAfter() ? "Yes" : "No") + " [" + s.getState() + "]");
            }
        }
    }

    private synchronized void handleStart() {
        if (isScanningActive) {
            System.out.println("Scanning is already in progress.");
            return;
        }

        boolean hasIdle = false;
        synchronized (scans) {
            for (Scan s : scans) {
                if (s.getState() == ScanState.IDLE) {
                    hasIdle = true;
                    break;
                }
            }
        }

        if (!hasIdle) {
            System.out.println("No IDLE scans in queue to start.");
            return;
        }

        isScanningActive = true;
        workerThread = new Thread(this::processQueue, "ScanWorkerThread");
        workerThread.start();
    }

    private void processQueue() {
        while (isScanningActive && appRunning) {
            Scan scanToRun = null;
            synchronized (scans) {
                for (Scan s : scans) {
                    if (s.getState() == ScanState.IDLE) {
                        scanToRun = s;
                        break;
                    }
                }
            }

            if (scanToRun == null) {
                isScanningActive = false;
                break;
            }

            executeScan(scanToRun);
        }
        isScanningActive = false;
    }

    private void executeScan(Scan scan) {
        scan.setState(ScanState.RUNNING);
        currentScan = scan;
        stopCurrentScanRequested = false;

        System.out.println("Starting " + scan.getName());

        long durationMs = scan.getDurationSeconds() * 1000L;
        long startTime = System.currentTimeMillis();
        boolean cancelled = false;

        while (System.currentTimeMillis() - startTime < durationMs) {
            if (stopCurrentScanRequested || Thread.currentThread().isInterrupted() || !appRunning) {
                cancelled = true;
                break;
            }
            try {
                long remaining = durationMs - (System.currentTimeMillis() - startTime);
                long sleepChunk = Math.min(100L, Math.max(1L, remaining));
                Thread.sleep(sleepChunk);
            } catch (InterruptedException e) {
                cancelled = true;
                break;
            }
        }

        if (cancelled || stopCurrentScanRequested) {
            scan.setState(ScanState.CANCELLED);
            System.out.println("Cancelled " + scan.getName());
            currentScan = null;
            stopCurrentScanRequested = false;
            
        } else {
            scan.setState(ScanState.COMPLETE);
            System.out.println("Completed " + scan.getName());
            currentScan = null;

            
            if (scan.isPauseAfter()) {
                isScanningActive = false;
            }
        }
    }

    private synchronized void handleStop() {
        if (currentScan != null) {
            stopCurrentScanRequested = true;
            if (workerThread != null) {
                workerThread.interrupt();
            }
        } else {
            System.out.println("No scan is currently running.");
        }
    }

    private void handleRemove(String id) {
        id = id.trim();
        synchronized (scans) {
            Scan found = null;
            for (Scan s : scans) {
                if (s.getId().equals(id)) {
                    found = s;
                    break;
                }
            }

            if (found == null) {
                System.out.println("Scan with ID " + id + " not found.");
                return;
            }

            if (found.getState() == ScanState.IDLE) {
                scans.remove(found);
                System.out.println("Removed scan " + id);
            } else {
                System.out.println("Cannot remove scan " + id + " because its state is " + found.getState() + ".");
            }
        }
    }

    private synchronized void handleExit() {
        appRunning = false;
        isScanningActive = false;
        if (currentScan != null) {
            stopCurrentScanRequested = true;
            if (workerThread != null) {
                workerThread.interrupt();
            }
        }
        System.out.println("Exiting application...");
    }
}
