/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.npm;

import io.reactivex.Flowable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonPatchBuilder;

/**
 * The meta.json file.
 *
 * @since 0.1
 */
final class Meta {

    /**
     * The meta.json file.
     */
    private final JsonObject json;

    /**
     * The path prefix. Use default if not specified.
     */
    private final Optional<String> pathpref;

    /**
     * Ctor.
     *
     * @param json The meta.json file location on disk.
     * @param pathpref The path prefix. Use default if not specified.
     */
    Meta(final JsonObject json, final Optional<String> pathpref) {
        this.json = json;
        this.pathpref = pathpref;
    }

    /**
     * Update the meta.json file by processing newly
     * uploaded {@code npm publish} generated json.
     *
     * @param uploaded The json
     * @return Completion or error signal.
     */
    public Meta updatedMeta(final JsonObject uploaded) {
        final JsonObject versions = uploaded.getJsonObject("versions");
        final Set<String> keys = versions.keySet();
        final JsonPatchBuilder patch = Json.createPatchBuilder();
        patch.add("/dist-tags", uploaded.getJsonObject("dist-tags"));
        for (final String key : keys) {
            final JsonObject version = versions.getJsonObject(key);
            patch.add(
                String.format("/versions/%s", key),
                version
            );
            this.pathpref.ifPresent(
                prefix -> patch.add(
                    String.format("/versions/%s/dist/tarball", key),
                    String.format(
                        "%s%s",
                        prefix,
                        Meta.nonRelativePart(version.getJsonObject("dist").getString("tarball"))
                    )
                )
            );
        }
        return new Meta(
            patch
                .build()
                .apply(this.json),
            this.pathpref
        );
    }

    /**
     * Obtain a byte flow.
     * @return The flow of bytes.
     */
    public Flowable<ByteBuffer> byteFlow() {
        return Flowable.fromArray(
            ByteBuffer.wrap(
                this.json.toString().getBytes(StandardCharsets.UTF_8)
            )
        );
    }

    /**
     * Extract non relative path.
     * @param tarball The tarball filed.
     * @return Non a relative path.
     */
    private static String nonRelativePart(final String tarball) {
        final Pattern pattern = Pattern.compile("(@[\\w-_]+/[\\w_-]+/-/@[\\w-_]+/[\\w.-]+)");
        final Matcher matcher = pattern.matcher(tarball);
        matcher.find();
        return matcher.group(1);
    }
}
