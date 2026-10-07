public class Scan {
    private final String id;
    private final String name;
    private final int durationSeconds;
    private final boolean pauseAfter;
    private volatile ScanState state;

    public Scan(String id, String name, int durationSeconds, boolean pauseAfter) {
        this.id = id;
        this.name = name;
        this.durationSeconds = durationSeconds;
        this.pauseAfter = pauseAfter;
        this.state = ScanState.IDLE;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isPauseAfter() {
        return pauseAfter;
    }

    public ScanState getState() {
        return state;
    }

    public void setState(ScanState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "Scan:" + id + ", " + name + ", " + durationSeconds + ", " + (pauseAfter ? "Yes" : "No") + " [" + state + "]";
    }
}
