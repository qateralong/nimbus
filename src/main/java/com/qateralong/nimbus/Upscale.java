package com.qateralong.nimbus;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

// Upscale and contrast-adaptive sharpening in one full-screen pass straight from the low-resolution world target.
public final class Upscale {
    private static final String VERTEX = """
            #version 150
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                uv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D Source;
            uniform vec2 Texel;
            uniform float Sharpness;
            in vec2 uv;
            out vec4 fragColor;
            vec3 tap(float x, float y) {
                return texture(Source, uv + vec2(x, y) * Texel).rgb;
            }
            void main() {
                vec3 b = tap(0.0, -1.0);
                vec3 d = tap(-1.0, 0.0);
                vec3 e = tap(0.0, 0.0);
                vec3 f = tap(1.0, 0.0);
                vec3 h = tap(0.0, 1.0);
                vec3 mn = min(min(min(d, e), min(f, b)), h);
                vec3 mx = max(max(max(d, e), max(f, b)), h);
                vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(1e-5)), 0.0, 1.0));
                vec3 w = amp * (-1.0 / mix(8.0, 5.0, clamp(Sharpness, 0.0, 1.0)));
                fragColor = vec4(clamp(((b + d + f + h) * w + e) / (1.0 + 4.0 * w), 0.0, 1.0), 1.0);
            }
            """;
    private static int program;
    private static int vao;
    private static int texel;
    private static int sharpness;
    private static boolean failed;

    private Upscale() {
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Upscale shader compile failed: " + log);
        }
        return shader;
    }

    private static boolean ready() {
        if (program != 0 || failed) {
            return program != 0;
        }
        try {
            int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            int linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, fragment);
            GL20.glLinkProgram(linked);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0) {
                Nimbus.LOGGER.warn("Nimbus upscale program link failed: {}", GL20.glGetProgramInfoLog(linked));
                GL20.glDeleteProgram(linked);
                failed = true;
                return false;
            }
            texel = GL20.glGetUniformLocation(linked, "Texel");
            sharpness = GL20.glGetUniformLocation(linked, "Sharpness");
            GlStateManager._glUseProgram(linked);
            GL20.glUniform1i(GL20.glGetUniformLocation(linked, "Source"), 0);
            GlStateManager._glUseProgram(0);
            ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
            ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
            vao = GL30.glGenVertexArrays();
            program = linked;
            return true;
        } catch (Throwable t) {
            Nimbus.LOGGER.warn("Nimbus upscale pass unavailable", t);
            failed = true;
            return false;
        }
    }

    // false when the pass is unavailable and the caller has to blit
    public static boolean draw(RenderTarget source, RenderTarget main, float strength, boolean nearest) {
        if (!ready()) {
            return false;
        }
        main.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        source.setFilterMode(nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(source.getColorTextureId());
        GlStateManager._glUseProgram(program);
        GL20.glUniform2f(texel, 1.0F / source.width, 1.0F / source.height);
        GL20.glUniform1f(sharpness, strength);
        GlStateManager._glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate();
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        RenderSystem.enableDepthTest();
        return true;
    }
}
