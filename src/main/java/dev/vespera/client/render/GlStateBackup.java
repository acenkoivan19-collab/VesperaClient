package dev.vespera.client.render;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Снимок всего GL-стейта, который трогает NanoVG GL3 бэкенд.
 * Minecraft (GlStateManager) кэширует стейт на своей стороне — если вернуть
 * его неточно, в следующем кадре поедут блендинг, текстуры или трафарет.
 * Экземпляр переиспользуется — ноль аллокаций за кадр.
 */
public final class GlStateBackup {

    private int program, vao, arrayBuffer, uniformBuffer;
    private int activeTexture, texture0, sampler0;
    private int drawFbo, readFbo;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];

    private boolean blend, depthTest, cullFace, stencilTest, scissorTest;
    private int blendSrcRgb, blendDstRgb, blendSrcA, blendDstA, blendEqRgb, blendEqA;
    private boolean depthMask;
    private final boolean[] colorMask = new boolean[4];
    private int cullMode, frontFace;

    // Трафарет: NanoVG использует раздельные front/back операции.
    private int fFunc, fRef, fValMask, fFail, fZFail, fZPass, fWriteMask;
    private int bFunc, bRef, bValMask, bFail, bZFail, bZPass, bWriteMask;

    private int unpackAlign, unpackRowLength, unpackSkipPixels, unpackSkipRows;

    public void capture() {
        program = glGetInteger(GL_CURRENT_PROGRAM);
        vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        uniformBuffer = glGetInteger(GL_UNIFORM_BUFFER_BINDING);

        activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        sampler0 = glGetInteger(GL_SAMPLER_BINDING);

        drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissorBox);

        blend = glIsEnabled(GL_BLEND);
        depthTest = glIsEnabled(GL_DEPTH_TEST);
        cullFace = glIsEnabled(GL_CULL_FACE);
        stencilTest = glIsEnabled(GL_STENCIL_TEST);
        scissorTest = glIsEnabled(GL_SCISSOR_TEST);

        blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcA = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstA = glGetInteger(GL_BLEND_DST_ALPHA);
        blendEqRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        blendEqA = glGetInteger(GL_BLEND_EQUATION_ALPHA);

        depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
        }
        cullMode = glGetInteger(GL_CULL_FACE_MODE);
        frontFace = glGetInteger(GL_FRONT_FACE);

        fFunc = glGetInteger(GL_STENCIL_FUNC);
        fRef = glGetInteger(GL_STENCIL_REF);
        fValMask = glGetInteger(GL_STENCIL_VALUE_MASK);
        fFail = glGetInteger(GL_STENCIL_FAIL);
        fZFail = glGetInteger(GL_STENCIL_PASS_DEPTH_FAIL);
        fZPass = glGetInteger(GL_STENCIL_PASS_DEPTH_PASS);
        fWriteMask = glGetInteger(GL_STENCIL_WRITEMASK);

        bFunc = glGetInteger(GL_STENCIL_BACK_FUNC);
        bRef = glGetInteger(GL_STENCIL_BACK_REF);
        bValMask = glGetInteger(GL_STENCIL_BACK_VALUE_MASK);
        bFail = glGetInteger(GL_STENCIL_BACK_FAIL);
        bZFail = glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_FAIL);
        bZPass = glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_PASS);
        bWriteMask = glGetInteger(GL_STENCIL_BACK_WRITEMASK);

        unpackAlign = glGetInteger(GL_UNPACK_ALIGNMENT);
        unpackRowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
    }

    public void restore() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);

        cap(GL_BLEND, blend);
        cap(GL_DEPTH_TEST, depthTest);
        cap(GL_CULL_FACE, cullFace);
        cap(GL_STENCIL_TEST, stencilTest);
        cap(GL_SCISSOR_TEST, scissorTest);

        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcA, blendDstA);
        glBlendEquationSeparate(blendEqRgb, blendEqA);
        glDepthMask(depthMask);
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        glCullFace(cullMode);
        glFrontFace(frontFace);

        glStencilFuncSeparate(GL_FRONT, fFunc, fRef, fValMask);
        glStencilOpSeparate(GL_FRONT, fFail, fZFail, fZPass);
        glStencilMaskSeparate(GL_FRONT, fWriteMask);
        glStencilFuncSeparate(GL_BACK, bFunc, bRef, bValMask);
        glStencilOpSeparate(GL_BACK, bFail, bZFail, bZPass);
        glStencilMaskSeparate(GL_BACK, bWriteMask);

        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlign);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture0);
        glBindSampler(0, sampler0);
        glActiveTexture(activeTexture);

        // Индексные UBO-слоты Minecraft перепривязывает перед каждым draw,
        // поэтому достаточно вернуть generic-привязку.
        glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glUseProgram(program);
    }

    private static void cap(int cap, boolean on) {
        if (on) glEnable(cap); else glDisable(cap);
    }
}
