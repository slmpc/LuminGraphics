package com.github.slmpc.lumingraphics.render.immediate;

import java.nio.ByteBuffer;
import java.util.List;

public record VertexBatch(ByteBuffer bytes, int vertexCount, int stride) {
    public VertexBatch {
        if (bytes == null || vertexCount <= 0 || stride <= 0 || bytes.remaining() != vertexCount * stride) {
            throw new IllegalArgumentException("vertex batch byte count does not match its layout");
        }
        bytes = bytes.asReadOnlyBuffer();
    }

    public static VertexBatch combine(List<VertexBatch> batches) {
        if (batches == null || batches.isEmpty()) throw new IllegalArgumentException("vertex batches are empty");
        int stride = batches.get(0).stride;
        int vertices = 0;
        int bytes = 0;
        for (VertexBatch batch : batches) {
            if (batch.stride != stride) throw new IllegalArgumentException("vertex batch strides differ");
            vertices = Math.addExact(vertices, batch.vertexCount);
            bytes = Math.addExact(bytes, batch.bytes.remaining());
        }
        ByteBuffer combined = ByteBuffer.allocateDirect(bytes);
        for (VertexBatch batch : batches) combined.put(batch.bytes.slice());
        combined.flip();
        return new VertexBatch(combined, vertices, stride);
    }
}
