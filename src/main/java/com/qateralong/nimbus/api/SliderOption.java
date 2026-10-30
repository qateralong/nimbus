package com.qateralong.nimbus.api;

public final class SliderOption extends Option<Integer, SliderOption> {
    private int min;
    private int max = 100;
    private int step = 1;

    SliderOption(String id) {
        super(id);
    }

    public SliderOption range(int min, int max, int step) {
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        return this;
    }

    public int min() {
        return this.min;
    }

    public int max() {
        return this.max;
    }

    public int step() {
        return this.step;
    }

    public int snap(double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        int raw = this.min + (int) Math.round(clamped * (this.max - this.min) / this.step) * this.step;
        return Math.max(this.min, Math.min(this.max, raw));
    }

    public double fraction(int value) {
        return this.max == this.min ? 0.0 : (double) (value - this.min) / (this.max - this.min);
    }
}
