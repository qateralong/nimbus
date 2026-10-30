package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.lwjgl.opengl.GL20;

final class TerrainProgram {
    final int id;
    final int mvp;
    final int fogShape;
    final int colorModulator;
    final int fogStart;
    final int fogEnd;
    final int fogColor;
    final int alphaCutoff;
    final int cameraBlock;
    final int cameraFrac;

    TerrainProgram(boolean cutout) throws IOException {
        int vertex = compile(GL20.GL_VERTEX_SHADER, source("terrain.vsh"));
        String fsh = source("terrain.fsh");
        if (cutout) {
            int line = fsh.indexOf('\n') + 1;
            fsh = fsh.substring(0, line) + "#define ALPHA_CUTOFF\n" + fsh.substring(line);
        }
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, fsh);
        this.id = GL20.glCreateProgram();
        GL20.glAttachShader(this.id, vertex);
        GL20.glAttachShader(this.id, fragment);
        GL20.glLinkProgram(this.id);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(this.id, GL20.GL_LINK_STATUS) == 0) {
            throw new IOException("Terrain program link failed: " + GL20.glGetProgramInfoLog(this.id));
        }
        this.mvp = GL20.glGetUniformLocation(this.id, "MvpMat");
        this.fogShape = GL20.glGetUniformLocation(this.id, "FogShape");
        this.colorModulator = GL20.glGetUniformLocation(this.id, "ColorModulator");
        this.fogStart = GL20.glGetUniformLocation(this.id, "FogStart");
        this.fogEnd = GL20.glGetUniformLocation(this.id, "FogEnd");
        this.fogColor = GL20.glGetUniformLocation(this.id, "FogColor");
        this.alphaCutoff = GL20.glGetUniformLocation(this.id, "AlphaCutoff");
        this.cameraBlock = GL20.glGetUniformLocation(this.id, "CameraBlock");
        this.cameraFrac = GL20.glGetUniformLocation(this.id, "CameraFrac");
        GlStateManager._glUseProgram(this.id);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.id, "Sampler0"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.id, "Sampler2"), 2);
        GlStateManager._glUseProgram(0);
    }

    static String source(String name) throws IOException {
        try (InputStream stream = TerrainProgram.class.getResourceAsStream("/assets/nimbus/shaders/terrain/" + name)) {
            if (stream == null) {
                throw new IOException("Missing shader " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static int compile(int type, String source) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IOException("Terrain shader compile failed: " + log);
        }
        return shader;
    }
}
