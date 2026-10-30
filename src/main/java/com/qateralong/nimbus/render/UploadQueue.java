package com.qateralong.nimbus.render;

import java.util.Queue;

public interface UploadQueue {
    Queue<Runnable> nimbus$uploads();
}
