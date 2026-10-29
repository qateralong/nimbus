package com.qateralong.nimbus.render;

public record Faces(int[] offsets, float[] planes) {
    public static final int GROUPS = 7;
    public static final int UNASSIGNED = 6;
}
