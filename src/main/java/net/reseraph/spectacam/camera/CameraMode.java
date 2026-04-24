package net.reseraph.spectacam.camera;

public enum CameraMode {
    FIRST_PERSON("First Person"),
    THIRD_PERSON("Third Person"),
    ORBIT("Orbit / Fly-Around");

    private final String displayName;

    CameraMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CameraMode next() {
        CameraMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
