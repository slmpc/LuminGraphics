package com.github.slmpc.lumingraphics.render.immediate;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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

    List<VertexBatch> splitTriangleList(int maxBytes) {
        if (maxBytes <= 0 || vertexCount % 3 != 0) {
            throw new IllegalArgumentException("triangle-list batch cannot be split");
        }
        int verticesPerChunk = maxBytes / stride / 3 * 3;
        if (verticesPerChunk == 0) {
            throw new IllegalArgumentException("ring slot cannot fit one triangle");
        }
        if (vertexCount <= verticesPerChunk) return List.of(this);
        List<VertexBatch> chunks = new ArrayList<>((vertexCount + verticesPerChunk - 1) / verticesPerChunk);
        for (int first = 0; first < vertexCount; first += verticesPerChunk) {
            int vertices = Math.min(verticesPerChunk, vertexCount - first);
            ByteBuffer chunk = bytes.slice();
            chunk.position(Math.multiplyExact(first, stride));
            chunk.limit(Math.addExact(chunk.position(), Math.multiplyExact(vertices, stride)));
            chunks.add(new VertexBatch(chunk.slice(), vertices, stride));
        }
        return List.copyOf(chunks);
    }
}
