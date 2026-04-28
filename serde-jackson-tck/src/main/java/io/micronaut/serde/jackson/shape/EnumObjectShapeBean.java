/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.jackson.shape;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public class EnumObjectShapeBean {
    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    private Status value;

    public EnumObjectShapeBean() {
    }

    public EnumObjectShapeBean(Status value) {
        this.value = value;
    }

    public Status getValue() {
        return value;
    }

    public void setValue(Status value) {
        this.value = value;
    }

    @Serdeable
    public enum Status {
        ALPHA(1, "Alpha"),
        BETA(2, "Beta");

        private final int code;
        private final String label;

        Status(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Status fromJson(Map<String, Object> value) {
            Object rawCode = value.get("code");
            int code = rawCode instanceof Number number
                ? number.intValue()
                : Integer.parseInt(rawCode.toString());
            for (Status status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status code: " + code);
        }
    }
}
